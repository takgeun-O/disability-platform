package io.github.takgeun.iyum.community.comment.api;

import io.github.takgeun.iyum.community.comment.application.CommentDeleteService;
import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.domain.CommentLike;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentLikeRepository;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.post.application.PostDeleteService;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
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
class CommentLikeSessionIntegrationTest {

    private static final String PASSWORD = "CommentLikeSession123!";

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
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PostRepository postRepository;

    @Autowired
    CommentRepository commentRepository;

    @Autowired
    CommentLikeRepository commentLikeRepository;

    @Autowired
    CommentDeleteService commentDeleteService;

    @Autowired
    PostDeleteService postDeleteService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Member me;
    private Member other;
    private Long postId;
    private Long commentId;

    @BeforeEach
    void setUp() {
        // 이 클래스의 Testcontainers 전용 DB를 정리한다.
        commentLikeRepository.deleteAllInBatch();
        commentRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        String passwordHash = passwordEncoder.encode(PASSWORD);

        me = saveActiveMember(
                "comment-like-session-me@example.com",
                "댓글좋아요회원",
                passwordHash
        );

        other = saveActiveMember(
                "comment-like-session-other@example.com",
                "다른좋아요회원",
                passwordHash
        );

        Post post = savePost(me);

        Comment comment = commentRepository.saveAndFlush(
                Comment.create(post, me, "댓글 좋아요 세션 테스트")
        );

        postId = post.getId();
        commentId = comment.getId();
    }

