package io.github.takgeun.iyum.community.comment.api;

import com.jayway.jsonpath.JsonPath;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.community.comment.api.dto.CommentCreateRequest;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 테스트 실행 환경 설정
// Spring 애플리케이션 컨텍스트를 띄워서 컨트롤러·서비스·리포지토리 등 실제 빈을 사용하게 한다.
@SpringBootTest(properties = {
        "spring.config.import=",    // 기존 import 설정을 비워 로컬 .env 등의 영향을 줄임
        "spring.docker.compose.enabled=false",  // 테스트 중 개발용 Docker Compose 자동 실행 방지
        "spring.jpa.hibernate.ddl-auto=validate",   // JPA는 테이블을 생성하지 않고 엔티티와 일치하는지 검사
        "spring.jpa.open-in-view=false",    // 웹 요청 전체에 영속성 컨텍스트를 유지하지 않음
        "spring.flyway.enabled=true"    // 테스트 DB에도 마이그레이션 SQL 적용
})
// MockMVC를 준비하고 보안 필터도 요청 처리에 포함
// MockMvc는 실제 포트에 HTTP 요청을 보내는 대신, 테스트 안에서 Spring MVC의 요청 처리 과정을 실행하는 도구
@AutoConfigureMockMvc(addFilters = true)
@Testcontainers // 아래에 선언한 테스트용 컨테이너의 시작과 종료를 관리하는 데에 사용
class CommentCreateApiTest {

    private static final String COMMENTS_URL =
            "/api/v1/community/posts/{postId}/comments";

    // 테스트용 PostgreSQL 준비
    @Container  // TestContainers가 관리할 컨테이너라는 표시
    // static : 이 테스트 클래스의 여러 테스트가 같은 컨테이너를 공유하게 하기 위함
    // final : POSTGRES 변수에 다른 컨테이너 객체를 다시 대입하지 못하게 함
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine");

    // Spring이 테스트 DB에 연결하도록 설정
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

    // 테스트에서 사용할 도구와 빈을 주입 받는다.
    @Autowired
    MockMvc mockMvc;    // 댓글 작성 요청 실행과 HTTP 응답 검증

    @Autowired
    ObjectMapper objectMapper;  // 요청 DTO를 JSON 문자열로 변환

    @Autowired
    CommentRepository commentRepository;

    @Autowired
    PostRepository postRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;  // SQL로 DB에 저장된 값을 직접 확인

    private Long postId;
    private Member commentAuthor;

    @BeforeEach
    void setUp() {
        // 테스트 전용 컨테이너의 데이터만 초기화한다.
        commentRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        Member postAuthor = saveActiveMember(
                "post-author@example.com",
                "게시글작성자"
        );

        commentAuthor = saveActiveMember(
                "comment-author@example.com",
                "댓글작성자"
        );

        postId = postRepository.saveAndFlush(
                Post.create(
                        postAuthor,
                        PostType.QUESTION,
                        PostTopic.HEARING_AID,
                        "댓글 작성 대상",
                        "게시글 본문"
                )
        ).getId();
    }

    @Test
    void 로그인한_회원이_댓글을_작성하면_201과_ID를_반환한다()
            throws Exception {

        String content = "  첫 번째 댓글\n다음 줄  ";

        MvcResult result = mockMvc.perform(
                // authenticatedPost : commentAuthor의 정보로 MemberPrincipal을 만든다.
                        authenticatedPost(
                                postId,
                                jsonBody(content)
                        ).with(csrf().asHeader())   // 유효한 테스트용 CSRF 토큰을 헤더로 전달
                )
                .andExpect(status().isCreated())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        header().string(
                                HttpHeaders.CACHE_CONTROL,
                                "no-store"
                        )
                )
                .andExpect(jsonPath("$.commentId").isNumber())
                .andReturn();

        // 응답의 댓글 ID와 실제 저장 개수 확인
        String responseBody = result.getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        Number commentId = JsonPath.read(
                responseBody,
                "$.commentId"
        );

        assertThat(commentId.longValue()).isPositive();
        assertThat(commentRepository.count()).isEqualTo(1L);

        // 응답 ID로 DB를 직접 조회해 저장 내용 확인
        Map<String, Object> saved = jdbcTemplate.queryForMap("""
                SELECT post_id, author_id, content,
                       created_at, updated_at, deleted_at
                FROM community_comments
                WHERE id = ?
                """,
                commentId.longValue()
        );

        assertThat(saved.get("post_id")).isEqualTo(postId);

        // 게시글 작성자가 아닌 로그인한 댓글 작성자로 저장된다.
        assertThat(saved.get("author_id"))
                .isEqualTo(commentAuthor.getId());

