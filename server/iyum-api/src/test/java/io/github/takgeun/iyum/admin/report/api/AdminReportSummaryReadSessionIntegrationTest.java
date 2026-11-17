package io.github.takgeun.iyum.admin.report.api;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.report.domain.ContentReport;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import static org.hamcrest.Matchers.is;
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
class AdminReportSummaryReadSessionIntegrationTest {

    private static final String PATH =
            "/api/v1/admin/reports/summary";

    private static final String PASSWORD =
            "ReportSummaryTest123!";

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
    private Member reporter;

    private String passwordHash;

    /*
     * 클래스 전체에 @Transactional을 붙이지 않는다.
     * 준비·변경 데이터를 커밋한 뒤 요청에서 다시 조회한다.
     * 서비스와 저장소는 실제 빈을 사용한다.
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
        reporter = saveMember("신고자");

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
    void 실제_관리자_세션으로_전체_대기와_오늘_접수를_구분해_조회한다()
            throws Exception {
        createFixture();

        MockHttpSession session = login(admin.getEmail());

        // 어제 게시글 신고 1건 + 오늘 댓글 신고 1건.
        assertSummary(
                session,
                2,
                1,
                0
        );
    }

    @Test
    void 신고가_없으면_실제_API에서도_모든_집계에_0을_반환한다()
            throws Exception {
        MockHttpSession session = login(admin.getEmail());

        assertSummary(
                session,
                0,
                0,
                0
        );
    }

    @ParameterizedTest(name = "{0} 실행 후 오늘 종결 집계 반영")
    @CsvSource({
            "resolve, RESOLVED, NO_CONTENT_ACTION",
            "dismiss, DISMISSED, NO_CONTENT_ACTION",
            "hide-and-resolve, RESOLVED, CONTENT_HIDDEN"
    })
    void 어제_접수한_신고를_오늘_종결하면_대기는_감소하고_오늘_종결은_증가한다(
            String action,
            String expectedStatus,
            String expectedOutcome
    ) throws Exception {
        Fixture fixture = createFixture();
        MockHttpSession session = login(admin.getEmail());

        assertSummary(
                session,
                2,
                1,
                0
        );

        // 어제 접수된 게시글 신고를 실제 처리 API로 종결한다.
        handleReport(
                session,
                fixture.postReportId(),
                action
        )
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(jsonPath("$.reportId").value(
                        is(fixture.postReportId()),
                        Long.class
                ))
                .andExpect(jsonPath("$.status")
                        .value(expectedStatus))
                .andExpect(jsonPath("$.handlingOutcome")
                        .value(expectedOutcome));

        // 오늘 접수는 그대로 1건이고 오늘 종결만 1건 증가한다.
        assertSummary(
                session,
                1,
                1,
                1
        );

        // 선택하지 않은 댓글 신고는 여전히 접수 상태다.
        mockMvc.perform(
                        get(
                                "/api/v1/admin/reports/{reportId}",
                                fixture.commentReportId()
                        )
                                .session(session)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        if ("hide-and-resolve".equals(action)) {
            // 종결 후 콘텐츠를 복원해도 신고 집계는 유지된다.
            transactions.executeWithoutResult(transaction -> {
                Post post = entityManager.find(
                        Post.class,
                        fixture.postId()
                );

                assertThat(post.getHiddenAt()).isNotNull();
                post.restore();
            });

            assertSummary(
                    session,
                    1,
                    1,
                    1
            );
        }
    }

    @Test
    void 콘텐츠만_숨기고_복원해도_신고_요약_수치는_유지된다()
            throws Exception {
        Fixture fixture = createFixture();
        MockHttpSession session = login(admin.getEmail());

        assertSummary(
                session,
                2,
                1,
                0
        );

        transactions.executeWithoutResult(transaction -> {
            Post post = entityManager.find(
                    Post.class,
                    fixture.postId()
            );
            Comment comment = entityManager.find(
                    Comment.class,
                    fixture.commentId()
            );

            post.hide(NOW);
            comment.hide(NOW);
        });

        assertSummary(
                session,
                2,
                1,
                0
        );

        transactions.executeWithoutResult(transaction -> {
            Post post = entityManager.find(
                    Post.class,
                    fixture.postId()
            );
            Comment comment = entityManager.find(
                    Comment.class,
                    fixture.commentId()
            );

            assertThat(post.getHiddenAt()).isEqualTo(NOW);
            assertThat(comment.getHiddenAt()).isEqualTo(NOW);

            post.restore();
            comment.restore();
        });

        assertSummary(
                session,
                2,
                1,
                0
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
        MockHttpSession session = login(reporter.getEmail());

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
        // 대상 콘텐츠는 어제 생성하고 신고 접수일을 각각 다르게 한다.
        setAuditingTime(BEFORE);

        Fixture fixture = transactions.execute(transaction -> {
            Member managedAuthor = entityManager.getReference(
                    Member.class,
                    author.getId()
            );

            Post post = Post.create(
                    managedAuthor,
                    PostType.GENERAL,
                    PostTopic.DAILY,
                    "신고 요약 통합 테스트 게시글",
                    "신고 요약 통합 테스트 본문입니다."
            );

            entityManager.persist(post);
            entityManager.flush();

            Comment comment = Comment.create(
                    post,
                    managedAuthor,
                    "신고 요약 통합 테스트 댓글입니다."
            );

            entityManager.persist(comment);
            entityManager.flush();

            ContentReport postReport = ContentReport.forPost(
                    reporter.getId(),
                    post.getId(),
                    ContentReportReasonType.SPAM_ADVERTISING,
                    null,
                    post.getTitle(),
                    post.getContent(),
                    BEFORE
            );

            entityManager.persist(postReport);
            entityManager.flush();

            ContentReport commentReport = ContentReport.forComment(
                    reporter.getId(),
                    comment.getId(),
                    ContentReportReasonType.ABUSE_HATE,
                    null,
                    comment.getContent(),
                    NOW
            );

            entityManager.persist(commentReport);
            entityManager.flush();

            return new Fixture(
                    post.getId(),
                    comment.getId(),
                    postReport.getId(),
                    commentReport.getId()
            );
        });

        setAuditingTime(NOW);

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
            int pendingCount,
            int todayCount,
            int handledCount
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
                .andExpect(jsonPath("$.counts.pendingReportCount")
                        .value(pendingCount))
                .andExpect(jsonPath("$.counts.todayReportCount")
                        .value(todayCount))
                .andExpect(jsonPath("$.counts.todayHandledReportCount")
                        .value(handledCount));
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
                .andExpect(jsonPath("$.counts").doesNotHaveJsonPath())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));
    }

    private ResultActions handleReport(
            MockHttpSession session,
            Long reportId,
            String action
    ) throws Exception {
        MvcResult csrfResult = mockMvc.perform(
                        get("/api/v1/auth/csrf")
                                .session(session)
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode csrf = body(csrfResult);

        String headerName = csrf.path("headerName").asString();
        String token = csrf.path("token").asString();

        assertThat(headerName).isEqualTo("X-CSRF-TOKEN");
        assertThat(token).isNotBlank();

        return mockMvc.perform(
                post(
                        "/api/v1/admin/reports/{reportId}/{action}",
                        reportId,
                        action
                )
                        .session(session)
                        .header(headerName, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of(
                                        "handlingReason",
                                        "신고 요약 집계 반영을 확인합니다."
                                )
                        ))
        );
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
            Long postId,
            Long commentId,
            Long postReportId,
            Long commentReportId
    ) {
    }
}