package io.github.takgeun.iyum.community.report.infrastructure;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.report.domain.ContentReport;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Testcontainers
@Transactional
class ContentReportRepositoryTest {

    private static final Instant NOW =
            Instant.parse("2026-11-12T10:00:00Z");

    private static final String ORIGINAL_TITLE =
            "접수 당시 게시글 제목";

    private static final String ORIGINAL_POST_CONTENT =
            " \n접수 당시 게시글 본문\n\n두  번째 문장\t ";

    private static final String ORIGINAL_COMMENT_CONTENT =
            " \n접수 당시 댓글 본문\n두  번째 문장\t ";

    private static final String INSERT_REPORT_SQL = """
            INSERT INTO content_reports (
                reporter_member_id,
                post_id,
                comment_id,
                reason_type,
                reason_detail,
                title_snapshot,
                content_snapshot,
                status,
                created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

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
    ContentReportRepository reportRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean(name = "auditingDateTimeProvider")
    DateTimeProvider dateTimeProvider;

    private Long reporterId;
    private Long otherReporterId;
    private Long authorId;
    private Long postId;
    private Long commentId;

    @BeforeEach
    void setUp() {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(NOW));

        Member reporter = activeMember(
                "reporter@example.com",
                "신고회원"
        );

        Member otherReporter = activeMember(
                "other-reporter@example.com",
                "다른신고회원"
        );

        Member author = activeMember(
                "content-author@example.com",
                "콘텐츠작성자"
        );

        memberRepository.saveAndFlush(reporter);
        memberRepository.saveAndFlush(otherReporter);
        memberRepository.saveAndFlush(author);

        reporterId = reporter.getId();
        otherReporterId = otherReporter.getId();
        authorId = author.getId();

        Post post = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                ORIGINAL_TITLE,
                ORIGINAL_POST_CONTENT
        );

        entityManager.persist(post);
        entityManager.flush();
        postId = post.getId();

        Comment comment = Comment.create(
                post,
                author,
                ORIGINAL_COMMENT_CONTENT
        );

        entityManager.persist(comment);
        entityManager.flush();
        commentId = comment.getId();

        entityManager.clear();
    }

    @Test
    void 게시글_신고를_저장하고_DB에서_다시_조회한다() {
        ContentReport saved = reportRepository.saveAndFlush(
                ContentReport.forPost(
                        reporterId,
                        postId,
                        ContentReportReasonType.SPAM_ADVERTISING,
                        "  광고가 반복되고 있습니다.  ",
                        ORIGINAL_TITLE,
                        ORIGINAL_POST_CONTENT,
                        NOW
                )
        );

        Long reportId = saved.getId();
        entityManager.clear();

        ContentReport found =
                reportRepository.findByReporterMemberIdAndPostId(
                        reporterId,
                        postId
                ).orElseThrow();

        assertThat(found.getId()).isEqualTo(reportId);
        assertThat(found.getReporterMemberId()).isEqualTo(reporterId);
        assertThat(found.getPostId()).isEqualTo(postId);
        assertThat(found.getCommentId()).isNull();

        assertThat(found.getReasonType())
                .isEqualTo(ContentReportReasonType.SPAM_ADVERTISING);
        assertThat(found.getReasonDetail())
                .isEqualTo("광고가 반복되고 있습니다.");

        assertThat(found.getTitleSnapshot()).isEqualTo(ORIGINAL_TITLE);
        assertThat(found.getContentSnapshot())
                .isEqualTo(ORIGINAL_POST_CONTENT);

        assertThat(found.getStatus())
                .isEqualTo(ContentReportStatus.RECEIVED);
        assertThat(found.getCreatedAt()).isEqualTo(NOW);

        // 같은 숫자의 댓글 ID가 있더라도 게시글 신고를 반환하면 안 된다.
        assertThat(
                reportRepository.findByReporterMemberIdAndCommentId(
                        reporterId,
                        postId
                )
        ).isEmpty();
    }

    @Test
    void 댓글_신고를_저장하고_DB에서_다시_조회한다() {
        ContentReport saved = reportRepository.saveAndFlush(
                ContentReport.forComment(
                        reporterId,
                        commentId,
                        ContentReportReasonType.ABUSE_HATE,
                        null,
                        ORIGINAL_COMMENT_CONTENT,
                        NOW
                )
        );

        Long reportId = saved.getId();
        entityManager.clear();

        ContentReport found =
                reportRepository.findByReporterMemberIdAndCommentId(
                        reporterId,
                        commentId
                ).orElseThrow();

        assertThat(found.getId()).isEqualTo(reportId);
        assertThat(found.getReporterMemberId()).isEqualTo(reporterId);
        assertThat(found.getPostId()).isNull();
        assertThat(found.getCommentId()).isEqualTo(commentId);

        assertThat(found.getReasonType())
                .isEqualTo(ContentReportReasonType.ABUSE_HATE);
        assertThat(found.getReasonDetail()).isNull();

        assertThat(found.getTitleSnapshot()).isNull();
        assertThat(found.getContentSnapshot())
                .isEqualTo(ORIGINAL_COMMENT_CONTENT);

        assertThat(found.getStatus())
                .isEqualTo(ContentReportStatus.RECEIVED);
        assertThat(found.getCreatedAt()).isEqualTo(NOW);

        assertThat(
                reportRepository.findByReporterMemberIdAndPostId(
                        reporterId,
                        commentId
                )
        ).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 같은_회원의_같은_대상_중복_신고는_DB에서_차단한다(
            boolean postTarget
    ) {
        reportRepository.saveAndFlush(
                newReport(postTarget, reporterId)
        );

        String constraint = postTarget
                ? "uq_content_reports_post_reporter"
                : "uq_content_reports_comment_reporter";

        assertThatThrownBy(() ->
                reportRepository.saveAndFlush(
                        newReport(postTarget, reporterId)
                )
        ).isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining(constraint);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 서로_다른_회원은_같은_대상을_신고할_수_있다(
            boolean postTarget
    ) {
        Long firstId = reportRepository.saveAndFlush(
                newReport(postTarget, reporterId)
        ).getId();

        Long secondId = reportRepository.saveAndFlush(
                newReport(postTarget, otherReporterId)
        ).getId();

        entityManager.clear();

        ContentReport first =
                reportRepository.findById(firstId).orElseThrow();

        ContentReport second =
                reportRepository.findById(secondId).orElseThrow();

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(first.getReporterMemberId()).isEqualTo(reporterId);
        assertThat(second.getReporterMemberId())
                .isEqualTo(otherReporterId);

        assertThat(first.getPostId()).isEqualTo(second.getPostId());
        assertThat(first.getCommentId()).isEqualTo(second.getCommentId());
    }

    @ParameterizedTest
    @EnumSource(
            value = ContentReportStatus.class,
            names = {"RESOLVED", "DISMISSED"}
    )
    void 종결된_신고도_동일_대상_재신고를_허용하지_않는다(
            ContentReportStatus status
    ) {
        Long reportId = reportRepository.saveAndFlush(
                newReport(true, reporterId)
        ).getId();

        // 관리자 처리 API 구현 전, DB 제약 검증을 위한 준비다.
        jdbcTemplate.update(
                "UPDATE content_reports SET status = ? WHERE id = ?",
                status.name(),
                reportId
        );

        entityManager.clear();

        assertThat(
                reportRepository.findById(reportId)
                        .orElseThrow()
                        .getStatus()
        ).isEqualTo(status);

        assertThatThrownBy(() ->
                reportRepository.saveAndFlush(
                        newReport(true, reporterId)
                )
        ).isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining(
                        "uq_content_reports_post_reporter"
                );
    }

    @Test
    void 게시글_ID와_댓글_ID가_같아도_별개_대상으로_신고한다() {
        // IDENTITY 시퀀스의 우연한 일치에 의존하지 않도록
        // 두 테이블에 동일한 ID를 명시적으로 준비한다.
        long sharedId = 9_000_000_000L;

        jdbcTemplate.update("""
                INSERT INTO community_posts (
                    id,
                    author_id,
                    post_type,
                    topic,
                    title,
                    content,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                sharedId,
                authorId,
                "GENERAL",
                "DAILY",
                "같은 ID 게시글",
                "게시글 본문",
                Timestamp.from(NOW),
                Timestamp.from(NOW)
        );

