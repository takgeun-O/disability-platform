package io.github.takgeun.iyum.community.post.api;

import io.github.takgeun.iyum.community.post.application.PostLikeReadService;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostLike;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostLikeRepository;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true",
        "iyum.auth.password-reset.mail.enabled=false",
        "iyum.auth.email-verification.mail.enabled=false"
})
@AutoConfigureMockMvc(addFilters = true)
@Testcontainers
class PostWeeklyBestIntegrationTest {

    private static final String API_PATH =
            "/api/v1/community/posts/weekly-best";

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PostRepository postRepository;

    @Autowired
    PostLikeRepository postLikeRepository;

    @MockitoSpyBean
    PostLikeReadService postLikeReadService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 비회원이_주간_베스트를_조회하면_좋아요가_있는_공개_글만_순위대로_반환한다()
            throws Exception {
        Member author = saveMember("author@example.test", "베스트작성자");
        Member readerOne = saveMember("one@example.test", "첫번째독자");
        Member readerTwo = saveMember("two@example.test", "두번째독자");

        Post popularPost = savePost(author, "어제 작성한 인기 글");
        Post newerPost = savePost(author, "오늘 작성한 최신 글");
        Post hiddenPost = savePost(author, "좋아요를 받은 뒤 숨긴 글");
        savePost(author, "아직 좋아요가 없는 공개 글");

        // 최신순으로 잘못 조회하면 newerPost가 먼저 나오도록 시간을 지정한다.
        Instant now = Instant.now();
        setCreatedAt(popularPost, now.minus(Duration.ofDays(1)));
        setCreatedAt(newerPost, now.minusSeconds(60));

        postLikeRepository.saveAllAndFlush(List.of(
                PostLike.create(popularPost, readerOne),
                PostLike.create(popularPost, readerTwo),
                PostLike.create(newerPost, readerOne),
                PostLike.create(hiddenPost, readerOne)
        ));

        hiddenPost.hide(now);
        postRepository.saveAndFlush(hiddenPost);

        mockMvc.perform(
                        get(API_PATH)
                                .param("size", "4")
                )
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id")
                        .value(popularPost.getId()))
                .andExpect(jsonPath("$.items[0].likeCount").value(2))
                .andExpect(jsonPath("$.items[0].liked").value(false))
                .andExpect(jsonPath("$.items[1].id")
                        .value(newerPost.getId()))
                .andExpect(jsonPath("$.items[1].likeCount").value(1));
    }

    @Test
    void 순위_선정_직후_마지막_좋아요가_취소되어도_같은_조회에서는_선정_당시_수를_반환한다()
            throws Exception {
        Member author = saveMember("snapshot-author@example.test", "순위작성자");
        Member reader = saveMember("snapshot-reader@example.test", "순위독자");
        Post post = savePost(author, "조회 중 좋아요가 취소되는 글");
        postLikeRepository.saveAndFlush(PostLike.create(post, reader));

        // 응답용 좋아요 집계 직전에 별도 연결에서 취소를 커밋한다.
        doAnswer(invocation -> {
            try (var connection = POSTGRES.createConnection("");
                 var cancel = connection.prepareStatement(
                         "DELETE FROM community_post_likes WHERE post_id = ?"
                 )) {
                cancel.setLong(1, post.getId());
                cancel.executeUpdate();
            }
            return invocation.callRealMethod();
        }).when(postLikeReadService).getStates(anyList(), isNull());

        String match = "$.items[?(@.id == " + post.getId() + ")]";
        mockMvc.perform(get(API_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath(match + ".likeCount", contains(1)));

        mockMvc.perform(get(API_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath(match).isEmpty());
    }

    private Member saveMember(
            String email,
            String nickname
    ) {
        Member member = Member.createPending(
                email,
                "unused-password-hash",
                nickname
        );
        member.activeAfterEmailVerification();

        return memberRepository.saveAndFlush(member);
    }

    private Post savePost(
            Member author,
            String title
    ) {
        Post post = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                title,
                "주간 베스트 조회를 확인하는 본문입니다."
        );

        return postRepository.saveAndFlush(post);
    }

    private void setCreatedAt(
            Post post,
            Instant createdAt
    ) {
        jdbcTemplate.update(
                """
                UPDATE community_posts
                SET created_at = ?
                WHERE id = ?
                """,
                Timestamp.from(createdAt),
                post.getId()
        );
    }
}