        assertThat(saved.get("content")).isEqualTo(content);
        assertThat(saved.get("created_at")).isNotNull();
        assertThat(saved.get("updated_at")).isNotNull();
        assertThat(saved.get("deleted_at")).isNull();
    }

    @Test
    void 비로그인은_유효한_CSRF가_있어도_401이다()
            throws Exception {

        assertRejected(
                post(COMMENTS_URL, postId)
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody("정상 댓글")),
                401,
                "AUTHENTICATION_REQUIRED"
        );
    }

    @Test
    void 로그인했어도_CSRF가_없으면_403이다()
            throws Exception {

        assertRejected(
                authenticatedPost(
                        postId,
                        jsonBody("정상 댓글")
                ),
                403,
                "CSRF_TOKEN_INVALID"
        );
    }

    @Test
    void 잘못된_CSRF_토큰이면_403이다()
            throws Exception {

        assertRejected(
                authenticatedPost(
                        postId,
                        jsonBody("정상 댓글")
                ).with(
                        csrf().asHeader().useInvalidToken()
                ),
                403,
                "CSRF_TOKEN_INVALID"
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void 잘못된_본문은_content_필드_오류와_400을_반환한다(
            String content
    ) throws Exception {

        mockMvc.perform(
                        authenticatedPost(
                                postId,
                                jsonBody(content)
                        ).with(csrf().asHeader())
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_FAILED")
                )
                .andExpect(
                        jsonPath("$.fieldErrors.length()").value(1)
                )
                .andExpect(
                        jsonPath("$.fieldErrors[0].field")
                                .value("content")
                )
                .andExpect(
                        jsonPath("$.fieldErrors[0].message")
                                .value("댓글 내용은 필수입니다.")
                );

        assertThat(commentRepository.count()).isZero();
    }

    @Test
    void 최대_길이를_초과한_본문은_400이다()
            throws Exception {

        mockMvc.perform(
                        authenticatedPost(
                                postId,
                                jsonBody("가".repeat(2_001))
                        ).with(csrf().asHeader())
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_FAILED")
                )
                .andExpect(
                        jsonPath("$.fieldErrors.length()").value(1)
                )
                .andExpect(
                        jsonPath("$.fieldErrors[0].field")
                                .value("content")
                )
                .andExpect(
                        jsonPath("$.fieldErrors[0].message")
                                .value("댓글 내용은 2,000자 이하여야 합니다.")
                );

        assertThat(commentRepository.count()).isZero();
    }

    @Test
    void content_필드가_누락되어도_검증_오류로_처리한다()
            throws Exception {

        mockMvc.perform(
                        authenticatedPost(postId, "{}")
                                .with(csrf().asHeader())
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_FAILED")
                )
                .andExpect(
                        jsonPath("$.fieldErrors[0].field")
                                .value("content")
                );

        assertThat(commentRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{broken", "", "null"})
    void JSON이_잘못되거나_요청_본문이_없으면_400이다(
            String body
    ) throws Exception {

        assertRejected(
                authenticatedPost(postId, body)
                        .with(csrf().asHeader()),
                400,
                "INVALID_REQUEST_BODY"
        );
    }

    @Test
    void 로그인_정보가_있어도_DB_회원이_없으면_401이다()
            throws Exception {

        MockHttpServletRequestBuilder request =
                authenticatedPost(
                        postId,
                        jsonBody("정상 댓글")
                ).with(csrf().asHeader());

        // 로그인 정보는 요청에 남아 있지만 DB 회원은 없는 상황
        jdbcTemplate.update(
                "DELETE FROM members WHERE id = ?",
                commentAuthor.getId()
        );

        assertRejected(
                request,
                401,
                "AUTHENTICATION_REQUIRED"
        );
    }

    @Test
    void 로그인_정보가_ACTIVE여도_DB가_PENDING이면_401이다()
            throws Exception {

        MockHttpServletRequestBuilder request =
                authenticatedPost(
                        postId,
                        jsonBody("정상 댓글")
                ).with(csrf().asHeader());

        // 인증 정보 생성 후 DB의 현재 상태를 변경한다.
        jdbcTemplate.update("""
                UPDATE members
                SET status = 'PENDING'
                WHERE id = ?
                """,
                commentAuthor.getId()
        );

        assertRejected(
                request,
                401,
                "AUTHENTICATION_REQUIRED"
        );
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, 0L, Long.MAX_VALUE})
    void 유효하지_않거나_없는_게시글은_404이다(
            long invalidPostId
    ) throws Exception {

        assertRejected(
                authenticatedPost(
                        invalidPostId,
                        jsonBody("정상 댓글")
                ).with(csrf().asHeader()),
                404,
                "POST_NOT_FOUND"
        );
    }

    @Test
    void 삭제된_게시글에는_작성할_수_없다()
            throws Exception {

        jdbcTemplate.update("""
                UPDATE community_posts
                SET deleted_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                postId
        );

        assertRejected(
                authenticatedPost(
                        postId,
                        jsonBody("정상 댓글")
                ).with(csrf().asHeader()),
                404,
                "POST_NOT_FOUND"
        );
    }

    @Test
    void 게시글_ID가_숫자가_아니면_400이다()
            throws Exception {

        assertRejected(
                authenticatedPost(
                        "abc",
                        jsonBody("정상 댓글")
                ).with(csrf().asHeader()),
                400,
                "INVALID_REQUEST_PARAMETER"
        );
    }

    // 회원 생성 -> ACTIVE 상태로 변경 -> DB 저장
    private Member saveActiveMember(
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

    private String jsonBody(String content) {
        return objectMapper.writeValueAsString(
                new CommentCreateRequest(content)
        );
    }

    private MockHttpServletRequestBuilder authenticatedPost(
            Object targetPostId,
            String body
    ) {
        MemberPrincipal principal = new MemberPrincipal(
                commentAuthor.getId(),
                commentAuthor.getEmail(),
                commentAuthor.getPasswordHash(),
                commentAuthor.getNickname(),
                commentAuthor.getStatus(),
                commentAuthor.getRole()
        );

        return post(COMMENTS_URL, targetPostId)
                .with(user(principal))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private void assertRejected(
            MockHttpServletRequestBuilder request,
            int expectedStatus,
            String expectedCode
    ) throws Exception {

        mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.code").value(expectedCode)
                );

        assertThat(commentRepository.count()).isZero();
    }
}