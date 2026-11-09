package io.github.takgeun.iyum.admin.operation.infrastructure;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Dedicated context/container; statistics are never shared with other test classes.
@SpringBootTest(properties = {
        "spring.config.import=", "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true", "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Testcontainers
@Transactional
@Execution(ExecutionMode.SAME_THREAD)
class AdminOperationLogTargetReadRepositoryTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired EntityManager em;
    @Autowired AdminOperationLogTargetReadRepository repository;
    private static final Instant NOW = Instant.parse("2026-11-04T03:00:00Z");

    @Test
    void postBatchMapsDistinctAuthorsAndStoredPreviewWithOneSelect() {
        Member a = member("작성자하나"), b = member("작성자둘");
        Post first = post(a, "첫 제목", "첫 줄\n\t둘째\u00a0\u3000줄 😀");
        Post second = post(b, "둘째 제목", "가".repeat(119) + "😀나");
        post(a, "요청하지 않은 제목", "제외할 본문");
        Statistics stats = beginMeasurement();
        var result = repository.findPostSummaries(Set.of(first.getId(), second.getId(), Long.MAX_VALUE));
        assertThat(result).containsOnlyKeys(first.getId(), second.getId());
        assertThat(result.get(first.getId()).author().memberId()).isEqualTo(a.getId());
        assertThat(result.get(first.getId()).author().nickname()).isEqualTo("작성자하나");
        assertThat(result.get(first.getId()).author().withdrawn()).isFalse();
        assertThat(result.get(first.getId()).title()).isEqualTo("첫 제목");
        assertThat(result.get(first.getId()).contentPreview()).isEqualTo("첫 줄 둘째 줄 😀");
        assertThat(result.get(second.getId()).author().memberId()).isEqualTo(b.getId());
        assertThat(result.get(second.getId()).title()).isEqualTo("둘째 제목");
        assertThat(result.get(second.getId()).contentPreview()).isEqualTo("가".repeat(119) + "😀…");
        assertSelects(stats, 1);
    }

    @Test
    void commentBatchMapsDistinctAuthorsAndNullTitlesWithOneSelect() {
        Member a = member("댓글하나"), b = member("댓글둘");
        Post parent = post(a, "부모", "부모 본문");
        Comment first = comment(parent, a, "첫 댓글\n\t다음 줄 😀");
        Comment second = comment(parent, b, "가".repeat(119) + "😀나");
        comment(parent, a, "조회 제외");
        Statistics stats = beginMeasurement();
        var result = repository.findCommentSummaries(Set.of(first.getId(), second.getId(), Long.MAX_VALUE));
        assertThat(result).containsOnlyKeys(first.getId(), second.getId());
        assertThat(result.get(first.getId()).author().memberId()).isEqualTo(a.getId());
        assertThat(result.get(first.getId()).author().nickname()).isEqualTo("댓글하나");
        assertThat(result.get(second.getId()).author().memberId()).isEqualTo(b.getId());
        assertThat(result.get(second.getId()).author().nickname()).isEqualTo("댓글둘");
        assertThat(result.values()).allSatisfy(item -> {
            assertThat(item.title()).isNull();
            assertThat(item.author().withdrawn()).isFalse();
        });
        assertThat(result.get(first.getId()).contentPreview()).isEqualTo("첫 댓글 다음 줄 😀");
        assertThat(result.get(second.getId()).contentPreview()).isEqualTo("가".repeat(119) + "😀…");
        assertSelects(stats, 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"VISIBLE", "HIDDEN", "DELETED", "HIDDEN_DELETED"})
    void allTargetStatesAndParentStatesRemainReadable(String state) {
        Member author = member("상태작성자");
        Post parent = post(author, "상태 제목", "게시글 전체 본문");
        Comment target = comment(parent, author, "댓글 전체 본문");
        Comment visibleChild = comment(parent, author, "정상 댓글");
        if (state.contains("HIDDEN")) { parent.hide(NOW); target.hide(NOW); }
        if (state.contains("DELETED")) { parent.delete(NOW); target.delete(NOW); }
        beginMeasurement();
        assertThat(repository.findPostSummaries(Set.of(parent.getId())).get(parent.getId()).contentPreview())
                .isEqualTo("게시글 전체 본문");
        var comments = repository.findCommentSummaries(Set.of(target.getId(), visibleChild.getId()));
        assertThat(comments).containsOnlyKeys(target.getId(), visibleChild.getId());
        assertThat(comments.get(target.getId()).contentPreview()).isEqualTo("댓글 전체 본문");
        assertThat(comments.get(visibleChild.getId()).contentPreview()).isEqualTo("정상 댓글");
    }

    @Test
    void withdrawnAuthorUsesAdministrativeDisplayForBothTargets() {
        Member author = member("탈퇴전이름");
        Post post = post(author, "남겨진 제목", "남겨진 본문");
        Comment comment = comment(post, author, "남겨진 댓글");
        author.withdraw(NOW);
        beginMeasurement();
        var postAuthor = repository.findPostSummaries(Set.of(post.getId())).get(post.getId()).author();
        var commentAuthor = repository.findCommentSummaries(Set.of(comment.getId())).get(comment.getId()).author();
        assertThat(postAuthor.memberId()).isEqualTo(author.getId());
        assertThat(postAuthor.nickname()).isEqualTo("탈퇴한 회원");
        assertThat(postAuthor.withdrawn()).isTrue();
        assertThat(commentAuthor).isEqualTo(postAuthor);
    }

    @Test
    void emptyIdsExecuteNoSql() {
        Statistics stats = beginMeasurement();
        assertThat(repository.findPostSummaries(Set.of())).isEmpty();
        assertThat(repository.findCommentSummaries(Set.of())).isEmpty();
        assertSelects(stats, 0);
    }

    private Statistics beginMeasurement() {
        em.flush();
        em.clear();
        Statistics stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        return stats;
    }
    private void assertSelects(Statistics stats, long expected) {
        // Query execution and all JDBC statements include conversion/lazy author access.
        assertThat(stats.getQueryExecutionCount()).isEqualTo(expected);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(expected);
        System.out.println("currentTarget measured SELECT/JDBC statements=" + expected);
    }
    private Member member(String nickname) {
        Member member = Member.createPending(UUID.randomUUID() + "@example.com", "test-only-hash", nickname);
        member.activeAfterEmailVerification();
        em.persist(member);
        return member;
    }
    private Post post(Member author, String title, String body) {
        Post post = Post.create(author, PostType.GENERAL, PostTopic.DAILY, title, body);
        em.persist(post);
        return post;
    }
    private Comment comment(Post parent, Member author, String body) {
        Comment comment = Comment.create(parent, author, body);
        em.persist(comment);
        return comment;
    }
}
