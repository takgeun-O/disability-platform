package io.github.takgeun.iyum.admin.report.infrastructure;

import io.github.takgeun.iyum.admin.report.application.AdminReportReadQuery;
import io.github.takgeun.iyum.admin.report.application.AdminReportSort;
import io.github.takgeun.iyum.admin.report.application.AdminReportTargetType;
import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.report.domain.ContentReport;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import io.github.takgeun.iyum.member.domain.Member;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
class AdminReportReadRepositoryTest {

    private static final Instant NOW =
            Instant.parse("2026-11-12T03:00:00Z");

    private static final LocalDate SEARCH_DATE =
            LocalDate.of(2026, 11, 12);

    private static final String TITLE = "신고 당시 제목";
    private static final String POST_CONTENT = "신고 당시 게시글 본문";
    private static final String COMMENT_CONTENT = "신고 당시 댓글 본문";

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    AdminReportReadRepository repository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean(name = "auditingDateTimeProvider")
    DateTimeProvider dateTimeProvider;

    private Long authorId;
    private Long reporterId;
    private Long handlerId;

    @BeforeEach
    void setUp() {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(NOW));

        Member author = activeMember(
                "report-read-author@example.com",
                "신고조회작성자"
        );
        Member reporter = activeMember(
                "report-read-reporter@example.com",
                "신고조회신고자"
        );

        entityManager.persist(author);
        entityManager.persist(reporter);
        entityManager.flush();

        authorId = author.getId();
        reporterId = reporter.getId();

        Member handler = activeMember(
                "report-handler@example.com",
                "신고처리관리자"
        );

        // 테스트 데이터에서만 관리자 역할을 설정한다.
        org.springframework.test.util.ReflectionTestUtils.setField(
                handler,
                "role",
                io.github.takgeun.iyum.member.domain.MemberRole.ADMIN
        );

        entityManager.persist(handler);
        entityManager.flush();