        jdbcTemplate.update("""
                INSERT INTO community_comments (
                    id,
                    post_id,
                    author_id,
                    content,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                sharedId,
                sharedId,
                authorId,
                "같은 ID 댓글",
                Timestamp.from(NOW),
                Timestamp.from(NOW)
        );

        Long postReportId = reportRepository.saveAndFlush(
                ContentReport.forPost(
                        reporterId,
                        sharedId,
                        ContentReportReasonType.SPAM_ADVERTISING,
                        null,
                        "같은 ID 게시글",
                        "게시글 본문",
                        NOW
                )
        ).getId();

        Long commentReportId = reportRepository.saveAndFlush(
                ContentReport.forComment(
                        reporterId,
                        sharedId,
                        ContentReportReasonType.ABUSE_HATE,
                        null,
                        "같은 ID 댓글",
                        NOW
                )
        ).getId();

        entityManager.clear();

        assertThat(postReportId).isNotEqualTo(commentReportId);

        assertThat(
                reportRepository.findByReporterMemberIdAndPostId(
                        reporterId,
                        sharedId
                ).orElseThrow().getId()
        ).isEqualTo(postReportId);

        assertThat(
                reportRepository.findByReporterMemberIdAndCommentId(
                        reporterId,
                        sharedId
                ).orElseThrow().getId()
        ).isEqualTo(commentReportId);
    }

    @Test
    void 이모지_500_코드포인트의_상세_설명을_저장한다() {
        String detail = "😀".repeat(500);

        Long reportId = reportRepository.saveAndFlush(
                ContentReport.forPost(
                        reporterId,
                        postId,
                        ContentReportReasonType.OTHER,
                        detail,
                        ORIGINAL_TITLE,
                        ORIGINAL_POST_CONTENT,
                        NOW
                )
        ).getId();

        entityManager.clear();

        ContentReport found =
                reportRepository.findById(reportId).orElseThrow();

        assertThat(found.getReasonDetail()).isEqualTo(detail);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UNKNOWN_REPORTER",
            "UNKNOWN_POST",
            "UNKNOWN_COMMENT",
            "NO_TARGET",
            "BOTH_TARGETS",
            "INVALID_REASON_TYPE",
            "OTHER_WITHOUT_DETAIL",
            "BLANK_DETAIL",
            "POST_WITHOUT_TITLE",
            "COMMENT_WITH_TITLE",
            "BLANK_TITLE",
            "BLANK_CONTENT",
            "INVALID_STATUS"
    })
    void 엔티티를_우회한_잘못된_입력도_DB_제약으로_차단한다(
            String scenario
    ) {
        Long reporter = reporterId;
        Long targetPost = postId;
        Long targetComment = null;

        String reasonType = "SPAM_ADVERTISING";
        String detail = null;
        String title = ORIGINAL_TITLE;
        String content = ORIGINAL_POST_CONTENT;
        String status = "RECEIVED";

        String constraint;

        switch (scenario) {
            case "UNKNOWN_REPORTER" -> {
                reporter = Long.MAX_VALUE;
                constraint = "fk_content_reports_reporter";
            }
            case "UNKNOWN_POST" -> {
                targetPost = Long.MAX_VALUE;
                constraint = "fk_content_reports_post";
            }
            case "UNKNOWN_COMMENT" -> {
                targetPost = null;
                targetComment = Long.MAX_VALUE;
                title = null;
                constraint = "fk_content_reports_comment";
            }
            case "NO_TARGET" -> {
                targetPost = null;
                title = null;
                constraint = "ck_content_reports_target";
            }
            case "BOTH_TARGETS" -> {
                targetComment = commentId;
                constraint = "ck_content_reports_target";
            }
            case "INVALID_REASON_TYPE" -> {
                reasonType = "LEGACY";
                constraint = "ck_content_reports_reason_type";
            }
            case "OTHER_WITHOUT_DETAIL" -> {
                reasonType = "OTHER";
                constraint = "ck_content_reports_other_detail";
            }
            case "BLANK_DETAIL" -> {
                detail = "   ";
                constraint = "ck_content_reports_reason_detail";
            }
            case "POST_WITHOUT_TITLE" -> {
                title = null;
                constraint = "ck_content_reports_title_snapshot";
            }
            case "COMMENT_WITH_TITLE" -> {
                targetPost = null;
                targetComment = commentId;
                constraint = "ck_content_reports_title_snapshot";
            }
            case "BLANK_TITLE" -> {
                title = "   ";
                constraint = "ck_content_reports_title_snapshot";
            }
            case "BLANK_CONTENT" -> {
                content = "   ";
                constraint = "ck_content_reports_content_snapshot";
            }
            case "INVALID_STATUS" -> {
                status = "UNKNOWN";
                constraint = "ck_content_reports_status";
            }
            default -> throw new IllegalArgumentException(scenario);
        }

        Object[] parameters = {
                reporter,
                targetPost,
                targetComment,
                reasonType,
                detail,
                title,
                content,
                status,
                Timestamp.from(NOW)
        };

        assertThatThrownBy(() ->
                jdbcTemplate.update(INSERT_REPORT_SQL, parameters)
        ).isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining(constraint);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "REPORTER",
            "REASON_TYPE",
            "CONTENT",
            "STATUS",
            "CREATED_AT"
    })
    void 필수_컬럼의_null은_DB에서_차단한다(String field) {
        Object[] parameters = {
                reporterId,
                postId,
                null,
                "SPAM_ADVERTISING",
                null,
                ORIGINAL_TITLE,
                ORIGINAL_POST_CONTENT,
                "RECEIVED",
                Timestamp.from(NOW)
        };

        int index = switch (field) {
            case "REPORTER" -> 0;
            case "REASON_TYPE" -> 3;
            case "CONTENT" -> 6;
            case "STATUS" -> 7;
            case "CREATED_AT" -> 8;
            default -> throw new IllegalArgumentException(field);
        };

        parameters[index] = null;

        assertThatThrownBy(() ->
                jdbcTemplate.update(INSERT_REPORT_SQL, parameters)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 엔티티를_우회해도_501자_상세_설명은_DB에서_차단한다() {
        Object[] parameters = {
                reporterId,
                postId,
                null,
                "OTHER",
                "가".repeat(501),
                ORIGINAL_TITLE,
                ORIGINAL_POST_CONTENT,
                "RECEIVED",
                Timestamp.from(NOW)
        };

        assertThatThrownBy(() ->
                jdbcTemplate.update(INSERT_REPORT_SQL, parameters)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 원문이_수정되고_숨김_삭제되어도_스냅샷은_유지된다() {
        Long postReportId = reportRepository.saveAndFlush(
                newReport(true, reporterId)
        ).getId();

        Long commentReportId = reportRepository.saveAndFlush(
                newReport(false, reporterId)
        ).getId();

        Post post = entityManager.find(Post.class, postId);
        Comment comment = entityManager.find(Comment.class, commentId);

        post.update(
                PostType.GENERAL,
                PostTopic.DAILY,
                "수정된 제목",
                "수정된 게시글 본문"
        );

        comment.update(
                "수정된 댓글 본문",
                NOW.plusSeconds(60)
        );

        post.hide(NOW.plusSeconds(120));
        comment.hide(NOW.plusSeconds(120));

        comment.delete(NOW.plusSeconds(180));
        post.delete(NOW.plusSeconds(180));

        entityManager.flush();
        entityManager.clear();

        Post changedPost = entityManager.find(Post.class, postId);
        Comment changedComment =
                entityManager.find(Comment.class, commentId);

        assertThat(changedPost.getTitle()).isEqualTo("수정된 제목");
        assertThat(changedPost.getContent()).isEqualTo("수정된 게시글 본문");
        assertThat(changedPost.getHiddenAt()).isNotNull();
        assertThat(changedPost.getDeletedAt()).isNotNull();

        assertThat(changedComment.getContent()).isEqualTo("수정된 댓글 본문");
        assertThat(changedComment.getHiddenAt()).isNotNull();
        assertThat(changedComment.getDeletedAt()).isNotNull();

        ContentReport postReport =
                reportRepository.findById(postReportId).orElseThrow();

        ContentReport commentReport =
                reportRepository.findById(commentReportId).orElseThrow();

        assertThat(postReport.getTitleSnapshot())
                .isEqualTo(ORIGINAL_TITLE);
        assertThat(postReport.getContentSnapshot())
                .isEqualTo(ORIGINAL_POST_CONTENT);

        assertThat(commentReport.getTitleSnapshot()).isNull();
        assertThat(commentReport.getContentSnapshot())
                .isEqualTo(ORIGINAL_COMMENT_CONTENT);

        // 콘텐츠 변경만으로 신고를 자동 종결하지 않는다.
        assertThat(postReport.getStatus())
                .isEqualTo(ContentReportStatus.RECEIVED);
        assertThat(commentReport.getStatus())
                .isEqualTo(ContentReportStatus.RECEIVED);
    }

    @Test
    void 신고자가_탈퇴해도_기존_신고는_유지된다() {
        Long reportId = reportRepository.saveAndFlush(
                newReport(true, reporterId)
        ).getId();

        Member reporter =
                memberRepository.findById(reporterId).orElseThrow();

        reporter.withdraw(NOW.plusSeconds(60));

        memberRepository.flush();
        entityManager.clear();

        Member withdrawn =
                memberRepository.findById(reporterId).orElseThrow();

        ContentReport found =
                reportRepository.findById(reportId).orElseThrow();

        assertThat(withdrawn.getStatus()).isEqualTo(MemberStatus.DELETED);
        assertThat(withdrawn.getEmail()).isNull();
        assertThat(withdrawn.getNickname()).isNull();

        assertThat(found.getReporterMemberId()).isEqualTo(reporterId);
        assertThat(found.getPostId()).isEqualTo(postId);
        assertThat(found.getTitleSnapshot()).isEqualTo(ORIGINAL_TITLE);
        assertThat(found.getContentSnapshot())
                .isEqualTo(ORIGINAL_POST_CONTENT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"REPORTER", "POST", "COMMENT"})
    void 신고가_참조하는_행의_물리_삭제는_차단한다(String target) {
        String deleteSql;
        Long deleteId;
        String constraint;

        switch (target) {
            case "REPORTER" -> {
                reportRepository.saveAndFlush(
                        newReport(true, reporterId)
                );

                deleteSql = "DELETE FROM members WHERE id = ?";
                deleteId = reporterId;
                constraint = "fk_content_reports_reporter";
            }
            case "POST" -> {
                // 댓글의 기존 외래 키 때문에 실패하는 것을 방지한다.
                // 신고의 외래 키만으로 게시글 삭제가 차단되는지 확인한다.
                jdbcTemplate.update(
                        "DELETE FROM community_comments WHERE id = ?",
                        commentId
                );

                reportRepository.saveAndFlush(
                        newReport(true, reporterId)
                );

                deleteSql = "DELETE FROM community_posts WHERE id = ?";
                deleteId = postId;
                constraint = "fk_content_reports_post";
            }
            case "COMMENT" -> {
                reportRepository.saveAndFlush(
                        newReport(false, reporterId)
                );

                deleteSql = "DELETE FROM community_comments WHERE id = ?";
                deleteId = commentId;
                constraint = "fk_content_reports_comment";
            }
            default -> throw new IllegalArgumentException(target);
        }

        assertThatThrownBy(() ->
                jdbcTemplate.update(deleteSql, deleteId)
        ).isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining(constraint);
    }

    private ContentReport newReport(
            boolean postTarget,
            Long reporter
    ) {
        if (postTarget) {
            return ContentReport.forPost(
                    reporter,
                    postId,
                    ContentReportReasonType.SPAM_ADVERTISING,
                    null,
                    ORIGINAL_TITLE,
                    ORIGINAL_POST_CONTENT,
                    NOW
            );
        }

        return ContentReport.forComment(
                reporter,
                commentId,
                ContentReportReasonType.ABUSE_HATE,
                null,
                ORIGINAL_COMMENT_CONTENT,
                NOW
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