package io.github.takgeun.iyum.community.report.infrastructure;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.report.domain.ContentReport;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true"
})
@Testcontainers
@Transactional
class ContentReportHandlingRepositoryTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-11-12T10:00:00Z");

    private static final Instant HANDLED_AT =
            Instant.parse("2026-11-13T01:00:00Z");

    private static final String TITLE =
            "접수 당시 제목";

    private static final String BODY =
            "  접수 당시 본문\n\n두  번째 문장\t ";

    private static final String UPDATE_HANDLING_SQL = """
            UPDATE content_reports
               SET status = ?,
                   handled_by_member_id = ?,
                   handled_at = ?,
                   handling_reason = ?
             WHERE id = ?
            """;

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                postgres::getJdbcUrl
        );
        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );
        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );
    }

    @Autowired
    ContentReportRepository reportRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean(name = "auditingDateTimeProvider")
    DateTimeProvider dateTimeProvider;

    private Long reporterId;
    private Long handlerId;
    private Long postId;
    private Long commentId;

    @BeforeEach
    void setUp() {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(CREATED_AT));

        Member reporter = activeMember(
                "handling-reporter@example.com",
                "신고회원"
        );
        Member author = activeMember(
                "handling-author@example.com",
                "작성회원"
        );
        Member handler = activeMember(
                "handling-admin@example.com",
                "처리관리자"
        );

        ReflectionTestUtils.setField(
                handler,
                "role",
                MemberRole.ADMIN
        );

        entityManager.persist(reporter);
        entityManager.persist(author);
        entityManager.persist(handler);
        entityManager.flush();

        reporterId = reporter.getId();
        handlerId = handler.getId();

        Post post = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                TITLE,
                BODY
        );

        entityManager.persist(post);
        entityManager.flush();

        Comment comment = Comment.create(
                post,
                author,
                BODY
        );

        entityManager.persist(comment);
        entityManager.flush();

        postId = post.getId();
        commentId = comment.getId();

        entityManager.clear();
    }

    @ParameterizedTest
    @MethodSource("targetsAndStatuses")
    void 신고_상태와_처리_정보를_저장하고_다시_조회한다(
            boolean comment,
            ContentReportStatus status
    ) {
        ContentReport report = reportRepository.saveAndFlush(
                newReport(comment)
        );

        switch (status) {
            case RECEIVED -> {
            }
            case RESOLVED -> report.resolve(
                    handlerId,
                    "  검토 완료\n필요한 대응을 마쳤습니다.  ",
                    HANDLED_AT
            );
            case DISMISSED -> report.dismiss(
                    handlerId,
                    "  검토 완료\n필요한 대응을 마쳤습니다.  ",
                    HANDLED_AT
            );
        }

        reportRepository.flush();

        Long reportId = report.getId();
        entityManager.clear();

        ContentReport found = reportRepository.findById(reportId)
                .orElseThrow();

        assertThat(found.getStatus()).isEqualTo(status);

        if (status == ContentReportStatus.RECEIVED) {
            assertThat(found.getHandledByMemberId()).isNull();
            assertThat(found.getHandledAt()).isNull();
            assertThat(found.getHandlingReason()).isNull();
        } else {
            assertThat(found.getHandledByMemberId())
                    .isEqualTo(handlerId);
            assertThat(found.getHandledAt())
                    .isEqualTo(HANDLED_AT);
            assertThat(found.getHandlingReason())
                    .isEqualTo("검토 완료\n필요한 대응을 마쳤습니다.");
        }

        assertThat(found.getReporterMemberId())
                .isEqualTo(reporterId);
        assertThat(found.getReasonType())
                .isEqualTo(ContentReportReasonType.OTHER);
        assertThat(found.getReasonDetail())
                .isEqualTo("신고자의 접수 사유");
        assertThat(found.getCreatedAt())
                .isEqualTo(CREATED_AT);
        assertThat(found.getContentSnapshot())
                .isEqualTo(BODY);

        if (comment) {
            assertThat(found.getPostId()).isNull();
            assertThat(found.getCommentId()).isEqualTo(commentId);
            assertThat(found.getTitleSnapshot()).isNull();
        } else {
            assertThat(found.getPostId()).isEqualTo(postId);
            assertThat(found.getCommentId()).isNull();
            assertThat(found.getTitleSnapshot()).isEqualTo(TITLE);
        }
    }

    @ParameterizedTest
    @MethodSource("terminalStatusesAndMissingFields")
    void 종결_상태에서_처리_정보가_빠지면_DB가_거부한다(
            ContentReportStatus status,
            String missingField
    ) {
        Long reportId = saveReceived();

        Long actor = handlerId;
        Timestamp time = Timestamp.from(HANDLED_AT);
        String reason = "검토 완료";

        switch (missingField) {
            case "HANDLER" -> actor = null;
            case "TIME" -> time = null;
            case "REASON" -> reason = null;
            case "ALL" -> {
                actor = null;
                time = null;
                reason = null;
            }
            default -> throw new IllegalArgumentException(missingField);
        }

        Object[] parameters = {
                status.name(),
                actor,
                time,
                reason,
                reportId
        };

        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        UPDATE_HANDLING_SQL,
                        parameters
                )
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining(
                        "ck_content_reports_handling_state"
                );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "HANDLER", "TIME", "REASON", "ALL"
    })
    void 접수_상태에_처리_정보가_있으면_DB가_거부한다(
            String providedField
    ) {
        Long reportId = saveReceived();

        Long actor = null;
        Timestamp time = null;
        String reason = null;

        switch (providedField) {
            case "HANDLER" -> actor = handlerId;
            case "TIME" -> time = Timestamp.from(HANDLED_AT);
            case "REASON" -> reason = "검토 완료";
            case "ALL" -> {
                actor = handlerId;
                time = Timestamp.from(HANDLED_AT);
                reason = "검토 완료";
            }
            default -> throw new IllegalArgumentException(providedField);
        }

        Object[] parameters = {
                "RECEIVED",
                actor,
                time,
                reason,
                reportId
        };

        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        UPDATE_HANDLING_SQL,
                        parameters
                )
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining(
                        "ck_content_reports_handling_state"
                );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "\t\r\n",
            "\u00A0\u2003\u3000",
            "\u0085\u2028\u2029"
    })
    void 공백뿐인_처리_사유는_DB가_거부한다(String reason) {
        Long reportId = saveReceived();

        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        UPDATE_HANDLING_SQL,
                        "RESOLVED",
                        handlerId,
                        Timestamp.from(HANDLED_AT),
                        reason,
                        reportId
                )
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining(
                        "ck_content_reports_handling_reason"
                );
    }

    @ParameterizedTest
    @ValueSource(strings = {"가", "😀"})
    void 처리_사유_501_코드_포인트는_DB가_거부한다(
            String character
    ) {
        Long reportId = saveReceived();

        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        UPDATE_HANDLING_SQL,
                        "RESOLVED",
                        handlerId,
                        Timestamp.from(HANDLED_AT),
                        character.repeat(501),
                        reportId
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"가", "😀"})
    void 처리_사유_500_코드_포인트를_저장할_수_있다(
            String character
    ) {
        ContentReport report = reportRepository.saveAndFlush(
                newReport(false)
        );

        String reason = character.repeat(500);

        report.resolve(
                handlerId,
                reason,
                HANDLED_AT
        );

        reportRepository.flush();

        Long reportId = report.getId();
        entityManager.clear();

        ContentReport found = reportRepository.findById(reportId)
                .orElseThrow();

        assertThat(found.getHandlingReason()).isEqualTo(reason);
    }

    @Test
    void 존재하지_않는_처리_관리자는_DB가_거부한다() {
        Long reportId = saveReceived();

        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        UPDATE_HANDLING_SQL,
                        "RESOLVED",
                        Long.MAX_VALUE,
                        Timestamp.from(HANDLED_AT),
                        "검토 완료",
                        reportId
                )
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining(
                        "fk_content_reports_handler"
                );
    }

    @Test
    void 처리_기록이_참조하는_관리자의_물리_삭제를_거부한다() {
        ContentReport report = reportRepository.saveAndFlush(
                newReport(false)
        );

        report.resolve(
                handlerId,
                "검토 완료",
                HANDLED_AT
        );

        reportRepository.flush();
        entityManager.clear();

        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "DELETE FROM members WHERE id = ?",
                        handlerId
                )
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining(
                        "fk_content_reports_handler"
                );
    }

    static Stream<Arguments> targetsAndStatuses() {
        return Stream.of(false, true)
                .flatMap(comment ->
                        Arrays.stream(ContentReportStatus.values())
                                .map(status ->
                                        Arguments.of(comment, status)
                                )
                );
    }

    static Stream<Arguments> terminalStatusesAndMissingFields() {
        return Stream.of(
                        ContentReportStatus.RESOLVED,
                        ContentReportStatus.DISMISSED
                )
                .flatMap(status ->
                        Stream.of("HANDLER", "TIME", "REASON", "ALL")
                                .map(field -> Arguments.of(status, field))
                );
    }

    private Long saveReceived() {
        ContentReport report = reportRepository.saveAndFlush(
                newReport(false)
        );

        Long reportId = report.getId();
        entityManager.clear();

        return reportId;
    }

    private ContentReport newReport(boolean comment) {
        if (comment) {
            return ContentReport.forComment(
                    reporterId,
                    commentId,
                    ContentReportReasonType.OTHER,
                    "신고자의 접수 사유",
                    BODY,
                    CREATED_AT
            );
        }

        return ContentReport.forPost(
                reporterId,
                postId,
                ContentReportReasonType.OTHER,
                "신고자의 접수 사유",
                TITLE,
                BODY,
                CREATED_AT
        );
    }

    private Member activeMember(String email, String nickname) {
        Member member = Member.createPending(
                email,
                "test-only-password-hash",
                nickname
        );

        member.activeAfterEmailVerification();

        return member;
    }
}