package io.github.takgeun.iyum.community.post.api;

import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostLikeRepository;
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
class PostLikeSessionIntegrationTest {

    private static final String PASSWORD = "LikeSession123!";

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
    PostLikeRepository postLikeRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Member me;
    private Member other;
    private Long postId;

    @BeforeEach
    void setUp() {
        // 이 클래스의 테스트 전용 PostgreSQL을 정리한다.
        postLikeRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        String passwordHash = passwordEncoder.encode(PASSWORD);

        me = saveActiveMember(
                "like-session-me@example.com",
                "좋아요회원",
                passwordHash
        );

        other = saveActiveMember(
                "like-session-other@example.com",
                "다른좋아요회원",
                passwordHash
        );

        Post post = postRepository.saveAndFlush(
                Post.create(
                        me,
                        PostType.GENERAL,
                        PostTopic.DAILY,
                        "좋아요 세션 테스트",
                        "통합 테스트 본문"
                )
        );

        postId = post.getId();
    }

    @Test
    void 실제_로그인_세션으로_등록과_취소를_반복할_수_있다()
            throws Exception {
        CsrfSession client = login(me.getEmail());

        // 최초 등록
        assertSuccess(change("PUT", client), true, 1);

        Long firstLikeId = postLikeRepository
                .findByPost_IdAndMember_Id(postId, me.getId())
                .orElseThrow()
                .getId();

        // 중복 등록: 기존 행 유지
        assertSuccess(change("PUT", client), true, 1);

        assertThat(
                postLikeRepository
                        .findByPost_IdAndMember_Id(postId, me.getId())
                        .orElseThrow()
                        .getId()
        ).isEqualTo(firstLikeId);

        // 최초 취소와 중복 취소
        assertSuccess(change("DELETE", client), false, 0);
        assertSuccess(change("DELETE", client), false, 0);

        assertThat(postLikeRepository.countByPost_Id(postId))
                .isZero();

        // 취소 후 다시 등록할 수도 있다.
        assertSuccess(change("PUT", client), true, 1);

        assertThat(postLikeRepository.countByPost_Id(postId))
                .isEqualTo(1L);

        // 좋아요 요청 후에도 로그인 세션은 유지된다.
        mockMvc.perform(
                        get("/api/v1/auth/me")
                                .session(client.session())
                )
                .andExpect(status().isOk());

        assertThat(client.session().isInvalid()).isFalse();
    }

    @Test
    void 각_세션은_자신의_좋아요만_등록하거나_취소한다()
            throws Exception {
        CsrfSession myClient = login(me.getEmail());
        CsrfSession otherClient = login(other.getEmail());

        assertThat(myClient.session().getId())
                .isNotEqualTo(otherClient.session().getId());

        assertSuccess(change("PUT", myClient), true, 1);
        assertSuccess(change("PUT", otherClient), true, 2);

        // 다른 회원 ID를 파라미터로 보내도
        // 현재 로그인한 회원의 좋아요만 취소해야 한다.
        ResultActions cancellation = mockMvc.perform(
                request(HttpMethod.DELETE, likeUrl())
                        .session(myClient.session())
                        .header(myClient.headerName(), myClient.token())
                        .param("memberId", other.getId().toString())
        );

        assertSuccess(cancellation, false, 1);

        assertThat(
                postLikeRepository.findByPost_IdAndMember_Id(
                        postId,
                        me.getId()
                )
        ).isEmpty();

        assertThat(
                postLikeRepository.findByPost_IdAndMember_Id(
                        postId,
                        other.getId()
                )
        ).isPresent();

        assertThat(postLikeRepository.countByPost_Id(postId))
                .isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE"})
    void 비로그인_세션은_실제_CSRF_토큰이_있어도_거절된다(
            String method
    ) throws Exception {
        // DELETE가 잘못 허용된다면 실제 데이터가 사라지도록 준비한다.
        if (method.equals("DELETE")) {
            CsrfSession owner = login(me.getEmail());
            assertSuccess(change("PUT", owner), true, 1);
        }

        long beforeCount =
                postLikeRepository.countByPost_Id(postId);

        // 로그인하지 않고 CSRF API만 호출한 세션
        CsrfSession anonymous = fetchCsrf(null);

        change(method, anonymous)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        assertThat(postLikeRepository.countByPost_Id(postId))
                .isEqualTo(beforeCount);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE"})
    void 같은_회원이라도_다른_세션의_CSRF_토큰은_사용할_수_없다(
            String method
    ) throws Exception {
        CsrfSession first = login(me.getEmail());
        CsrfSession second = login(me.getEmail());

        if (method.equals("DELETE")) {
            assertSuccess(change("PUT", first), true, 1);
        }

        long beforeCount =
                postLikeRepository.countByPost_Id(postId);

        // 첫 번째 세션에 두 번째 세션의 토큰을 섞어서 전송한다.
        mockMvc.perform(
                        request(HttpMethod.valueOf(method), likeUrl())
                                .session(first.session())
                                .header(second.headerName(), second.token())
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        assertThat(postLikeRepository.countByPost_Id(postId))
                .isEqualTo(beforeCount);

        // 첫 번째 세션의 올바른 토큰으로는 정상 처리된다.
        boolean liked = method.equals("PUT");

        assertSuccess(
                change(method, first),
                liked,
                liked ? 1 : 0
        );
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
                postLikeRepository.countByPost_Id(postId);

        int updatedRows;

        if (changedValue.equals("STATUS")) {
            updatedRows = jdbcTemplate.update("""
                    UPDATE members
                    SET status = 'PENDING'
                    WHERE id = ?
                    """,
                    me.getId()
            );
        } else {
            updatedRows = jdbcTemplate.update("""
                    UPDATE members
                    SET credential_version = credential_version + 1
                    WHERE id = ?
                    """,
                    me.getId()
            );
        }

        assertThat(updatedRows).isEqualTo(1);

        // 로그인 당시 발급받은 유효한 CSRF를 사용한다.
        // 따라서 CSRF 오류가 아닌 인증 무효화 결과를 확인한다.
        change(method, client)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        assertThat(client.session().isInvalid()).isTrue();

        assertThat(postLikeRepository.countByPost_Id(postId))
                .isEqualTo(beforeCount);
    }

    private CsrfSession login(String email) throws Exception {
        // 로그인 전 CSRF 토큰과 세션
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

        // 로그인 시 CSRF 토큰이 갱신되므로 로그인 후 다시 발급받는다.
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

    private String likeUrl() {
        return "/api/v1/community/posts/" + postId + "/like";
    }

    private record CsrfSession(
            MockHttpSession session,
            String headerName,
            String token
    ) {
    }
}