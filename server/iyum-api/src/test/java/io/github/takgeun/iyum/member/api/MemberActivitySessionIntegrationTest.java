package io.github.takgeun.iyum.member.api;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true"
})
@AutoConfigureMockMvc(addFilters = true)
@Testcontainers
class MemberActivitySessionIntegrationTest {

    private static final String SUMMARY_URL =
            "/api/v1/members/me/activity-summary";

    private static final String PASSWORD =
            "ActivityTest123!";

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
    JdbcTemplate jdbcTemplate;

    private Member me;
    private Member other;

    @BeforeEach
    void setUp() {
        // 테스트 전용 컨테이너의 데이터만 초기화한다.
        commentRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        // 실제 로그인에서 비밀번호 검증을 통과할 수 있도록
        // 애플리케이션의 PasswordEncoder로 암호화한다.
        String passwordHash = passwordEncoder.encode(PASSWORD);

        me = saveActiveMember(
                "activity-me@example.com",
                "내활동회원",
                passwordHash
        );

        other = saveActiveMember(
                "activity-other@example.com",
                "다른활동회원",
                passwordHash
        );
    }

    @Test
    void 서로_다른_로그인_세션은_각_회원의_활동만_조회한다()
            throws Exception {
        Post myPost = savePost(me, "내 첫 번째 게시글");
        savePost(me, "내 두 번째 게시글");
        Post otherPost = savePost(other, "다른 회원 게시글");

        saveComment(myPost, me, "내 게시글에 작성한 내 댓글");
        saveComment(otherPost, me, "다른 게시글에 작성한 내 댓글");
        saveComment(otherPost, me, "내 추가 댓글");

        saveComment(myPost, other, "다른 회원의 댓글");

        MockHttpSession mySession = login(me.getEmail());
        MockHttpSession otherSession = login(other.getEmail());

        assertThat(mySession.getId())
                .isNotEqualTo(otherSession.getId());

        assertSummary(mySession, 2L, 3L);
        assertSummary(otherSession, 1L, 1L);

        // 다른 회원의 요청 후에도 내 세션의 조회 대상은 유지된다.
        assertSummary(mySession, 2L, 3L);
    }

    @Test
    void 같은_세션으로_다시_조회하면_삭제된_활동이_집계에서_제외된다()
            throws Exception {
        Post myPost = savePost(me, "삭제 확인용 게시글");

        Comment firstComment = saveComment(
                myPost,
                me,
                "먼저 삭제할 댓글"
        );

        saveComment(
                myPost,
                me,
                "게시글 삭제 시 집계에서 제외할 댓글"
        );

        MockHttpSession session = login(me.getEmail());

        assertSummary(session, 1L, 2L);

        // 댓글 하나를 소프트 삭제한다.
        firstComment.delete(Instant.now());
        commentRepository.saveAndFlush(firstComment);

        assertSummary(session, 1L, 1L);

        // 나머지 댓글은 직접 삭제하지 않고 원본 게시글만 삭제한다.
        myPost.delete(Instant.now());
        postRepository.saveAndFlush(myPost);

        assertSummary(session, 0L, 0L);
    }

    @Test
    void 로그인_후_회원이_비활성_상태가_되면_조회할_수_없다()
            throws Exception {
        MockHttpSession session = login(me.getEmail());

        assertSummary(session, 0L, 0L);

        // 세션에는 로그인 당시 ACTIVE 정보가 남아 있지만,
        // DB의 현재 상태는 PENDING으로 변경한다.
        int updatedRows = jdbcTemplate.update("""
                UPDATE members
                SET status = 'PENDING'
                WHERE id = ?
                """,
                me.getId()
        );

        assertThat(updatedRows).isEqualTo(1);

        mockMvc.perform(get(SUMMARY_URL)
                        .session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void 로그인_후_회원이_사라지면_남은_세션으로_조회할_수_없다()
            throws Exception {
        MockHttpSession session = login(me.getEmail());

        assertSummary(session, 0L, 0L);

        // 이 테스트의 회원에는 게시글·댓글·약관 등의 참조 데이터가 없다.
        // 회원을 찾을 수 없는 상황을 테스트 DB에서 재현한다.
        memberRepository.deleteById(me.getId());

        assertThat(memberRepository.existsById(me.getId())).isFalse();

        mockMvc.perform(get(SUMMARY_URL)
                        .session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message")
                        .value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    private MockHttpSession login(String email) throws Exception {
        // 1. 실제 CSRF API를 호출해 토큰과 세션을 받는다.
        MvcResult csrfResult = mockMvc.perform(
                        get("/api/v1/auth/csrf")
                )
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession csrfSession =
                (MockHttpSession) csrfResult
                        .getRequest()
                        .getSession(false);

        assertThat(csrfSession).isNotNull();

        JsonNode csrfBody = objectMapper.readTree(
                csrfResult.getResponse().getContentAsString()
        );

        String headerName = csrfBody
                .path("headerName")
                .asString();

        String token = csrfBody
                .path("token")
                .asString();

        assertThat(headerName).isEqualTo("X-CSRF-TOKEN");
        assertThat(token).isNotBlank();

        // 2. 같은 세션과 실제 발급받은 토큰으로 로그인한다.
        MvcResult loginResult = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .session(csrfSession)
                                .header(headerName, token)
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

        // 3. 로그인 처리 후의 세션을 이후 요청에 사용한다.
        MockHttpSession authenticatedSession =
                (MockHttpSession) loginResult
                        .getRequest()
                        .getSession(false);

        assertThat(authenticatedSession).isNotNull();

        return authenticatedSession;
    }

    private void assertSummary(
            MockHttpSession session,
            long expectedPostCount,
            long expectedCommentCount
    ) throws Exception {
        // 조회 GET에는 CSRF 토큰을 전달하지 않는다.
        mockMvc.perform(get(SUMMARY_URL)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(jsonPath("$.postCount")
                        .value(is(expectedPostCount), Long.class))
                .andExpect(jsonPath("$.commentCount")
                        .value(is(expectedCommentCount), Long.class));
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

    private Post savePost(Member author, String title) {
        return postRepository.saveAndFlush(
                Post.create(
                        author,
                        PostType.QUESTION,
                        PostTopic.DAILY,
                        title,
                        "활동 요약 통합 테스트용 본문"
                )
        );
    }

    private Comment saveComment(
            Post post,
            Member author,
            String content
    ) {
        return commentRepository.saveAndFlush(
                Comment.create(post, author, content)
        );
    }
}