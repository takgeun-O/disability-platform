package io.github.takgeun.iyum.admin.report.infrastructure;

import io.github.takgeun.iyum.admin.report.application.AdminReportCurrentTargetResult;
import io.github.takgeun.iyum.admin.report.application.AdminReportTargetAuthorResult;
import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.report.domain.ContentReport;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.member.domain.Member;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Testcontainers
@Transactional
@Execution(ExecutionMode.SAME_THREAD)
class AdminReportTargetReadRepositoryTest {

    private static final Instant NOW =
            Instant.parse("2026-11-12T03:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    EntityManager entityManager;

    @Autowired
    AdminReportTargetReadRepository repository;

    @Test
    void 여러_게시글과_작성자를_SQL_한번으로_조회한다() {
        Member firstAuthor = member("첫작성자");
        Member secondAuthor = member("둘째작성자");

        String firstContent = "첫 줄\n\t둘째 줄 😀";
        String secondContent = "가".repeat(130) + "\n마지막 줄";

        Post first = post(firstAuthor, "첫 제목", firstContent);
        Post second = post(secondAuthor, "둘째 제목", secondContent);
        post(firstAuthor, "제외 대상", "조회하지 않을 본문");

        Statistics statistics = beginMeasurement();

        Map<Long, AdminReportCurrentTargetResult> result =
                repository.findPosts(
                        Set.of(
                                first.getId(),
                                second.getId(),
                                Long.MAX_VALUE
                        )
                );

        assertThat(result)
                .containsOnlyKeys(first.getId(), second.getId());

        AdminReportCurrentTargetResult firstResult =
                result.get(first.getId());

        assertThat(firstResult.targetId()).isEqualTo(first.getId());
        assertThat(firstResult.parentPostId()).isNull();
        assertThat(firstResult.title()).isEqualTo("첫 제목");
        assertThat(firstResult.content()).isEqualTo(firstContent);
        assertThat(firstResult.hiddenAt()).isNull();
        assertThat(firstResult.deletedAt()).isNull();

        assertThat(firstResult.author().memberId())
                .isEqualTo(firstAuthor.getId());
        assertThat(firstResult.author().nickname()).isEqualTo("첫작성자");
        assertThat(firstResult.author().withdrawn()).isFalse();

        AdminReportCurrentTargetResult secondResult =
                result.get(second.getId());

        assertThat(secondResult.title()).isEqualTo("둘째 제목");
        assertThat(secondResult.content()).isEqualTo(secondContent);
        assertThat(secondResult.author().memberId())
                .isEqualTo(secondAuthor.getId());

        assertSelectCount(statistics, 1);
    }

    @Test
    void 댓글_작성자와_부모_게시글_ID를_SQL_한번으로_조회한다() {
        Member postAuthor = member("게시글작성자");
        Member firstAuthor = member("첫댓글작성자");
        Member secondAuthor = member("둘째댓글작성자");

        Post parent = post(postAuthor, "부모 제목", "부모 본문");

        String firstContent = "첫 댓글\n\t다음 줄";
        String secondContent = "나".repeat(130) + "😀";

        Comment first = comment(parent, firstAuthor, firstContent);
        Comment second = comment(parent, secondAuthor, secondContent);
        comment(parent, postAuthor, "조회하지 않을 댓글");

        Statistics statistics = beginMeasurement();

        Map<Long, AdminReportCurrentTargetResult> result =
                repository.findComments(
                        Set.of(
                                first.getId(),
                                second.getId(),
                                Long.MAX_VALUE
                        )
                );

        assertThat(result)
                .containsOnlyKeys(first.getId(), second.getId());

        AdminReportCurrentTargetResult firstResult =
                result.get(first.getId());

        assertThat(firstResult.targetId()).isEqualTo(first.getId());
        assertThat(firstResult.parentPostId()).isEqualTo(parent.getId());
        assertThat(firstResult.title()).isNull();
        assertThat(firstResult.content()).isEqualTo(firstContent);
        assertThat(firstResult.hiddenAt()).isNull();
        assertThat(firstResult.deletedAt()).isNull();

        // 부모 게시글 작성자가 아닌 댓글 작성자다.
        assertThat(firstResult.author().memberId())
                .isEqualTo(firstAuthor.getId());
        assertThat(firstResult.author().nickname())
                .isEqualTo("첫댓글작성자");

        AdminReportCurrentTargetResult secondResult =
                result.get(second.getId());

        assertThat(secondResult.parentPostId()).isEqualTo(parent.getId());
        assertThat(secondResult.title()).isNull();
        assertThat(secondResult.content()).isEqualTo(secondContent);
        assertThat(secondResult.author().memberId())
                .isEqualTo(secondAuthor.getId());
        assertThat(secondResult.author().withdrawn()).isFalse();

        assertSelectCount(statistics, 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "VISIBLE",
            "HIDDEN",
            "DELETED",
            "HIDDEN_DELETED"
    })
    void 숨김과_삭제_상태의_대상도_현재_정보를_조회한다(
            String state
    ) {
        Member author = member("상태확인작성자");
        Post parent = post(author, "상태 확인", "게시글 본문");
        Comment target = comment(parent, author, "대상 댓글");
        Comment visibleChild = comment(parent, author, "정상 댓글");

        boolean hidden = state.contains("HIDDEN");
        boolean deleted = state.contains("DELETED");

        if (hidden) {
            parent.hide(NOW);
            target.hide(NOW);
        }

        if (deleted) {
            parent.delete(NOW.plusSeconds(60));
            target.delete(NOW.plusSeconds(60));
        }

        Statistics statistics = beginMeasurement();

        AdminReportCurrentTargetResult postResult =
                repository.findPosts(Set.of(parent.getId()))
                        .get(parent.getId());

        Map<Long, AdminReportCurrentTargetResult> comments =
                repository.findComments(
                        Set.of(target.getId(), visibleChild.getId())
                );

        assertThat(postResult.content()).isEqualTo("게시글 본문");
        assertThat(postResult.hiddenAt())
                .isEqualTo(hidden ? NOW : null);
        assertThat(postResult.deletedAt())
                .isEqualTo(deleted ? NOW.plusSeconds(60) : null);

        assertThat(comments)
                .containsOnlyKeys(target.getId(), visibleChild.getId());

        AdminReportCurrentTargetResult targetResult =
                comments.get(target.getId());

        assertThat(targetResult.content()).isEqualTo("대상 댓글");
        assertThat(targetResult.parentPostId()).isEqualTo(parent.getId());
        assertThat(targetResult.hiddenAt())
                .isEqualTo(hidden ? NOW : null);
        assertThat(targetResult.deletedAt())
                .isEqualTo(deleted ? NOW.plusSeconds(60) : null);

        // 부모 상태를 댓글 자체의 상태로 바꾸어 표시하지 않는다.
        AdminReportCurrentTargetResult visibleResult =
                comments.get(visibleChild.getId());

        assertThat(visibleResult.content()).isEqualTo("정상 댓글");
        assertThat(visibleResult.hiddenAt()).isNull();
        assertThat(visibleResult.deletedAt()).isNull();

        assertSelectCount(statistics, 2);
    }

    @Test
    void 탈퇴한_작성자는_ID를_유지하고_탈퇴한_회원으로_표시한다() {
        Member author = member("탈퇴전닉네임");
        Post targetPost = post(author, "남은 제목", "남은 본문");
        Comment targetComment =
                comment(targetPost, author, "남은 댓글");

        author.withdraw(NOW);

        Statistics statistics = beginMeasurement();

        AdminReportTargetAuthorResult postAuthor =
                repository.findPosts(Set.of(targetPost.getId()))
                        .get(targetPost.getId())
                        .author();

        AdminReportTargetAuthorResult commentAuthor =
                repository.findComments(Set.of(targetComment.getId()))
                        .get(targetComment.getId())
                        .author();

        assertThat(postAuthor.memberId()).isEqualTo(author.getId());
        assertThat(postAuthor.nickname()).isEqualTo("탈퇴한 회원");
        assertThat(postAuthor.withdrawn()).isTrue();

        assertThat(commentAuthor).isEqualTo(postAuthor);

        assertSelectCount(statistics, 2);
    }

    @Test
    void 원문_수정_후_현재_본문과_접수_당시_스냅샷을_구분한다() {
        Member author = member("원문작성자");
        Member reporter = member("신고자");

        Post targetPost = post(author, "접수 당시 제목", "접수 당시 본문");
        Comment targetComment =
                comment(targetPost, author, "접수 당시 댓글");

        ContentReport postReport = ContentReport.forPost(
                reporter.getId(),
                targetPost.getId(),
                ContentReportReasonType.SPAM_ADVERTISING,
                null,
                targetPost.getTitle(),
                targetPost.getContent(),
                NOW
        );

        ContentReport commentReport = ContentReport.forComment(
                reporter.getId(),
                targetComment.getId(),
                ContentReportReasonType.ABUSE_HATE,
                null,
                targetComment.getContent(),
                NOW
        );

        entityManager.persist(postReport);
        entityManager.persist(commentReport);
        entityManager.flush();

        Long postReportId = postReport.getId();
        Long commentReportId = commentReport.getId();

        targetPost.update(
                PostType.GENERAL,
                PostTopic.DAILY,
                "수정된 제목",
                "수정된 본문\n다음 줄"
        );

        targetComment.update(
                "수정된 댓글\n다음 줄",
                NOW.plusSeconds(60)
        );

        Statistics statistics = beginMeasurement();

        AdminReportCurrentTargetResult currentPost =
                repository.findPosts(Set.of(targetPost.getId()))
                        .get(targetPost.getId());

        AdminReportCurrentTargetResult currentComment =
                repository.findComments(Set.of(targetComment.getId()))
                        .get(targetComment.getId());

        assertThat(currentPost.title()).isEqualTo("수정된 제목");
        assertThat(currentPost.content()).isEqualTo("수정된 본문\n다음 줄");
        assertThat(currentComment.content())
                .isEqualTo("수정된 댓글\n다음 줄");

        assertSelectCount(statistics, 2);

        ContentReport storedPostReport =
                entityManager.find(ContentReport.class, postReportId);
        ContentReport storedCommentReport =
                entityManager.find(ContentReport.class, commentReportId);

        assertThat(storedPostReport.getTitleSnapshot())
                .isEqualTo("접수 당시 제목");
        assertThat(storedPostReport.getContentSnapshot())
                .isEqualTo("접수 당시 본문");
        assertThat(storedCommentReport.getContentSnapshot())
                .isEqualTo("접수 당시 댓글");
    }

    @Test
    void 빈_ID_집합은_SQL을_실행하지_않는다() {
        Statistics statistics = beginMeasurement();

        assertThat(repository.findPosts(Set.of())).isEmpty();
        assertThat(repository.findComments(Set.of())).isEmpty();

        assertSelectCount(statistics, 0);
    }

    @Test
    void 존재하지_않는_ID는_결과에_포함하지_않는다() {
        Statistics statistics = beginMeasurement();

        assertThat(repository.findPosts(Set.of(Long.MAX_VALUE)))
                .isEmpty();
        assertThat(repository.findComments(Set.of(Long.MAX_VALUE)))
                .isEmpty();

        assertSelectCount(statistics, 2);
    }

    private Statistics beginMeasurement() {
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManager
                .getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();

        statistics.clear();

        return statistics;
    }

    private void assertSelectCount(
            Statistics statistics,
            long expected
    ) {
        assertThat(statistics.getQueryExecutionCount())
                .isEqualTo(expected);

        // 결과 변환 중 추가 SQL이 발생하는지도 함께 확인한다.
        assertThat(statistics.getPrepareStatementCount())
                .isEqualTo(expected);
    }

    private Member member(String nickname) {
        Member member = Member.createPending(
                UUID.randomUUID() + "@example.com",
                "test-only-password-hash",
                nickname
        );

        member.activeAfterEmailVerification();
        entityManager.persist(member);

        return member;
    }

    private Post post(
            Member author,
            String title,
            String content
    ) {
        Post post = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                title,
                content
        );

        entityManager.persist(post);

        return post;
    }

    private Comment comment(
            Post parent,
            Member author,
            String content
    ) {
        Comment comment = Comment.create(parent, author, content);

        entityManager.persist(comment);

        return comment;
    }
}