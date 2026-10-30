package io.github.takgeun.iyum.admin.content.api;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

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
class AdminPostReadSessionIntegrationTest {

    private static final String LIST_PATH =
            "/api/v1/admin/posts";

    private static final String PASSWORD =
            "AdminReadTest123!";

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-30T10:00:00Z");

    private static final Instant HIDDEN_AT =
            Instant.parse("2026-10-30T11:00:00Z");

    private static final Instant DELETED_AT =
            Instant.parse("2026-10-30T12:00:00Z");

    private static final String ORIGINAL_CONTENT =
            "첫 번째 줄\n\n두 번째 줄";

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
    JdbcTemplate jdbcTemplate;

    private Member admin;
    private Member author;

    private String keyword;

    private Long visiblePostId;
    private Long hiddenPostId;
    private Long deletedPostId;
    private Long hiddenAndDeletedPostId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "");

        keyword = "조회검증" + suffix;

        String passwordHash = passwordEncoder.encode(PASSWORD);

        admin = saveActiveMember(
                "admin-" + suffix + "@example.com",
                "조회관리" + suffix.substring(0, 8),
                passwordHash
        );

        author = saveActiveMember(
                "author-" + suffix + "@example.com",
                "조회작성" + suffix.substring(0, 8),
                passwordHash
        );

        // 테스트 준비용으로 관리자 권한과 인증 버전을 설정한다.
        int updatedRows = jdbcTemplate.update("""
                UPDATE members
                SET role = 'ADMIN',
                    credential_version = 3
                WHERE id = ?
                """,
                admin.getId()
        );

        assertThat(updatedRows).isEqualTo(1);

        visiblePostId = savePost("공개", false, false);
        hiddenPostId = savePost("숨김", true, false);
        deletedPostId = savePost("삭제", false, true);
        hiddenAndDeletedPostId =
                savePost("숨김후삭제", true, true);
    }

    @Test
    void 실제_관리자_로그인으로_목록과_숨김삭제_상세를_조회한다()
            throws Exception {
        MockHttpSession session = login(admin.getEmail());

        // 작성 시각을 동일하게 저장했으므로 ID 내림차순이다.
        expectOk(readList(session, "ALL", 0, 20))
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.totalElements")
                        .value(is(4L), Long.class))
                .andExpect(jsonPath("$.items[0].postId")
                        .value(is(hiddenAndDeletedPostId), Long.class))
                .andExpect(jsonPath("$.items[0].status")
                        .value("DELETED"))
                .andExpect(jsonPath("$.items[1].postId")
                        .value(is(deletedPostId), Long.class))
                .andExpect(jsonPath("$.items[2].postId")
                        .value(is(hiddenPostId), Long.class))
                .andExpect(jsonPath("$.items[2].status")
                        .value("HIDDEN"))
                .andExpect(jsonPath("$.items[3].postId")
                        .value(is(visiblePostId), Long.class))
                .andExpect(jsonPath("$.items[3].status")
                        .value("VISIBLE"))
                .andExpect(jsonPath("$.items[3].contentPreview")
                        .value("첫 번째 줄 두 번째 줄"));

        expectOk(readDetail(session, hiddenPostId))
                .andExpect(jsonPath("$.status").value("HIDDEN"))
                .andExpect(jsonPath("$.content").value(ORIGINAL_CONTENT))
                .andExpect(jsonPath("$.hiddenAt")
                        .value(HIDDEN_AT.toString()))
                .andExpect(jsonPath("$.author.memberId")
                        .value(is(author.getId()), Long.class));

        expectOk(readDetail(session, hiddenAndDeletedPostId))
                .andExpect(jsonPath("$.status").value("DELETED"))
                .andExpect(jsonPath("$.content").value(ORIGINAL_CONTENT))
                .andExpect(jsonPath("$.hiddenAt")
                        .value(HIDDEN_AT.toString()))
                .andExpect(jsonPath("$.deletedAt")
                        .value(DELETED_AT.toString()));

        // 관리자 조회가 가능해도 일반 공개 조회에서는 숨겨져야 한다.
        mockMvc.perform(get(
                        "/api/v1/community/posts/{postId}",
                        hiddenPostId
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    void 상태_검색과_페이지_전체_개수가_실제_DB에서_연결된다()
            throws Exception {
        MockHttpSession session = login(admin.getEmail());

        expectOk(readList(session, "VISIBLE", 0, 20))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].postId")
                        .value(is(visiblePostId), Long.class))
                .andExpect(jsonPath("$.totalElements")
                        .value(is(1L), Long.class));

        expectOk(readList(session, "HIDDEN", 0, 20))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].postId")
                        .value(is(hiddenPostId), Long.class))
                .andExpect(jsonPath("$.totalElements")
                        .value(is(1L), Long.class));

        expectOk(readList(session, "DELETED", 0, 1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].postId")
                        .value(is(hiddenAndDeletedPostId), Long.class))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements")
                        .value(is(2L), Long.class))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.hasPrevious").value(false));

        expectOk(readList(session, "DELETED", 1, 1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].postId")
                        .value(is(deletedPostId), Long.class))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements")
                        .value(is(2L), Long.class))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(true));

        expectOk(readList(session, "DELETED", 2, 1))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements")
                        .value(is(2L), Long.class));
    }

    @Test
    void 검색_결과가_없으면_200이고_상세가_없으면_404다()
            throws Exception {
        MockHttpSession session = login(admin.getEmail());

        expectOk(mockMvc.perform(
                get(LIST_PATH)
                        .session(session)
                        .param("keyword", keyword + "없는검색어")
        ))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements")
                        .value(is(0L), Long.class));

        // 테스트 전용 DB에 존재하지 않는 양수 ID를 사용한다.
        readDetail(session, Long.MAX_VALUE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ));
    }

    @Test
    void 작성자가_탈퇴해도_관리자는_게시글과_탈퇴_표시를_조회한다()
            throws Exception {
        MockHttpSession session = login(admin.getEmail());

        author.withdraw(DELETED_AT);
        memberRepository.saveAndFlush(author);

        expectOk(readList(session, "VISIBLE", 0, 20))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].author.memberId")
                        .value(is(author.getId()), Long.class))
                .andExpect(jsonPath("$.items[0].author.nickname")
                        .value("탈퇴한 회원"))
                .andExpect(jsonPath("$.items[0].author.withdrawn")
                        .value(true));

        expectOk(readDetail(session, hiddenPostId))
                .andExpect(jsonPath("$.content").value(ORIGINAL_CONTENT))
                .andExpect(jsonPath("$.author.nickname")
                        .value("탈퇴한 회원"))
                .andExpect(jsonPath("$.author.withdrawn").value(true))
                .andExpect(jsonPath("$.author.email").doesNotExist())
                .andExpect(jsonPath("$.author.passwordHash").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 실제_일반_회원_세션은_관리자_조회가_403이다(
            boolean detail
    ) throws Exception {
        MockHttpSession session = login(author.getEmail());

        mockMvc.perform(
                        get(targetPath(detail))
                                .session(session)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.items").doesNotExist())
                .andExpect(jsonPath("$.content").doesNotExist());

        // 일반 회원의 유효한 로그인 자체를 무효화할 이유는 없다.
        assertThat(session.isInvalid()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "ROLE, false",
            "ROLE, true",
            "VERSION, false",
            "VERSION, true",
            "STATUS, false",
            "STATUS, true"
    })
    void 로그인_후_권한이나_인증정보가_변경되면_기존_세션을_차단한다(
            String change,
            boolean detail
    ) throws Exception {
        MockHttpSession session = login(admin.getEmail());

        // 변경 전에는 동일한 세션으로 조회할 수 있다.
        expectOk(mockMvc.perform(
                get(targetPath(detail))
                        .session(session)
                        .param("keyword", keyword)
        ));

        int updatedRows = switch (change) {
            case "ROLE" -> jdbcTemplate.update("""
                    UPDATE members
                    SET role = 'USER'
                    WHERE id = ?
                    """,
                    admin.getId()
            );

            case "VERSION" -> jdbcTemplate.update("""
                    UPDATE members
                    SET credential_version = credential_version + 1
                    WHERE id = ?
                    """,
                    admin.getId()
            );

            case "STATUS" -> jdbcTemplate.update("""
                    UPDATE members
                    SET status = 'PENDING'
                    WHERE id = ?
                    """,
                    admin.getId()
            );

            default -> throw new IllegalArgumentException(
                    "지원하지 않는 테스트 조건: " + change
            );
        };

        assertThat(updatedRows).isEqualTo(1);

        mockMvc.perform(
                        get(targetPath(detail))
                                .session(session)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.items").doesNotExist())
                .andExpect(jsonPath("$.content").doesNotExist())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        assertThat(session.isInvalid()).isTrue();

        if (change.equals("ROLE")) {
            // 권한 강등 후 새로 로그인하면 USER 세션이므로 403이다.
            MockHttpSession newSession = login(admin.getEmail());

            mockMvc.perform(
                            get(targetPath(detail))
                                    .session(newSession)
                    )
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code")
                            .value("ACCESS_DENIED"));
        }
    }

    private ResultActions readList(
            MockHttpSession session,
            String status,
            int page,
            int size
    ) throws Exception {
        return mockMvc.perform(
                get(LIST_PATH)
                        .session(session)
                        .param("status", status)
                        .param("keyword", keyword)
                        .param("page", Integer.toString(page))
                        .param("size", Integer.toString(size))
        );
    }

    private ResultActions readDetail(
            MockHttpSession session,
            Long postId
    ) throws Exception {
        return mockMvc.perform(
                get(LIST_PATH + "/{postId}", postId)
                        .session(session)
        );
    }

    private String targetPath(boolean detail) {
        return detail
                ? LIST_PATH + "/" + hiddenPostId
                : LIST_PATH;
    }

    private ResultActions expectOk(
            ResultActions response
    ) throws Exception {
        return response
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ));
    }

    private MockHttpSession login(String email) throws Exception {
        // 실제 CSRF API로 토큰과 세션을 발급받는다.
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

        String headerName = csrfBody.path("headerName").asString();
        String token = csrfBody.path("token").asString();

        assertThat(headerName).isEqualTo("X-CSRF-TOKEN");
        assertThat(token).isNotBlank();

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

        MockHttpSession authenticatedSession =
                (MockHttpSession) loginResult
                        .getRequest()
                        .getSession(false);

        assertThat(authenticatedSession).isNotNull();

        // 이후 요청은 GET이므로 새 CSRF 토큰을 발급받을 필요가 없다.
        return authenticatedSession;
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

    private Long savePost(
            String label,
            boolean hidden,
            boolean deleted
    ) {
        Post post = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                keyword + " " + label,
                ORIGINAL_CONTENT
        );

        if (hidden) {
            post.hide(HIDDEN_AT);
        }

        if (deleted) {
            post.delete(DELETED_AT);
        }

        Post saved = postRepository.saveAndFlush(post);

        // 작성 시각을 동일하게 만들어 ID 정렬까지 확정적으로 검증한다.
        int updatedRows = jdbcTemplate.update("""
                UPDATE community_posts
                SET created_at = ?
                WHERE id = ?
                """,
                Timestamp.from(CREATED_AT),
                saved.getId()
        );

        assertThat(updatedRows).isEqualTo(1);

        return saved.getId();
    }
}