    @Test
    void 삭제된_원댓글의_공개_답글은_답글_작성자만_수정하고_삭제한다() throws Exception {
        CsrfSession owner = login(other.getEmail());
        CsrfSession parentOwner = login(me.getEmail());
        String comments = "/api/v1/community/posts/" + postId + "/comments";
        String replies = comments + "/" + commentId + "/replies";
        MvcResult created = mockMvc.perform(post(replies)
                        .session(owner.session())
                        .header(owner.headerName(), owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"내 답글\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsByteArray())
                .get("commentId").asLong();
        commentDeleteService.delete(me.getId(), postId, commentId);

        for (HttpMethod method : new HttpMethod[]{HttpMethod.PUT, HttpMethod.DELETE}) {
            mockMvc.perform(request(method, comments + "/" + id)
                            .session(parentOwner.session())
                            .header(parentOwner.headerName(), parentOwner.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"다른 사람의 변경\"}"))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(request(HttpMethod.PUT, comments + "/" + id)
                        .session(owner.session())
                        .header(owner.headerName(), owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"수정한 답글\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(replies))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].commentId").value(id))
                .andExpect(jsonPath("$.items[0].content").value("수정한 답글"))
                .andExpect(jsonPath("$.items[0].contentUpdatedAt").isString());
        mockMvc.perform(request(HttpMethod.DELETE, comments + "/" + id)
                        .session(owner.session())
                        .header(owner.headerName(), owner.token()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(comments))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void 실제_로그인_세션으로_등록과_취소를_반복할_수_있다()
            throws Exception {
        CsrfSession client = login(me.getEmail());

        assertSuccess(change("PUT", client), true, 1);

        Long firstLikeId = commentLikeRepository
                .findByComment_IdAndMember_Id(commentId, me.getId())
                .orElseThrow()
                .getId();

        // 중복 등록은 같은 행을 유지한다.
        assertSuccess(change("PUT", client), true, 1);

        assertThat(
                commentLikeRepository
                        .findByComment_IdAndMember_Id(commentId, me.getId())
                        .orElseThrow()
                        .getId()
        ).isEqualTo(firstLikeId);

        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isEqualTo(1L);

        // 최초 취소와 중복 취소
        assertSuccess(change("DELETE", client), false, 0);
        assertSuccess(change("DELETE", client), false, 0);

        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isZero();

        assertThat(
                commentLikeRepository.findByComment_IdAndMember_Id(
                        commentId, me.getId()
                )
        ).isEmpty();

        // 취소 후 재등록
        assertSuccess(change("PUT", client), true, 1);

        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isEqualTo(1L);

        // 좋아요 처리 후에도 로그인 상태를 유지한다.
        mockMvc.perform(
                        get("/api/v1/auth/me")
                                .session(client.session())
                )
                .andExpect(status().isOk());

        assertThat(client.session().isInvalid()).isFalse();
    }

    @Test
    void 각_세션은_자신의_댓글_좋아요만_변경한다()
            throws Exception {
        CsrfSession myClient = login(me.getEmail());
        CsrfSession otherClient = login(other.getEmail());

        assertThat(myClient.session().getId())
                .isNotEqualTo(otherClient.session().getId());

        assertSuccess(change("PUT", myClient), true, 1);
        assertSuccess(change("PUT", otherClient), true, 2);

        // 다른 회원 ID를 보내도 내 좋아요만 취소해야 한다.
        ResultActions cancellation = mockMvc.perform(
                request(HttpMethod.DELETE, likeUrl())
                        .session(myClient.session())
                        .header(myClient.headerName(), myClient.token())
                        .param("memberId", other.getId().toString())
        );

        assertSuccess(cancellation, false, 1);

        assertThat(
                commentLikeRepository.findByComment_IdAndMember_Id(
                        commentId, me.getId()
                )
        ).isEmpty();

        assertThat(
                commentLikeRepository.findByComment_IdAndMember_Id(
                        commentId, other.getId()
                )
        ).isPresent();

        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE"})
    void 비로그인_세션은_실제_CSRF_토큰이_있어도_거절된다(
            String method
    ) throws Exception {
        if (method.equals("DELETE")) {
            CsrfSession owner = login(me.getEmail());
            assertSuccess(change("PUT", owner), true, 1);
        }

        long beforeCount =
                commentLikeRepository.countByComment_Id(commentId);

        // 로그인 없이 CSRF만 발급받는다.
        CsrfSession anonymous = fetchCsrf(null);

        change(method, anonymous)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isEqualTo(beforeCount);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE"})
    void 같은_회원이라도_다른_세션의_CSRF_토큰은_사용할_수_없다(
            String method
    ) throws Exception {
        CsrfSession first = login(me.getEmail());
        CsrfSession second = login(me.getEmail());

        assertThat(first.session().getId())
                .isNotEqualTo(second.session().getId());

        if (method.equals("DELETE")) {
            assertSuccess(change("PUT", first), true, 1);
        }

        long beforeCount =
                commentLikeRepository.countByComment_Id(commentId);

        // 첫 번째 세션 + 두 번째 세션의 토큰
        mockMvc.perform(
                        request(HttpMethod.valueOf(method), likeUrl())
                                .session(first.session())
                                .header(second.headerName(), second.token())
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isEqualTo(beforeCount);

        // 올바른 세션·토큰 조합은 정상 처리된다.
        boolean liked = method.equals("PUT");

        assertSuccess(
                change(method, first),
                liked,
                liked ? 1 : 0
        );

        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isEqualTo(liked ? 1L : 0L);
    }

    @ParameterizedTest
    @CsvSource({
            "PUT, STATUS",
            "DELETE, STATUS",
            "PUT, VERSION",
            "DELETE, VERSION"
    })
    void 로그인_후_회원_상태나_인증_버전이_바뀌면_요청을_차단한다(
            String method,
            String changedValue
    ) throws Exception {
        CsrfSession client = login(me.getEmail());

        if (method.equals("DELETE")) {
            assertSuccess(change("PUT", client), true, 1);
        }

        long beforeCount =
                commentLikeRepository.countByComment_Id(commentId);

        int updatedRows;

        if (changedValue.equals("STATUS")) {
            updatedRows = jdbcTemplate.update("""
                    UPDATE members
                    SET status = 'PENDING'
                    WHERE id = ?
                    """, me.getId());
        } else {
            updatedRows = jdbcTemplate.update("""
                    UPDATE members
                    SET credential_version = credential_version + 1
                    WHERE id = ?
                    """, me.getId());
        }

        assertThat(updatedRows).isEqualTo(1);

        // CSRF는 정상이고, 로그인 정보가 더 이상 유효하지 않은 상황이다.
        change(method, client)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        assertThat(client.session().isInvalid()).isTrue();

        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isEqualTo(beforeCount);
    }

    @ParameterizedTest
    @CsvSource({
            "PUT, COMMENT_DELETED, COMMENT_NOT_FOUND",
            "DELETE, COMMENT_DELETED, COMMENT_NOT_FOUND",
            "PUT, POST_DELETED, POST_NOT_FOUND",
            "DELETE, POST_DELETED, POST_NOT_FOUND",
            "PUT, WRONG_POST, COMMENT_NOT_FOUND",
            "DELETE, WRONG_POST, COMMENT_NOT_FOUND"
    })
    void 접근할_수_없는_댓글은_404이며_기존_좋아요를_변경하지_않는다(
            String method,
            String targetState,
            String expectedCode
    ) throws Exception {
        CsrfSession client = login(other.getEmail());

        if (method.equals("DELETE")) {
            assertSuccess(change("PUT", client), true, 1);
        }

        long beforeCount =
                commentLikeRepository.countByComment_Id(commentId);

        Long targetPostId = postId;

        switch (targetState) {
            case "COMMENT_DELETED" ->
                    commentDeleteService.delete(
                            me.getId(), postId, commentId
                    );

            case "POST_DELETED" ->
                    postDeleteService.delete(me.getId(), postId);

            case "WRONG_POST" ->
                    targetPostId = savePost(me).getId();

            default ->
                    throw new IllegalArgumentException(targetState);
        }

        String targetUrl =
                "/api/v1/community/posts/" + targetPostId
                        + "/comments/" + commentId + "/like";

        mockMvc.perform(
                        request(HttpMethod.valueOf(method), targetUrl)
                                .session(client.session())
                                .header(client.headerName(), client.token())
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isEqualTo(beforeCount);

        // 対象の404でログインセッションまで無効化してはいけない。
        assertThat(client.session().isInvalid()).isFalse();

        mockMvc.perform(
                        get("/api/v1/auth/me")
                                .session(client.session())
                )
                .andExpect(status().isOk());
    }

    @Test
    void 댓글_목록은_전체_좋아요_수와_현재_회원의_좋아요_상태를_반환한다()
            throws Exception {
        CsrfSession myClient = login(me.getEmail());
        CsrfSession otherClient = login(other.getEmail());

        String url =
                "/api/v1/community/posts/" + postId + "/comments";

        // 좋아요가 없는 댓글의 기본값
        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].commentId").value(commentId))
                .andExpect(jsonPath("$.items[0].likeCount").value(0))
                .andExpect(jsonPath("$.items[0].liked").value(false))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        assertSuccess(change("PUT", myClient), true, 1);

        // 비로그인은 전체 개수만 확인한다.
        // viewerId를 보내도 다른 회원의 상태를 선택할 수 없다.
        mockMvc.perform(
                        get(url)
                                .param("viewerId", me.getId().toString())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].likeCount").value(1))
                .andExpect(jsonPath("$.items[0].liked").value(false));

        // 좋아요를 등록한 회원
        mockMvc.perform(
                        get(url)
                                .session(myClient.session())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].likeCount").value(1))
                .andExpect(jsonPath("$.items[0].liked").value(true))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        // 다른 회원은 본인의 상태를 받아야 한다.
        mockMvc.perform(
                        get(url)
                                .session(otherClient.session())
                                .param("viewerId", me.getId().toString())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].likeCount").value(1))
                .andExpect(jsonPath("$.items[0].liked").value(false));

        // 두 회원이 모두 등록한 뒤, 내 좋아요만 취소한다.
        assertSuccess(change("PUT", otherClient), true, 2);
        assertSuccess(change("DELETE", myClient), false, 1);

        mockMvc.perform(
                        get(url)
                                .session(myClient.session())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].likeCount").value(1))
                .andExpect(jsonPath("$.items[0].liked").value(false));

        mockMvc.perform(
                        get(url)
                                .session(otherClient.session())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].likeCount").value(1))
                .andExpect(jsonPath("$.items[0].liked").value(true));
    }

    @Test
    void 커서_후속_조회도_댓글별_좋아요_정보를_정확하게_반환한다()
            throws Exception {
        Post postEntity = postRepository.findById(postId)
                .orElseThrow();

        Long secondCommentId = commentRepository.saveAndFlush(
                Comment.create(postEntity, me, "두 번째 댓글")
        ).getId();

        Long thirdCommentId = commentRepository.saveAndFlush(
                Comment.create(postEntity, other, "세 번째 댓글")
        ).getId();

        CsrfSession myClient = login(me.getEmail());
        CsrfSession otherClient = login(other.getEmail());

        String collectionUrl =
                "/api/v1/community/posts/" + postId + "/comments";

        // 두 번째 댓글에는 내가 좋아요 등록
        mockMvc.perform(
                        request(
                                HttpMethod.PUT,
                                collectionUrl + "/" + secondCommentId + "/like"
                        )
                                .session(myClient.session())
                                .header(myClient.headerName(), myClient.token())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commentId").value(secondCommentId))
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));

        // 세 번째 댓글에는 다른 회원이 좋아요 등록
        mockMvc.perform(
                        request(
                                HttpMethod.PUT,
                                collectionUrl + "/" + thirdCommentId + "/like"
                        )
                                .session(otherClient.session())
                                .header(otherClient.headerName(), otherClient.token())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commentId").value(thirdCommentId))
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));

        // 첫 페이지: 첫 번째와 두 번째 댓글
        MvcResult firstPage = mockMvc.perform(
                        get(collectionUrl)
                                .session(myClient.session())
                                .param("size", "2")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].commentId").value(commentId))
                .andExpect(jsonPath("$.items[0].likeCount").value(0))
                .andExpect(jsonPath("$.items[0].liked").value(false))
                .andExpect(jsonPath("$.items[1].commentId").value(secondCommentId))
                .andExpect(jsonPath("$.items[1].likeCount").value(1))
                .andExpect(jsonPath("$.items[1].liked").value(true))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor.commentId").value(secondCommentId))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andReturn();

        // 서버가 실제로 반환한 커서를 그대로 사용한다.
        JsonNode body = objectMapper.readTree(
                firstPage.getResponse().getContentAsString()
        );

        String afterCreatedAt =
                body.path("nextCursor").path("createdAt").asString();

        String afterId =
                body.path("nextCursor").path("commentId").asString();

        // 다음 페이지: 세 번째 댓글
        mockMvc.perform(
                        get(collectionUrl)
                                .session(myClient.session())
                                .param("size", "2")
                                .param("afterCreatedAt", afterCreatedAt)
                                .param("afterId", afterId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].commentId").value(thirdCommentId))
                .andExpect(jsonPath("$.items[0].likeCount").value(1))
                .andExpect(jsonPath("$.items[0].liked").value(false))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").hasJsonPath())
                .andExpect(jsonPath("$.nextCursor").value(
                        org.hamcrest.Matchers.nullValue()
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"STATUS", "VERSION"})
    void 인증이_무효화된_세션도_댓글을_비로그인_상태로_조회한다(
            String changedValue
    ) throws Exception {
        CsrfSession client = login(me.getEmail());

        assertSuccess(change("PUT", client), true, 1);

        int updatedRows;

        if (changedValue.equals("STATUS")) {
            updatedRows = jdbcTemplate.update("""
                UPDATE members
                SET status = 'PENDING'
                WHERE id = ?
                """, me.getId());
        } else {
            updatedRows = jdbcTemplate.update("""
                UPDATE members
                SET credential_version = credential_version + 1
                WHERE id = ?
                """, me.getId());
        }

        assertThat(updatedRows).isEqualTo(1);

        String url =
                "/api/v1/community/posts/" + postId + "/comments";

        mockMvc.perform(
                        get(url)
                                .session(client.session())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].commentId").value(commentId))
                .andExpect(jsonPath("$.items[0].likeCount").value(1))
                .andExpect(jsonPath("$.items[0].liked").value(false))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        assertThat(client.session().isInvalid()).isTrue();

        // 인증 무효화 자체가 기존 좋아요 행을 삭제하지는 않는다.
        assertThat(
                commentLikeRepository.findByComment_IdAndMember_Id(
                        commentId, me.getId()
                )
        ).isPresent();

        assertThat(commentLikeRepository.countByComment_Id(commentId))
                .isEqualTo(1L);
    }

    @Test
    void 비로그인으로_베스트_댓글을_조회하면_좋아요_10개인_댓글은_포함하고_9개인_댓글은_제외한다()
            throws Exception {
        Post post = postRepository.findById(postId)
                .orElseThrow();

        Comment bestComment = commentRepository.findById(commentId)
                .orElseThrow();

        Comment belowThreshold = commentRepository.saveAndFlush(
                Comment.create(post, other, "좋아요가 9개인 댓글")
        );

        // 두 댓글 모두 좋아요 9개에서 시작한다.
        String passwordHash = passwordEncoder.encode(PASSWORD);

        for (int i = 1; i <= 9; i++) {
            Member reader = saveActiveMember(
                    "best-reader-" + i + "@example.com",
                    "베스트독자" + i,
                    passwordHash
            );

            commentLikeRepository.save(CommentLike.create(bestComment, reader));
            commentLikeRepository.save(CommentLike.create(belowThreshold, reader));
        }

        // 기존 change() 헬퍼는 setUp()에서 만든 commentId에 좋아요를 등록한다.
        CsrfSession client = login(other.getEmail());
        assertSuccess(change("PUT", client), true, 10);

        // 조회 요청에는 로그인 세션을 전달하지 않는다.
        mockMvc.perform(
                        get("/api/v1/community/posts/{postId}/comments/best", postId)
                )
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].commentId").value(commentId))
                .andExpect(jsonPath("$.items[0].likeCount").value(10))
                .andExpect(jsonPath("$.items[0].liked").value(false));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 15})
    void 비로그인으로_베스트를_조회하면_원댓글과_인기_대댓글을_한_항목으로_반환한다(
            int parentLikeCount
    ) throws Exception {
        Post post = savePost(me);

        Comment parent = commentRepository.saveAndFlush(
                Comment.create(post, me, "첫 상담 때 무엇을 준비하셨나요?")
        );
        Comment reply = commentRepository.saveAndFlush(
                Comment.createReply(parent, other, "불편했던 상황을 메모해 갔어요.")
        );

        int replyLikeCount = 10;
        int readerCount = Math.max(parentLikeCount, replyLikeCount);
        String passwordHash = passwordEncoder.encode(PASSWORD);

        for (int i = 1; i <= readerCount; i++) {
            Member reader = saveActiveMember(
                    "best-pair-reader-" + i + "@example.com",
                    "베스트독자" + i,
                    passwordHash
            );

            if (i <= parentLikeCount) {
                commentLikeRepository.save(CommentLike.create(parent, reader));
            }

            if (i <= replyLikeCount) {
                commentLikeRepository.save(CommentLike.create(reply, reader));
            }
        }

        mockMvc.perform(
                        get("/api/v1/community/posts/{postId}/comments/best", post.getId())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].commentId").value(parent.getId()))
                .andExpect(jsonPath("$.items[0].content").value(parent.getContent()))
                .andExpect(jsonPath("$.items[0].likeCount").value(parentLikeCount))
                .andExpect(jsonPath("$.items[0].bestReply.commentId").value(reply.getId()))
                .andExpect(jsonPath("$.items[0].bestReply.content").value(reply.getContent()))
                .andExpect(jsonPath("$.items[0].bestReply.likeCount").value(replyLikeCount));
    }

    private CsrfSession login(String email) throws Exception {
        CsrfSession beforeLogin = fetchCsrf(null);

        MvcResult loginResult = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .session(beforeLogin.session())
                                .header(
                                        beforeLogin.headerName(),
                                        beforeLogin.token()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        Map.of(
                                                "email", email,
                                                "password", PASSWORD
                                        )
                                ))
                )
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession authenticatedSession =
                (MockHttpSession) loginResult
                        .getRequest()
                        .getSession(false);

        assertThat(authenticatedSession).isNotNull();

        // 로그인 시 기존 CSRF 토큰이 폐기되므로 다시 발급받는다.
        return fetchCsrf(authenticatedSession);
    }

    private CsrfSession fetchCsrf(MockHttpSession session)
            throws Exception {
        MockHttpServletRequestBuilder csrfRequest =
                get("/api/v1/auth/csrf");

        if (session != null) {
            csrfRequest.session(session);
        }

        MvcResult result = mockMvc.perform(csrfRequest)
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession actualSession =
                (MockHttpSession) result
                        .getRequest()
                        .getSession(false);

        assertThat(actualSession).isNotNull();

        JsonNode body = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );

        String headerName = body.path("headerName").asString();
        String token = body.path("token").asString();

        assertThat(headerName).isEqualTo("X-CSRF-TOKEN");
        assertThat(token).isNotBlank();

        return new CsrfSession(actualSession, headerName, token);
    }

    private ResultActions change(
            String method,
            CsrfSession client
    ) throws Exception {
        return mockMvc.perform(
                request(HttpMethod.valueOf(method), likeUrl())
                        .session(client.session())
                        .header(client.headerName(), client.token())
        );
    }

    private void assertSuccess(
            ResultActions result,
            boolean liked,
            int likeCount
    ) throws Exception {
        result
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.postId").value(postId))
                .andExpect(jsonPath("$.commentId").value(commentId))
                .andExpect(jsonPath("$.liked").value(liked))
                .andExpect(jsonPath("$.likeCount").value(likeCount))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));
    }

    private Member saveActiveMember(
            String email,
            String nickname,
            String passwordHash
    ) {
        Member member = Member.createPending(
                email,
                passwordHash,
                nickname
        );

        member.activeAfterEmailVerification();

        return memberRepository.saveAndFlush(member);
    }

    private Post savePost(Member author) {
        return postRepository.saveAndFlush(
                Post.create(
                        author,
                        PostType.GENERAL,
                        PostTopic.DAILY,
                        "댓글 좋아요 세션 테스트",
                        "통합 테스트 본문"
                )
        );
    }

    private String likeUrl() {
        return "/api/v1/community/posts/" + postId
                + "/comments/" + commentId + "/like";
    }

    private record CsrfSession(
            MockHttpSession session,
            String headerName,
            String token
    ) {
    }
}