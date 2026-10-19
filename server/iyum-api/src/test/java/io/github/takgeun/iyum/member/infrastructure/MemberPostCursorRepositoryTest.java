package io.github.takgeun.iyum.member.infrastructure;

import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true"
})
@Testcontainers
class MemberPostCursorRepositoryTest {

    private static final Instant BASE_TIME =
            Instant.parse("2026-10-19T10:00:00.123456Z");

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                POSTGRES::getJdbcUrl
        );
        registry.add(
                "spring.datasource.username",
                POSTGRES::getUsername
        );
        registry.add(
                "spring.datasource.password",
                POSTGRES::getPassword
        );
    }

    @Autowired
    PostRepository postRepository;

    @Autowired
    CommentRepository commentRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Member me;
    private Member emptyMember;

    private Post newest;
    private Post sameTimeLowerId;
    private Post sameTimeHigherId;
    private Post oldest;

    @BeforeEach
    void setUp() {
        // 테스트 전용 컨테이너의 데이터만 초기화한다.
        commentRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        me = saveMember(
                "my-posts@example.com",
                "내게시글회원"
        );

        Member other = saveMember(
                "other-posts@example.com",
                "다른게시글회원"
        );

        emptyMember = saveMember(
                "empty-posts@example.com",
                "게시글없는회원"
        );

        // 최신 글을 먼저 저장한다.
        // ID만 내림차순으로 정렬하는 잘못된 구현도 잡아내기 위한 구성이다.
        newest = savePost(
                me,
                PostTopic.DAILY,
                "가장 최신인 내 게시글",
                BASE_TIME.plusSeconds(120)
        );

        oldest = savePost(
                me,
                PostTopic.HEARING_AID,
                "가장 오래된 내 게시글",
                BASE_TIME
        );

        sameTimeLowerId = savePost(
                me,
                PostTopic.DAILY,
                "같은 시각의 작은 ID 게시글",
                BASE_TIME.plusSeconds(60)
        );

        sameTimeHigherId = savePost(
                me,
                PostTopic.HEARING_AID,
                "같은 시각의 큰 ID 게시글",
                BASE_TIME.plusSeconds(60)
        );

        // 첫 조회와 후속 조회 모두에서 제외되어야 하는 다른 회원의 글.
        savePost(
                other,
                PostTopic.DAILY,
                "다른 회원 게시글",
                BASE_TIME.plusSeconds(90)
        );

        // 첫 조회와 후속 조회 모두에서 제외되어야 하는 삭제된 내 글.
        Post deletedPost = savePost(
                me,
                PostTopic.DAILY,
                "삭제된 내 게시글",
                BASE_TIME.plusSeconds(80)
        );

        softDelete(deletedPost.getId());
    }

    @Test
    void 첫_조회는_카테고리와_관계없이_내_공개_게시글만_최신순으로_반환한다() {
        List<Post> posts =
                postRepository.findFirstVisiblePostsByAuthorId(
                        me.getId(),
                        PageRequest.of(0, 10)
                );

        assertThat(posts)
                .extracting(Post::getId)
                .containsExactly(
                        newest.getId(),
                        sameTimeHigherId.getId(),
                        sameTimeLowerId.getId(),
                        oldest.getId()
                );
    }

    @Test
    void 첫_조회는_지정한_조회_개수를_넘지_않는다() {
        List<Post> posts =
                postRepository.findFirstVisiblePostsByAuthorId(
                        me.getId(),
                        PageRequest.of(0, 2)
                );

        assertThat(posts)
                .extracting(Post::getId)
                .containsExactly(
                        newest.getId(),
                        sameTimeHigherId.getId()
                );
    }

    @Test
    void 후속_조회에서도_다른_회원의_글과_삭제된_글을_제외한다() {
        List<Post> posts =
                postRepository.findVisiblePostsByAuthorIdAfter(
                        me.getId(),
                        newest.getCreatedAt(),
                        newest.getId(),
                        PageRequest.of(0, 10)
                );

        assertThat(posts)
                .extracting(Post::getId)
                .containsExactly(
                        sameTimeHigherId.getId(),
                        sameTimeLowerId.getId(),
                        oldest.getId()
                );
    }

    @Test
    void 동일한_생성_시각의_경계에서도_중복이나_누락_없이_이어진다() {
        List<Post> first =
                postRepository.findFirstVisiblePostsByAuthorId(
                        me.getId(),
                        PageRequest.of(0, 2)
                );

        assertThat(first)
                .extracting(Post::getId)
                .containsExactly(
                        newest.getId(),
                        sameTimeHigherId.getId()
                );

        // 첫 조회에서 실제로 받은 마지막 게시글을 커서로 사용한다.
        Post cursorPost = first.getLast();

        List<Post> next =
                postRepository.findVisiblePostsByAuthorIdAfter(
                        me.getId(),
                        cursorPost.getCreatedAt(),
                        cursorPost.getId(),
                        PageRequest.of(0, 2)
                );

        assertThat(next)
                .extracting(Post::getId)
                .containsExactly(
                        sameTimeLowerId.getId(),
                        oldest.getId()
                );
    }

    @Test
    void 커서에_해당하는_게시글이_삭제되어도_다음_목록을_조회한다() {
        List<Post> first =
                postRepository.findFirstVisiblePostsByAuthorId(
                        me.getId(),
                        PageRequest.of(0, 2)
                );

        Post cursorPost = first.getLast();

        // 클라이언트가 커서를 받은 뒤 해당 게시글이 삭제된 상황.
        softDelete(cursorPost.getId());

        List<Post> next =
                postRepository.findVisiblePostsByAuthorIdAfter(
                        me.getId(),
                        cursorPost.getCreatedAt(),
                        cursorPost.getId(),
                        PageRequest.of(0, 2)
                );

        assertThat(next)
                .extracting(Post::getId)
                .containsExactly(
                        sameTimeLowerId.getId(),
                        oldest.getId()
                );
    }

    @Test
    void 마지막_게시글_이후를_조회하면_빈_목록을_반환한다() {
        List<Post> posts =
                postRepository.findVisiblePostsByAuthorIdAfter(
                        me.getId(),
                        oldest.getCreatedAt(),
                        oldest.getId(),
                        PageRequest.of(0, 10)
                );

        assertThat(posts).isEmpty();
    }

    @Test
    void 작성한_게시글이_없는_회원은_빈_목록을_반환한다() {
        List<Post> posts =
                postRepository.findFirstVisiblePostsByAuthorId(
                        emptyMember.getId(),
                        PageRequest.of(0, 10)
                );

        assertThat(posts).isEmpty();
    }

    private Member saveMember(
            String email,
            String nickname
    ) {
        Member member = Member.createPending(
                email,
                "test-only-password-hash",
                nickname
        );
        member.activeAfterEmailVerification();

        return memberRepository.saveAndFlush(member);
    }

    private Post savePost(
            Member author,
            PostTopic topic,
            String title,
            Instant createdAt
    ) {
        Post post = postRepository.saveAndFlush(
                Post.create(
                        author,
                        PostType.QUESTION,
                        topic,
                        title,
                        "내 게시글 커서 조회 테스트용 본문"
                )
        );

        // 정렬과 커서 비교를 일정하게 검증하려고
        // 테스트 데이터의 시각만 직접 지정한다.
        int updatedRows = jdbcTemplate.update("""
                UPDATE community_posts
                SET created_at = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt),
                post.getId()
        );

        assertThat(updatedRows).isEqualTo(1);

        // JDBC 변경은 기존 Java 객체에 자동 반영되지 않으므로
        // DB에서 다시 읽은 엔티티를 반환한다.
        return postRepository.findById(post.getId())
                .orElseThrow();
    }

    private void softDelete(Long postId) {
        int updatedRows = jdbcTemplate.update("""
                UPDATE community_posts
                SET deleted_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                postId
        );

        assertThat(updatedRows).isEqualTo(1);
    }
}