package io.github.takgeun.iyum.admin.content.api;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
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
@Execution(ExecutionMode.SAME_THREAD)
class AdminContentSummaryReadSessionIntegrationTest {

    private static final String PATH =
            "/api/v1/admin/content/summary";

    private static final String PASSWORD =
            "ContentSummaryTest123!";

    private static final Instant NOW =
            Instant.parse("2026-11-17T03:00:00Z");

    private static final Instant BEFORE =
            Instant.parse("2026-11-16T03:00:00Z");

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
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @MockitoBean
    Clock clock;

    @MockitoBean(name = "auditingDateTimeProvider")
    DateTimeProvider dateTimeProvider;

    private TransactionTemplate transactions;

    private Member admin;
    private Member author;

    private String passwordHash;

    /*
     * 클래스 전체에 @Transactional을 붙이지 않는다.
     * 데이터를 준비하거나 변경한 트랜잭션이 커밋된 뒤
     * 별도 요청에서 다시 조회하도록 한다.
     */
    @BeforeEach
    void setUp() {
        transactions = new TransactionTemplate(transactionManager);

        // 이 클래스 전용 Testcontainers DB의 데이터만 정리한다.
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    content_reports,
                    community_comments,
                    community_posts,
                    members
                CASCADE
                """);

        when(clock.instant()).thenReturn(NOW);
        setAuditingTime(BEFORE);

        passwordHash = passwordEncoder.encode(PASSWORD);

        admin = saveMember("관리자");
        author = saveMember("작성자");

        int updated = jdbcTemplate.update("""
                UPDATE members
                   SET role = 'ADMIN',
                       credential_version = 3
                 WHERE id = ?
                """,
                admin.getId()
        );

        assertThat(updated).isEqualTo(1);

        setAuditingTime(NOW);
    }

    @Test
    void 실제_관리자_세션으로_오늘_작성과_현재_숨김을_구분해_조회한다()
            throws Exception {
        createFixture();

        MockHttpSession session = login(admin.getEmail());

        // 오늘: 게시글 1건, 댓글 2건.
        // 어제 생성하고 숨긴 게시글·댓글: 각각 1건.
        assertSummary(
                session,
                1,
                2,
                1,
                1
        );
    }

    @Test
    void 콘텐츠가_없으면_실제_API에서도_모든_집계에_0을_반환한다()
            throws Exception {
        MockHttpSession session = login(admin.getEmail());

        assertSummary(
                session,
                0,
                0,
                0,
                0
        );
    }

    @Test
    void 콘텐츠_숨김_복원_삭제가_커밋되면_다음_요약_조회에_반영된다()
            throws Exception {
        Fixture fixture = createFixture();
        MockHttpSession session = login(admin.getEmail());

        assertSummary(
                session,
                1,
                2,
                1,
                1
        );

        // 오늘 작성한 게시글과 댓글 한 건을 숨긴다.
        transactions.executeWithoutResult(transaction -> {
            Post post = entityManager.find(
                    Post.class,
                    fixture.todayPostId()
            );
            Comment comment = entityManager.find(
                    Comment.class,
                    fixture.todayCommentId()
            );

            post.hide(NOW);
            comment.hide(NOW);
        });

        // 오늘 작성 수는 유지되고 현재 숨김 수만 증가한다.
        assertSummary(
                session,
                1,
                2,
                2,
                2
        );

        transactions.executeWithoutResult(transaction -> {
            Post post = entityManager.find(
                    Post.class,
                    fixture.todayPostId()
            );
            Comment comment = entityManager.find(
                    Comment.class,
                    fixture.todayCommentId()
            );

            assertThat(post.getHiddenAt()).isEqualTo(NOW);
            assertThat(comment.getHiddenAt()).isEqualTo(NOW);

            post.restore();
            comment.restore();
        });

        // 어제부터 숨겨져 있던 대상만 남는다.
        assertSummary(
                session,
                1,
                2,
                1,
                1
        );

        transactions.executeWithoutResult(transaction -> {
            Post post = entityManager.find(
                    Post.class,
                    fixture.todayPostId()
            );
            Comment comment = entityManager.find(
                    Comment.class,
                    fixture.todayCommentId()
            );

            post.hide(NOW);
            comment.hide(NOW);

            post.delete(NOW);
            comment.delete(NOW);
        });

        // 숨김 후 삭제된 대상은 현재 숨김에서 제외된다.
        // 삭제된 게시글과 댓글도 오늘 작성 수에는 남는다.
        // 삭제된 부모 게시글의 다른 댓글도 오늘 작성 수에 남는다.
        assertSummary(
                session,
                1,
                2,
                1,
                1
        );
    }

    @Test
    void 로그인_후_인증_버전이_변경되면_기존_세션은_401로_거부한다()
            throws Exception {
        MockHttpSession session = login(admin.getEmail());

        assertSummary(
                session,
                0,
                0,
                0,
                0
        );

        int updated = jdbcTemplate.update("""
                UPDATE members
                   SET credential_version = credential_version + 1
                 WHERE id = ?
                """,
                admin.getId()
        );

        assertThat(updated).isEqualTo(1);

        expectError(
                mockMvc.perform(
                        get(PATH)
                                .session(session)
                                .accept(MediaType.APPLICATION_JSON)
                ),
                401,
                "AUTHENTICATION_REQUIRED"
        );
    }

    @Test
    void 실제_일반_회원_로그인_세션은_403으로_거부한다()
            throws Exception {
        MockHttpSession session = login(author.getEmail());

        expectError(
                mockMvc.perform(
                        get(PATH)
                                .session(session)
                                .accept(MediaType.APPLICATION_JSON)
                ),
                403,
                "ACCESS_DENIED"
        );
    }

    private Fixture createFixture() {
        Fixture fixture = transactions.execute(transaction -> {
            Member managedAuthor = entityManager.getReference(
                    Member.class,
                    author.getId()
            );

            // 어제 작성하고 숨긴 게시글·댓글.
            setAuditingTime(BEFORE);

            Post oldPost = Post.create(
                    managedAuthor,
                    PostType.GENERAL,
                    PostTopic.DAILY,
                    "어제 작성한 숨김 게시글",
                    "과거 콘텐츠의 현재 숨김 집계를 검증합니다."
            );

            entityManager.persist(oldPost);
            entityManager.flush();

            Comment oldComment = Comment.create(
                    oldPost,
                    managedAuthor,
                    "어제 작성한 숨김 댓글입니다."
            );

            entityManager.persist(oldComment);
            entityManager.flush();

            oldPost.hide(BEFORE.plusSeconds(60));
            oldComment.hide(BEFORE.plusSeconds(60));

            entityManager.flush();

            // 오늘 작성한 정상 게시글 1건과 댓글 2건.
            setAuditingTime(NOW);

            Post todayPost = Post.create(
                    managedAuthor,
                    PostType.GENERAL,
                    PostTopic.DAILY,
                    "오늘 작성한 게시글",
                    "오늘 작성 수와 상태 변경을 검증합니다."
            );

            entityManager.persist(todayPost);
            entityManager.flush();

            Comment firstComment = Comment.create(
                    todayPost,
                    managedAuthor,
                    "오늘 작성한 첫 번째 댓글입니다."
            );

            Comment secondComment = Comment.create(
                    todayPost,
                    managedAuthor,
                    "오늘 작성한 두 번째 댓글입니다."
            );

            entityManager.persist(firstComment);
            entityManager.persist(secondComment);
            entityManager.flush();

            return new Fixture(
                    todayPost.getId(),
                    firstComment.getId()
            );
        });

        assertThat(fixture).isNotNull();

        return fixture;
    }

    private Member saveMember(String label) {
        String unique = UUID.randomUUID()
                .toString()
                .replace("-", "");

        Member member = Member.createPending(
                unique + "@example.com",
                passwordHash,
                label + unique.substring(0, 8)
        );

        member.activeAfterEmailVerification();

        return memberRepository.saveAndFlush(member);
    }

    private MockHttpSession login(String email) throws Exception {
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

        JsonNode csrf = body(csrfResult);

        String headerName = csrf.path("headerName").asString();
        String token = csrf.path("token").asString();

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

        // 로그인 과정에서 세션이 교체될 수 있으므로 다시 가져온다.
        MockHttpSession session =
                (MockHttpSession) loginResult
                        .getRequest()
                        .getSession(false);

        assertThat(session).isNotNull();
        assertThat(session.isInvalid()).isFalse();

        return session;
    }

    private void assertSummary(
            MockHttpSession session,
            int todayPostCount,
            int todayCommentCount,
            int hiddenPostCount,
            int hiddenCommentCount
    ) throws Exception {
        // 조회 GET에는 CSRF 토큰을 전달하지 않는다.
        mockMvc.perform(
                        get(PATH)
                                .session(session)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(jsonPath("$.queriedAt")
                        .value(NOW.toString()))
                .andExpect(jsonPath("$.baseDate")
                        .value("2026-11-17"))
                .andExpect(jsonPath("$.counts.todayPostCount")
                        .value(todayPostCount))
                .andExpect(jsonPath("$.counts.todayCommentCount")
                        .value(todayCommentCount))
                .andExpect(jsonPath("$.counts.hiddenPostCount")
                        .value(hiddenPostCount))
                .andExpect(jsonPath("$.counts.hiddenCommentCount")
                        .value(hiddenCommentCount));
    }

    private void expectError(
            ResultActions response,
            int expectedStatus,
            String expectedCode
    ) throws Exception {
        response
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.counts").doesNotHaveJsonPath())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsByteArray()
        );
    }

    private void setAuditingTime(Instant time) {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(time));
    }

    private record Fixture(
            Long todayPostId,
            Long todayCommentId
    ) {
    }
}