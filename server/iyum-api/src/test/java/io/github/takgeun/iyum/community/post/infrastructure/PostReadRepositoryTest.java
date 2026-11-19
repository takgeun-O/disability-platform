package io.github.takgeun.iyum.community.post.infrastructure;

import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostLike;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Testcontainers
@Transactional
class PostReadRepositoryTest {

    private static final Instant BASE_TIME =
            Instant.parse("2026-10-13T03:00:00Z");

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(
            DynamicPropertyRegistry registry
    ) {
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
    PostRepository postRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PostLikeRepository postLikeRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Long firstAuthorId;

    private Long oldQuestionId;
    private Long latestGeneralId;
    private Long recentQuestionId;
    private Long sameTimeQuestionId;
    private Long hearingAidGeneralId;
    private Long deletedId;

    @BeforeEach
    void preparePosts() {
        Member firstAuthor = createMember(
                "writer.one@example.com",
                "작성자하나"
        );

        Member secondAuthor = createMember(
                "writer.two@example.com",
                "작성자둘"
        );

        firstAuthorId = firstAuthor.getId();

        // 기준 시각 (미삭제)
        oldQuestionId = createPost(
                firstAuthor,
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                "오래된 질문",
                BASE_TIME
        );

        // 기준 + 300초 (미삭제)
        // ID는 작지만 작성 시각은 가장 최신인 공개 게시글이다.
        latestGeneralId = createPost(
                secondAuthor,
                PostType.GENERAL,
                PostTopic.DAILY,
                "최신 일반 글",
                BASE_TIME.plusSeconds(300)
        );

        // 기준 + 200초 (미삭제)
        recentQuestionId = createPost(
                firstAuthor,
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                "최근 보청기 질문",
                BASE_TIME.plusSeconds(200)
        );

        // 기준 + 200초 (미삭제)
        // 바로 앞 글과 시각은 같지만 ID는 더 크다.
        sameTimeQuestionId = createPost(
                secondAuthor,
                PostType.QUESTION,
                PostTopic.DAILY,
                "같은 시각의 일상 질문",
                BASE_TIME.plusSeconds(200)
        );

        // 기준 + 100초 (미삭제)
        hearingAidGeneralId = createPost(
                firstAuthor,
                PostType.GENERAL,
                PostTopic.HEARING_AID,
                "보청기 일반 글",
                BASE_TIME.plusSeconds(100)
        );

        // 기준 + 400초 (삭제됨)
        deletedId = createPost(
                secondAuthor,
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                "삭제된 최신 질문",
                BASE_TIME.plusSeconds(400)
        );

        // 삭제 기능 구현 전이므로 테스트 데이터만 SQL로 준비한다.
        jdbcTemplate.update(
                """
                UPDATE community_posts
                SET deleted_at = ?
                WHERE id = ?
                """,
                Timestamp.from(BASE_TIME.plusSeconds(500)),
                deletedId
        );

        // 이후 조회가 준비 과정의 관리 객체에 의존하지 않도록 한다.
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void 삭제되지_않은_글을_작성자와_함께_최신순으로_조회한다() {

        // 유형·주제 제한 없이 첫 페이지를 조회
        Page<Post> page = postRepository.findVisiblePosts(
                null,
                null,
                PageRequest.of(0, 20)
        );

        // 6개의 게시글 중 삭제된 게시글인 deletedId 가 나오면 안된다.
        assertThat(page.getContent())   // 현재 페이지의 List<Post>
                .extracting(Post::getId)    // 각 게시글에서 ID만 추출
                .containsExactly(
                        latestGeneralId,
                        sameTimeQuestionId,
                        recentQuestionId,
                        hearingAidGeneralId,
                        oldQuestionId
                );

        assertThat(page.getTotalElements()).isEqualTo(5);

        // 조회 후 영속성 컨텍스트를 비워도 작성자 정보가 있어야 한다.
        entityManager.clear();

        // allSatisfy() : 목록의 모든 게시글에 대해 내부 검증을 실행한다
        // 조회 후 영속성 컨텍스트를 비웠으니 그 뒤에도 닉네임에 접근할 수 있는지 본다.
        // join fetch p.author로 작성자를 미리 가져왔다면 통과하는거고,
        // 작성자가 초기화되지 않은 지연 로딩 상태라면 보통 LazyInitializationException으로 실패할 것.
        assertThat(page.getContent()).allSatisfy(post ->
                assertThat(post.getAuthor().getNickname())
                        .isNotBlank()
        );
    }

    @Test
    void 페이지별_목록과_전체_개수를_정확히_반환한다() {
        Page<Post> first = postRepository.findVisiblePosts(
                null,
                null,
                PageRequest.of(0, 2)
        );

        Page<Post> second = postRepository.findVisiblePosts(
                null,
                null,
                PageRequest.of(1, 2)
        );

        Page<Post> last = postRepository.findVisiblePosts(
                null,
                null,
                PageRequest.of(2, 2)
        );

        assertThat(first.getContent())
                .extracting(Post::getId)
                .containsExactly(
                        latestGeneralId,
                        sameTimeQuestionId
                );

        assertThat(second.getContent())
                .extracting(Post::getId)
                .containsExactly(
                        recentQuestionId,
                        hearingAidGeneralId
                );

        assertThat(last.getContent())
                .extracting(Post::getId)
                .containsExactly(oldQuestionId);

        assertThat(first.getNumber()).isZero();
        assertThat(first.getSize()).isEqualTo(2);
        assertThat(first.getTotalElements()).isEqualTo(5);
        assertThat(first.getTotalPages()).isEqualTo(3);
        assertThat(first.hasNext()).isTrue();

        assertThat(last.getTotalElements()).isEqualTo(5);
        assertThat(last.hasNext()).isFalse();

        Page<Post> beyondLast = postRepository.findVisiblePosts(
                null,
                null,
                PageRequest.of(10, 2)
        );

        assertThat(beyondLast.getContent()).isEmpty();
        assertThat(beyondLast.getTotalElements()).isEqualTo(5);
    }

    @Test
    void 유형으로_필터링한다() {
        Page<Post> page = postRepository.findVisiblePosts(
                PostType.QUESTION,
                null,
                PageRequest.of(0, 20)
        );

        assertThat(page.getContent())
                .extracting(Post::getId)
                .containsExactly(
                        sameTimeQuestionId,
                        recentQuestionId,
                        oldQuestionId
                );

        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void 주제로_필터링한다() {
        Page<Post> page = postRepository.findVisiblePosts(
                null,
                PostTopic.HEARING_AID,
                PageRequest.of(0, 20)
        );

        assertThat(page.getContent())
                .extracting(Post::getId)
                .containsExactly(
                        recentQuestionId,
                        hearingAidGeneralId,
                        oldQuestionId
                );

        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void 유형과_주제를_함께_적용하고_개수에도_반영한다() {
        // 일치하는 공개 글은 2건이다.
        // 페이지 크기를 1로 지정해 countQuery도 확인한다.
        Page<Post> first = postRepository.findVisiblePosts(
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                PageRequest.of(0, 1)
        );

        Page<Post> second = postRepository.findVisiblePosts(
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                PageRequest.of(1, 1)
        );

        assertThat(first.getContent())
                .extracting(Post::getId)
                .containsExactly(recentQuestionId);

        assertThat(second.getContent())
                .extracting(Post::getId)
                .containsExactly(oldQuestionId);

        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.hasNext()).isTrue();
        assertThat(second.hasNext()).isFalse();
    }

    @Test
    void 조건에_맞는_게시글이_없으면_빈_페이지를_반환한다() {
        Page<Post> page = postRepository.findVisiblePosts(
                PostType.REVIEW,
                null,
                PageRequest.of(0, 20)
        );

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getTotalPages()).isZero();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void 공개_게시글의_상세와_작성자를_함께_조회한다() {
        Post post = postRepository
                .findVisiblePostById(recentQuestionId)
                .orElseThrow();

        entityManager.clear();

        assertThat(post.getId()).isEqualTo(recentQuestionId);
        assertThat(post.getTitle()).isEqualTo("최근 보청기 질문");
        assertThat(post.getContent())
                .isEqualTo("최근 보청기 질문 본문");

        assertThat(post.getType()).isEqualTo(PostType.QUESTION);
        assertThat(post.getTopic()).isEqualTo(PostTopic.HEARING_AID);
        assertThat(post.getDeletedAt()).isNull();

        assertThat(post.getAuthor().getId())
                .isEqualTo(firstAuthorId);

        assertThat(post.getAuthor().getNickname())
                .isEqualTo("작성자하나");
    }

    @Test
    void 삭제된_게시글은_DB에_있어도_상세_조회되지_않는다() {
        assertThat(postRepository.existsById(deletedId))
                .isTrue();

        assertThat(postRepository.findVisiblePostById(deletedId))
                .isEmpty();
    }

    @Test
    void 존재하지_않는_게시글은_빈_Optional을_반환한다() {
        assertThat(
                postRepository.findVisiblePostById(Long.MAX_VALUE)
        ).isEmpty();
    }

    @Test
    void 제목이나_본문이_일치하는_공개_게시글만_필터와_페이지_건수에_포함한다() {
        // 제목만 일치하는 공개 글
        jdbcTemplate.update(
                "update community_posts set title = ? where id = ?",
                "LIVE 자막 사용 후기",
                recentQuestionId
        );

        // 본문만 일치하는 공개 글
        // 나머지 세 글은 각각 유형 불일치, 주제 불일치, 삭제 상태다.
        jdbcTemplate.update(
                """
                update community_posts
                set content = ?
                where id in (?, ?, ?, ?)
                """,
                "회의에서 Live 자막을 사용했습니다.",
                oldQuestionId,
                hearingAidGeneralId,
                sameTimeQuestionId,
                deletedId
        );

        // 검색어와 유형·주제가 모두 일치하지만 숨겨진 글
        Member author = memberRepository.findById(firstAuthorId)
                .orElseThrow();

        Long hiddenId = createPost(
                author,
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                "LIVE 자막 설정",
                BASE_TIME.plusSeconds(600)
        );

        jdbcTemplate.update(
                "update community_posts set hidden_at = ? where id = ?",
                Timestamp.from(BASE_TIME.plusSeconds(601)),
                hiddenId
        );

        // 같은 유형·주제의 공개 글이어도 검색어가 없으면 제외돼야 한다.
        createPost(
                author,
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                "배터리 교체 주기",
                BASE_TIME.plusSeconds(700)
        );

        entityManager.clear();

        Page<Post> first = postRepository.findVisiblePosts(
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                "live 자막",
                PageRequest.of(0, 1)
        );

        Page<Post> second = postRepository.findVisiblePosts(
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                "live 자막",
                PageRequest.of(1, 1)
        );

        assertThat(first.getContent())
                .extracting(Post::getId)
                .containsExactly(recentQuestionId);

        assertThat(second.getContent())
                .extracting(Post::getId)
                .containsExactly(oldQuestionId);

        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.hasNext()).isTrue();
        assertThat(second.hasNext()).isFalse();
    }

    @Test
    void 퍼센트와_밑줄은_입력한_문자_그대로_검색한다() {
        jdbcTemplate.update(
                "update community_posts set title = ? where id = ?",
                "보청기 50%_할인 안내",
                oldQuestionId
        );

        jdbcTemplate.update(
                "update community_posts set title = ? where id = ?",
                "보청기 50특별_할인 안내",
                recentQuestionId
        );

        jdbcTemplate.update(
                "update community_posts set title = ? where id = ?",
                "보청기 50%X할인 안내",
                latestGeneralId
        );

        Page<Post> result = postRepository.findVisiblePosts(
                null,
                null,
                "50%_할인",
                PageRequest.of(0, 10)
        );

        assertThat(result.getContent())
                .extracting(Post::getId)
                .containsExactly(oldQuestionId);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void 공개_검색은_본문의_첫_일치_위치를_반환하고_제목만_일치하면_0을_반환한다() {
        jdbcTemplate.update(
                "update community_posts set content = ? where id = ?",
                "😀CAFÉ 그리고 café",
                oldQuestionId
        );

        jdbcTemplate.update(
                "update community_posts set title = ? where id = ?",
                "café 방문 후기",
                recentQuestionId
        );

        Page<PostSearchRow> result = postRepository.findVisiblePostSearchRows(
                null,
                null,
                "café",
                PageRequest.of(0, 10)
        );

        assertThat(result.getTotalElements()).isEqualTo(2);

        assertThat(result.getContent())
                .extracting(row -> row.getPost().getId())
                .containsExactly(recentQuestionId, oldQuestionId);

        assertThat(result.getContent())
                .extracting(PostSearchRow::getBodyMatchPosition)
                .containsExactly(0, 2);
    }

    @Test
    void 본문_검색은_마침표와_연속_공백을_문자_그대로_비교한다() {
        String updateContent =
                "update community_posts set content = ? where id = ?";

        jdbcTemplate.update(updateContent, "A.B  안내", oldQuestionId);
        jdbcTemplate.update(updateContent, "AXB  안내", recentQuestionId);
        jdbcTemplate.update(updateContent, "A.B 안내", latestGeneralId);

        Page<PostSearchRow> result = postRepository.findVisiblePostSearchRows(
                null,
                null,
                "a.b  안내",
                PageRequest.of(0, 10)
        );

        assertThat(result.getContent())
                .extracting(row -> row.getPost().getId())
                .containsExactly(oldQuestionId);
    }

    @Test
    void 주간_베스트를_한_건_조회하면_최신글보다_좋아요가_많은_글이_먼저_나온다() {
        Instant asOf = BASE_TIME;
        Instant fromInclusive = asOf.minus(Duration.ofDays(7));

        Member author = createMember("best.author@example.com", "베스트작성자");
        Member readerOne = createMember("best.one@example.com", "베스트독자하나");
        Member readerTwo = createMember("best.two@example.com", "베스트독자둘");

        Long popularPostId = createPost(
                author, PostType.GENERAL, PostTopic.DAILY,
                "6일 전 작성한 인기 글", asOf.minus(Duration.ofDays(6))
        );
        Long newerPostId = createPost(
                author, PostType.GENERAL, PostTopic.DAILY,
                "1분 전 작성한 최신 글", asOf.minusSeconds(60)
        );

        Post popularPost = entityManager.getReference(Post.class, popularPostId);
        Post newerPost = entityManager.getReference(Post.class, newerPostId);

        entityManager.persist(PostLike.create(popularPost, readerOne));
        entityManager.persist(PostLike.create(popularPost, readerTwo));
        entityManager.persist(PostLike.create(newerPost, readerOne));
        entityManager.flush();
        entityManager.clear();

        var result = postLikeRepository.findWeeklyBestPostIds(
                fromInclusive, asOf, PageRequest.of(0, 1)
        );

        assertThat(result).containsExactly(popularPostId);
    }

    @Test
    void 주간_베스트는_정확히_7일_전부터_기준_시각까지_작성된_글을_포함한다() {
        Instant asOf = BASE_TIME;
        Instant fromInclusive = asOf.minus(Duration.ofDays(7));

        Member author = entityManager.getReference(Member.class, firstAuthorId);
        Member reader = createMember("boundary.reader@example.com", "경계독자");

        Long beforeStartId = createPost(
                author, PostType.GENERAL, PostTopic.DAILY,
                "시작 1초 전", fromInclusive.minusSeconds(1)
        );
        Long atStartId = createPost(
                author, PostType.GENERAL, PostTopic.DAILY,
                "정확히 7일 전", fromInclusive
        );
        Long afterEndId = createPost(
                author, PostType.GENERAL, PostTopic.DAILY,
                "기준 시각 1초 후", asOf.plusSeconds(1)
        );

        addLike(beforeStartId, reader);
        addLike(atStartId, reader);
        addLike(oldQuestionId, reader);
        addLike(afterEndId, reader);
        entityManager.flush();
        entityManager.clear();

        var result = postLikeRepository.findWeeklyBestPostIds(
                fromInclusive, asOf, PageRequest.of(0, 10)
        );

        assertThat(result).containsExactly(oldQuestionId, atStartId);
    }

    @Test
    void 주간_베스트는_숨김글과_삭제글과_좋아요가_없는_글을_제외한다() {
        Instant asOf = BASE_TIME.plusSeconds(600);
        Member reader = createMember("visible.reader@example.com", "공개독자");

        addLike(oldQuestionId, reader);       // 공개, 좋아요 1개
        addLike(recentQuestionId, reader);    // 좋아요를 받은 뒤 숨김
        addLike(deletedId, reader);           // 삭제 후에도 좋아요 행이 남은 상태
        // latestGeneralId 등 나머지 공개 글은 좋아요 0개

        Post hiddenPost = entityManager.find(Post.class, recentQuestionId);
        hiddenPost.hide(asOf);

        entityManager.flush();
        entityManager.clear();

        var result = postLikeRepository.findWeeklyBestPostIds(
                asOf.minus(Duration.ofDays(7)), asOf, PageRequest.of(0, 10)
        );

        assertThat(result).containsExactly(oldQuestionId);
    }

    @Test
    void 주간_베스트는_좋아요가_같으면_작성_시각과_ID_내림차순으로_조회한다() {
        Instant asOf = BASE_TIME.plusSeconds(600);
        Member reader = createMember("ranking.reader@example.com", "순위독자");

        addLike(latestGeneralId, reader);      // BASE_TIME + 300초
        addLike(recentQuestionId, reader);     // BASE_TIME + 200초, 작은 ID
        addLike(sameTimeQuestionId, reader);   // BASE_TIME + 200초, 큰 ID
        entityManager.flush();
        entityManager.clear();

        var result = postLikeRepository.findWeeklyBestPostIds(
                asOf.minus(Duration.ofDays(7)), asOf, PageRequest.of(0, 10)
        );

        assertThat(result).containsExactly(
                latestGeneralId,
                sameTimeQuestionId,
                recentQuestionId
        );
    }

    @Test
    void 베스트_ID_순서를_유지하면서_현재_공개된_글과_작성자만_조회한다() {
        Post hiddenPost = entityManager.find(Post.class, recentQuestionId);
        hiddenPost.hide(BASE_TIME.plusSeconds(600));

        entityManager.flush();
        entityManager.clear();

        List<Long> rankedIds = List.of(
                oldQuestionId,
                recentQuestionId,     // 숨김
                deletedId,            // 삭제
                Long.MAX_VALUE,       // 존재하지 않음
                latestGeneralId
        );

        List<Post> result = postRepository.findVisiblePostsInRankOrder(rankedIds);

        // 조회 후 영속성 컨텍스트를 비워도 작성자 닉네임을 읽을 수 있어야 한다.
        entityManager.clear();

        assertThat(result)
                .extracting(Post::getId)
                .containsExactly(oldQuestionId, latestGeneralId);

        assertThat(result)
                .extracting(post -> post.getAuthor().getNickname())
                .containsExactly("작성자하나", "작성자둘");
    }

    @Test
    void 베스트_ID가_비어_있으면_빈_목록을_반환한다() {
        assertThat(postRepository.findVisiblePostsInRankOrder(List.of()))
                .isEmpty();
    }

    private Member createMember(
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

    private Long createPost(
            Member author,
            PostType type,
            PostTopic topic,
            String title,
            Instant createdAt
    ) {
        Post post = postRepository.saveAndFlush(
                Post.create(
                        author,
                        type,
                        topic,
                        title,
                        title + " 본문"
                )
        );

        // 정렬을 정확히 검증하기 위해 테스트에서 작성 시각을 지정한다.
        jdbcTemplate.update(
                """
                UPDATE community_posts
                SET created_at = ?, updated_at = ?
                WHERE id = ?
                """,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt),
                post.getId()
        );

        return post.getId();
    }

    private void addLike(
            Long postId,
            Member reader
    ) {
        Post post = entityManager.getReference(Post.class, postId);
        entityManager.persist(PostLike.create(post, reader));
    }
}