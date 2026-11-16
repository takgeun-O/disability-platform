package io.github.takgeun.iyum.community.report.infrastructure;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ContentReportHandlingOutcomeMigrationTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-11-12T10:00:00Z");

    private static final Instant HANDLED_AT =
            Instant.parse("2026-11-13T01:00:00Z");

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @Test
    void V17_기존_신고를_보존하면서_V18_처리_결과를_추가한다()
            throws Exception {

        String schema = "report_outcome_"
                + UUID.randomUUID()
                .toString()
                .replace("-", "");

        // 1. V17까지만 적용한다.
        Flyway version17 = flyway(schema, "17");
        version17.migrate();

        assertThat(
                version17.info()
                        .current()
                        .getVersion()
                        .getVersion()
        ).isEqualTo("17");

        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        )) {
            connection.setSchema(schema);

            // JdbcTemplate이 작업 후 테스트용 연결을 닫지 않도록 한다.
            // 실제 연결은 try-with-resources에서 닫는다.
            JdbcTemplate jdbc = new JdbcTemplate(
                    new SingleConnectionDataSource(
                            connection,
                            true
                    )
            );

            // 2. V17 구조로 기존 데이터를 준비한다.
            seedMembers(jdbc);
            seedContentsAndReports(jdbc);

            List<Map<String, Object>> reportsBefore =
                    readTable(jdbc, "content_reports");

            List<Map<String, Object>> postsBefore =
                    readTable(jdbc, "community_posts");

            List<Map<String, Object>> commentsBefore =
                    readTable(jdbc, "community_comments");

            List<Map<String, Object>> logsBefore =
                    readTable(jdbc, "admin_content_moderation_logs");

            assertThat(reportsBefore).hasSize(6);
            assertThat(logsBefore).isEmpty();

            assertThat(reportsBefore).allSatisfy(row ->
                    assertThat(row)
                            .doesNotContainKey("handling_outcome")
            );

            // 3. 같은 스키마에 V18을 적용한다.
            Flyway version18 = flyway(schema, "18");
            version18.migrate();

            assertThat(
                    version18.info()
                            .current()
                            .getVersion()
                            .getVersion()
            ).isEqualTo("18");

            // 4. 기존 모든 컬럼 값이 보존됐는지 확인한다.
            List<Map<String, Object>> reportsAfter =
                    readTable(jdbc, "content_reports");

            assertThat(reportsAfter).hasSize(6);

            List<Map<String, Object>> originalColumnsAfter =
                    reportsAfter.stream()
                            .map(row -> {
                                Map<String, Object> original =
                                        new LinkedHashMap<>(row);

                                original.remove("handling_outcome");
                                return original;
                            })
                            .toList();

            assertThat(originalColumnsAfter)
                    .containsExactlyElementsOf(reportsBefore);

            // 5. 접수 기록은 null,
            // 기존 종결 기록은 LEGACY_UNKNOWN이어야 한다.
            assertThat(reportsAfter).allSatisfy(row -> {
                assertThat(row)
                        .containsKey("handling_outcome");

                if ("RECEIVED".equals(row.get("status"))) {
                    assertThat(row.get("handling_outcome"))
                            .isNull();
                } else {
                    assertThat(row.get("handling_outcome"))
                            .isEqualTo("LEGACY_UNKNOWN");
                }
            });

            // 6. 마이그레이션이 콘텐츠나 운영 기록을
            // 변경하거나 생성하지 않았는지 확인한다.
            assertThat(readTable(jdbc, "community_posts"))
                    .containsExactlyElementsOf(postsBefore);

            assertThat(readTable(jdbc, "community_comments"))
                    .containsExactlyElementsOf(commentsBefore);

            assertThat(
                    readTable(jdbc, "admin_content_moderation_logs")
            ).containsExactlyElementsOf(logsBefore);

            // 7. 다시 migrate해도 V18이 재실행되지 않는다.
            var repeated = version18.migrate();

            assertThat(repeated.migrationsExecuted).isZero();

            assertThat(readTable(jdbc, "content_reports"))
                    .containsExactlyElementsOf(reportsAfter);
        }
    }

    private static Flyway flyway(
            String schema,
            String targetVersion
    ) {
        return Flyway.configure()
                .dataSource(
                        postgres.getJdbcUrl(),
                        postgres.getUsername(),
                        postgres.getPassword()
                )
                .locations("classpath:db/migration")
                .schemas(schema)
                .defaultSchema(schema)
                .target(
                        MigrationVersion.fromVersion(targetVersion)
                )
                .load();
    }

    private static void seedMembers(JdbcTemplate jdbc) {
        insertMember(
                jdbc,
                1L,
                "migration-author@example.com",
                "이행작성자",
                "USER"
        );

        insertMember(
                jdbc,
                2L,
                "migration-reporter@example.com",
                "이행신고자",
                "USER"
        );

        insertMember(
                jdbc,
                3L,
                "migration-admin@example.com",
                "이행관리자",
                "ADMIN"
        );
    }

    private static void insertMember(
            JdbcTemplate jdbc,
            long id,
            String email,
            String nickname,
            String role
    ) {
        jdbc.update(
                """
                INSERT INTO members (
                    id,
                    email,
                    password_hash,
                    nickname,
                    status,
                    role,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                email,
                "test-only-password-hash",
                nickname,
                "ACTIVE",
                role,
                Timestamp.from(CREATED_AT),
                Timestamp.from(CREATED_AT)
        );
    }

    private static void seedContentsAndReports(
            JdbcTemplate jdbc
    ) {
        List<String> statuses = List.of(
                "RECEIVED",
                "RESOLVED",
                "DISMISSED"
        );

        for (int index = 0; index < statuses.size(); index++) {
            String status = statuses.get(index);

            long postId = 100L + index;
            long commentId = 200L + index;

            // 일부 콘텐츠는 이미 숨김 상태로 준비한다.
            // 현재 숨김 상태로 과거 신고 결과를 추정하면 안 된다.
            Timestamp postHiddenAt = index == 1
                    ? Timestamp.from(HANDLED_AT)
                    : null;

            Timestamp commentHiddenAt = index == 2
                    ? Timestamp.from(HANDLED_AT)
                    : null;

            jdbc.update(
                    """
                    INSERT INTO community_posts (
                        id,
                        author_id,
                        post_type,
                        topic,
                        title,
                        content,
                        created_at,
                        updated_at,
                        hidden_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    postId,
                    1L,
                    "GENERAL",
                    "DAILY",
                    "현재 게시글 제목 " + index,
                    "현재 게시글 본문 " + index,
                    Timestamp.from(CREATED_AT),
                    Timestamp.from(CREATED_AT),
                    postHiddenAt
            );

            jdbc.update(
                    """
                    INSERT INTO community_comments (
                        id,
                        post_id,
                        author_id,
                        content,
                        created_at,
                        updated_at,
                        hidden_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    commentId,
                    postId,
                    1L,
                    "현재 댓글 본문 " + index,
                    Timestamp.from(CREATED_AT),
                    Timestamp.from(CREATED_AT),
                    commentHiddenAt
            );

            insertReport(
                    jdbc,
                    300L + index * 2L,
                    postId,
                    null,
                    status,
                    "  신고 당시 제목 " + index + "  "
            );

            insertReport(
                    jdbc,
                    301L + index * 2L,
                    null,
                    commentId,
                    status,
                    null
            );
        }
    }

    private static void insertReport(
            JdbcTemplate jdbc,
            long reportId,
            Long postId,
            Long commentId,
            String status,
            String titleSnapshot
    ) {
        boolean received = "RECEIVED".equals(status);

        Long handlerId = received ? null : 3L;

        Timestamp handledAt = received
                ? null
                : Timestamp.from(HANDLED_AT);

        String handlingReason = received
                ? null
                : "기존 처리 사유\n줄바꿈과 이모지 😀 보존";

        // V17 구조이므로 handling_outcome은 넣지 않는다.
        jdbc.update(
                """
                INSERT INTO content_reports (
                    id,
                    reporter_member_id,
                    post_id,
                    comment_id,
                    reason_type,
                    reason_detail,
                    title_snapshot,
                    content_snapshot,
                    status,
                    created_at,
                    handled_by_member_id,
                    handled_at,
                    handling_reason
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                reportId,
                2L,
                postId,
                commentId,
                "OTHER",
                "기존 신고 상세 사유",
                titleSnapshot,
                "  신고 당시 본문\n\n공백과 이모지 😀 보존  ",
                status,
                Timestamp.from(CREATED_AT),
                handlerId,
                handledAt,
                handlingReason
        );
    }

    private static List<Map<String, Object>> readTable(
            JdbcTemplate jdbc,
            String table
    ) {
        // 테스트 내부에서 지정한 고정 테이블만 허용한다.
        String sql = switch (table) {
            case "content_reports" ->
                    "SELECT * FROM content_reports ORDER BY id";

            case "community_posts" ->
                    "SELECT * FROM community_posts ORDER BY id";

            case "community_comments" ->
                    "SELECT * FROM community_comments ORDER BY id";

            case "admin_content_moderation_logs" ->
                    """
                    SELECT *
                    FROM admin_content_moderation_logs
                    ORDER BY id
                    """;

            default -> throw new IllegalArgumentException(
                    "지원하지 않는 테스트 테이블: " + table
            );
        };

        return jdbc.queryForList(sql);
    }
}