        handlerId = handler.getId();
    }

    @ParameterizedTest
    @EnumSource(AdminReportTargetType.class)
    void 대상_유형을_목록과_전체_개수에_적용한다(
            AdminReportTargetType targetType
    ) {
        Long postReport = saveReport(
                true,
                ContentReportStatus.RECEIVED,
                ContentReportReasonType.SPAM_ADVERTISING,
                NOW
        );
        Long commentReport = saveReport(
                false,
                ContentReportStatus.RECEIVED,
                ContentReportReasonType.SPAM_ADVERTISING,
                NOW
        );

        Page<ContentReport> result = repository.findReports(
                new AdminReportReadQuery(
                        targetType,
                        null,
                        null,
                        null,
                        null,
                        0,
                        20,
                        AdminReportSort.LATEST
                )
        );

        List<Long> expected = switch (targetType) {
            case ALL -> List.of(commentReport, postReport);
            case POST -> List.of(postReport);
            case COMMENT -> List.of(commentReport);
        };

        assertReports(result, expected, expected.size());
    }

    @ParameterizedTest
    @EnumSource(ContentReportStatus.class)
    void 신고_상태로_조회한다(ContentReportStatus status) {
        Long expectedId = null;

        for (ContentReportStatus candidate : ContentReportStatus.values()) {
            Long id = saveReport(
                    true,
                    candidate,
                    ContentReportReasonType.SPAM_ADVERTISING,
                    NOW
            );

            if (candidate == status) {
                expectedId = id;
            }
        }

        Page<ContentReport> result = repository.findReports(
                new AdminReportReadQuery(
                        null,
                        status,
                        null,
                        null,
                        null,
                        0,
                        20,
                        null
                )
        );

        assertReports(result, List.of(expectedId), 1);
    }

    @ParameterizedTest
    @EnumSource(ContentReportReasonType.class)
    void 신고_사유_유형으로_조회한다(
            ContentReportReasonType reasonType
    ) {
        Long expectedId = null;

        for (ContentReportReasonType candidate
                : ContentReportReasonType.values()) {
            Long id = saveReport(
                    true,
                    ContentReportStatus.RECEIVED,
                    candidate,
                    NOW
            );

            if (candidate == reasonType) {
                expectedId = id;
            }
        }

        Page<ContentReport> result = repository.findReports(
                new AdminReportReadQuery(
                        null,
                        null,
                        reasonType,
                        null,
                        null,
                        0,
                        20,
                        null
                )
        );

        assertReports(result, List.of(expectedId), 1);
    }

    @Test
    void 선택_조건을_생략하면_모든_상태와_사유를_조회한다() {
        Long received = saveReport(
                true,
                ContentReportStatus.RECEIVED,
                ContentReportReasonType.SPAM_ADVERTISING,
                NOW
        );
        Long resolved = saveReport(
                false,
                ContentReportStatus.RESOLVED,
                ContentReportReasonType.ABUSE_HATE,
                NOW
        );
        Long dismissed = saveReport(
                true,
                ContentReportStatus.DISMISSED,
                ContentReportReasonType.OTHER,
                NOW
        );

        Page<ContentReport> result =
                readPage(0, 20, AdminReportSort.LATEST);

        assertReports(
                result,
                List.of(dismissed, resolved, received),
                3
        );
    }

    @ParameterizedTest
    @EnumSource(AdminReportSort.class)
    void 접수시각을_우선하고_같은_시각이면_ID로_정렬한다(
            AdminReportSort sort
    ) {
        Long newerFirst = saveReceived(true, NOW.plusSeconds(60));
        Long newerSecond = saveReceived(false, NOW.plusSeconds(60));

        // 가장 큰 ID여도 접수 시각이 오래되면 시간 순서를 따른다.
        Long older = saveReceived(true, NOW);

        List<Long> expected = switch (sort) {
            case LATEST -> List.of(newerSecond, newerFirst, older);
            case OLDEST -> List.of(older, newerFirst, newerSecond);
        };

        Page<ContentReport> result = readPage(0, 20, sort);

        assertReports(result, expected, 3);

        Sort.Direction direction = sort == AdminReportSort.LATEST
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        assertThat(result.getSort())
                .isEqualTo(Sort.by(direction, "createdAt", "id"));
    }

    @ParameterizedTest
    @EnumSource(AdminReportSort.class)
    void 같은_접수시각의_신고도_페이지_사이에_중복과_누락이_없다(
            AdminReportSort sort
    ) {
        Long first = saveReceived(true, NOW);
        Long second = saveReceived(false, NOW);
        Long third = saveReceived(true, NOW);

        List<Long> expected = switch (sort) {
            case LATEST -> List.of(third, second, first);
            case OLDEST -> List.of(first, second, third);
        };

        Page<ContentReport> firstPage = readPage(0, 2, sort);
        Page<ContentReport> secondPage = readPage(1, 2, sort);

        assertReports(firstPage, expected.subList(0, 2), 3);
        assertReports(secondPage, expected.subList(2, 3), 3);

        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(secondPage.getTotalPages()).isEqualTo(2);

        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.hasPrevious()).isFalse();

        assertThat(secondPage.hasNext()).isFalse();
        assertThat(secondPage.hasPrevious()).isTrue();
    }

    @Test
    void 한국_날짜의_시작은_포함하고_다음날_시작은_제외한다() {
        Instant start = Instant.parse("2026-11-11T15:00:00Z");
        Instant end = Instant.parse("2026-11-12T15:00:00Z");

        // PostgreSQL의 마이크로초 정밀도에 맞춰 경계를 준비한다.
        saveReceived(true, start.minusNanos(1_000));
        Long atStart = saveReceived(true, start);
        Long beforeEnd = saveReceived(false, end.minusNanos(1_000));
        saveReceived(false, end);

        Page<ContentReport> result = repository.findReports(
                new AdminReportReadQuery(
                        null,
                        null,
                        null,
                        SEARCH_DATE,
                        SEARCH_DATE,
                        0,
                        20,
                        null
                )
        );

        assertReports(result, List.of(beforeEnd, atStart), 2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 시작일과_종료일을_각각_단독으로_사용한다(
            boolean startOnly
    ) {
        Long previous = saveReceived(
                true,
                Instant.parse("2026-11-11T14:59:59Z")
        );
        Long inside = saveReceived(true, NOW);
        Long next = saveReceived(
                false,
                Instant.parse("2026-11-12T15:00:00Z")
        );

        Page<ContentReport> result = repository.findReports(
                new AdminReportReadQuery(
                        null,
                        null,
                        null,
                        startOnly ? SEARCH_DATE : null,
                        startOnly ? null : SEARCH_DATE,
                        0,
                        20,
                        null
                )
        );

        List<Long> expected = startOnly
                ? List.of(next, inside)
                : List.of(inside, previous);

        assertReports(result, expected, 2);
    }

    @ParameterizedTest
    @EnumSource(AdminReportSort.class)
    void 모든_검색_조건을_AND로_적용한_후_페이지를_나눈다(
            AdminReportSort sort
    ) {
        Long first = saveReceived(true, NOW);
        Long second = saveReceived(true, NOW.plusSeconds(1));
        Long third = saveReceived(true, NOW.plusSeconds(2));

        // 대상 유형만 불일치.
        saveReceived(false, NOW);

        // 신고 상태만 불일치.
        saveReport(
                true,
                ContentReportStatus.RESOLVED,
                ContentReportReasonType.SPAM_ADVERTISING,
                NOW
        );

        // 신고 사유만 불일치.
        saveReport(
                true,
                ContentReportStatus.RECEIVED,
                ContentReportReasonType.ABUSE_HATE,
                NOW
        );

        // 시작일보다 이전.
        saveReceived(
                true,
                Instant.parse("2026-11-11T14:59:59Z")
        );

        // 종료일 다음 날 시작.
        saveReceived(
                true,
                Instant.parse("2026-11-12T15:00:00Z")
        );

        Page<ContentReport> firstPage =
                readCombinedPage(0, 2, sort);
        Page<ContentReport> secondPage =
                readCombinedPage(1, 2, sort);
        Page<ContentReport> outsidePage =
                readCombinedPage(2, 2, sort);

        List<Long> expected = switch (sort) {
            case LATEST -> List.of(third, second, first);
            case OLDEST -> List.of(first, second, third);
        };

        assertReports(firstPage, expected.subList(0, 2), 3);
        assertReports(secondPage, expected.subList(2, 3), 3);
        assertReports(outsidePage, List.of(), 3);

        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(secondPage.getTotalPages()).isEqualTo(2);
        assertThat(outsidePage.getTotalPages()).isEqualTo(2);
        assertThat(outsidePage.getNumber()).isEqualTo(2);
        assertThat(outsidePage.hasNext()).isFalse();
    }

    @Test
    void 일치하는_신고가_없으면_빈_목록과_전체_개수_0을_반환한다() {
        saveReceived(true, NOW);

        Page<ContentReport> result = repository.findReports(
                new AdminReportReadQuery(
                        AdminReportTargetType.COMMENT,
                        ContentReportStatus.RESOLVED,
                        ContentReportReasonType.OTHER,
                        null,
                        null,
                        0,
                        20,
                        null
                )
        );

        assertReports(result, List.of(), 0);
        assertThat(result.getTotalPages()).isZero();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.hasPrevious()).isFalse();
    }

    @Test
    void 허용_날짜_범위의_양끝으로_DB를_조회할_수_있다() {
        Long id = saveReceived(true, NOW);

        Page<ContentReport> result = repository.findReports(
                new AdminReportReadQuery(
                        null,
                        null,
                        null,
                        LocalDate.of(1, 1, 1),
                        LocalDate.of(9999, 12, 31),
                        0,
                        20,
                        null
                )
        );

        assertReports(result, List.of(id), 1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 신고_ID로_신고_정보와_스냅샷을_조회한다(
            boolean postTarget
    ) {
        Long id = saveReport(
                postTarget,
                ContentReportStatus.DISMISSED,
                ContentReportReasonType.OTHER,
                NOW
        );

        ContentReport found = repository.findById(id).orElseThrow();

        assertThat(found.getId()).isEqualTo(id);
        assertThat(found.getReporterMemberId()).isEqualTo(reporterId);
        assertThat(found.getStatus())
                .isEqualTo(ContentReportStatus.DISMISSED);
        assertThat(found.getReasonType())
                .isEqualTo(ContentReportReasonType.OTHER);
        assertThat(found.getReasonDetail()).isEqualTo("검토 요청");
        assertThat(found.getCreatedAt()).isEqualTo(NOW);

        if (postTarget) {
            assertThat(found.getPostId()).isNotNull();
            assertThat(found.getCommentId()).isNull();
            assertThat(found.getTitleSnapshot()).isEqualTo(TITLE);
            assertThat(found.getContentSnapshot())
                    .isEqualTo(POST_CONTENT);
        } else {
            assertThat(found.getPostId()).isNull();
            assertThat(found.getCommentId()).isNotNull();
            assertThat(found.getTitleSnapshot()).isNull();
            assertThat(found.getContentSnapshot())
                    .isEqualTo(COMMENT_CONTENT);
        }
    }

    @Test
    void 존재하지_않는_신고_ID는_빈_Optional을_반환한다() {
        saveReceived(true, NOW);

        assertThat(repository.findById(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void 원문이_숨김_삭제되어도_신고_목록과_단건_조회는_유지된다() {
        Long postReportId = saveReceived(true, NOW);
        Long commentReportId = saveReceived(false, NOW);

        ContentReport postReport =
                repository.findById(postReportId).orElseThrow();
        ContentReport commentReport =
                repository.findById(commentReportId).orElseThrow();

        Post post = entityManager.find(
                Post.class,
                postReport.getPostId()
        );
        Comment comment = entityManager.find(
                Comment.class,
                commentReport.getCommentId()
        );

        post.hide(NOW.plusSeconds(60));
        comment.hide(NOW.plusSeconds(60));
        post.delete(NOW.plusSeconds(120));
        comment.delete(NOW.plusSeconds(120));

        entityManager.flush();
        entityManager.clear();

        Page<ContentReport> result =
                readPage(0, 20, AdminReportSort.LATEST);

        assertReports(
                result,
                List.of(commentReportId, postReportId),
                2
        );

        ContentReport foundPost =
                repository.findById(postReportId).orElseThrow();
        ContentReport foundComment =
                repository.findById(commentReportId).orElseThrow();

        assertThat(foundPost.getTitleSnapshot()).isEqualTo(TITLE);
        assertThat(foundPost.getContentSnapshot())
                .isEqualTo(POST_CONTENT);
        assertThat(foundComment.getContentSnapshot())
                .isEqualTo(COMMENT_CONTENT);

        assertThat(foundPost.getStatus())
                .isEqualTo(ContentReportStatus.RECEIVED);
        assertThat(foundComment.getStatus())
                .isEqualTo(ContentReportStatus.RECEIVED);
    }

    @Test
    void 같은_대상의_여러_신고를_합치지_않고_각각_조회한다() {
        Long firstReportId = saveReceived(true, NOW);

        ContentReport firstReport =
                repository.findById(firstReportId).orElseThrow();

        Member otherReporter = activeMember(
                "report-read-other@example.com",
                "다른신고자"
        );
        entityManager.persist(otherReporter);
        entityManager.flush();

        ContentReport secondReport = ContentReport.forPost(
                otherReporter.getId(),
                firstReport.getPostId(),
                ContentReportReasonType.ABUSE_HATE,
                null,
                TITLE,
                POST_CONTENT,
                NOW.plusSeconds(1)
        );

        entityManager.persist(secondReport);
        entityManager.flush();
        Long secondReportId = secondReport.getId();
        entityManager.clear();

        Page<ContentReport> result =
                readPage(0, 20, AdminReportSort.LATEST);

        assertReports(
                result,
                List.of(secondReportId, firstReportId),
                2
        );

        assertThat(result.getContent())
                .extracting(ContentReport::getPostId)
                .containsOnly(firstReport.getPostId());
    }

    private Page<ContentReport> readPage(
            int page,
            int size,
            AdminReportSort sort
    ) {
        return repository.findReports(
                new AdminReportReadQuery(
                        null,
                        null,
                        null,
                        null,
                        null,
                        page,
                        size,
                        sort
                )
        );
    }

    private Page<ContentReport> readCombinedPage(
            int page,
            int size,
            AdminReportSort sort
    ) {
        return repository.findReports(
                new AdminReportReadQuery(
                        AdminReportTargetType.POST,
                        ContentReportStatus.RECEIVED,
                        ContentReportReasonType.SPAM_ADVERTISING,
                        SEARCH_DATE,
                        SEARCH_DATE,
                        page,
                        size,
                        sort
                )
        );
    }

    private Long saveReceived(boolean postTarget, Instant createdAt) {
        return saveReport(
                postTarget,
                ContentReportStatus.RECEIVED,
                ContentReportReasonType.SPAM_ADVERTISING,
                createdAt
        );
    }

    private Long saveReport(
            boolean postTarget,
            ContentReportStatus status,
            ContentReportReasonType reasonType,
            Instant createdAt
    ) {
        Member author = entityManager.getReference(Member.class, authorId);

        // 매번 새 대상을 생성하여 동일 신고자의 중복 신고 제약을 피한다.
        Post post = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                TITLE,
                POST_CONTENT
        );
        entityManager.persist(post);
        entityManager.flush();

        String detail = reasonType == ContentReportReasonType.OTHER
                ? "검토 요청"
                : null;

        ContentReport report;

        if (postTarget) {
            report = ContentReport.forPost(
                    reporterId,
                    post.getId(),
                    reasonType,
                    detail,
                    TITLE,
                    POST_CONTENT,
                    createdAt
            );
        } else {
            Comment comment = Comment.create(
                    post,
                    author,
                    COMMENT_CONTENT
            );
            entityManager.persist(comment);
            entityManager.flush();

            report = ContentReport.forComment(
                    reporterId,
                    comment.getId(),
                    reasonType,
                    detail,
                    COMMENT_CONTENT,
                    createdAt
            );
        }

        entityManager.persist(report);
        entityManager.flush();

        switch (status) {
            case RECEIVED -> {
                // 접수 상태와 처리 정보 null을 유지한다.
            }
            case RESOLVED -> report.resolve(
                    handlerId,
                    "조회 테스트: 처리 완료",
                    createdAt.plusSeconds(60)
            );
            case DISMISSED -> report.dismiss(
                    handlerId,
                    "조회 테스트: 기각",
                    createdAt.plusSeconds(60)
            );
        }

        entityManager.persist(report);
        entityManager.flush();

        Long id = report.getId();

        entityManager.clear();

        return id;
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

    private void assertReports(
            Page<ContentReport> result,
            List<Long> expectedIds,
            long expectedTotal
    ) {
        assertThat(result.getContent())
                .extracting(ContentReport::getId)
                .containsExactlyElementsOf(expectedIds);

        assertThat(result.getTotalElements()).isEqualTo(expectedTotal);
    }
}