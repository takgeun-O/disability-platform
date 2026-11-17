package io.github.takgeun.iyum.admin.dashboard.api;

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
class AdminDashboardReadSessionIntegrationTest {

    private static final String PATH =
            "/api/v1/admin/dashboard";

    private static final String PASSWORD =
            "AdminDashboardTest123!";

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
     * 준비 데이터는 커밋한 뒤 HTTP 요청에서 다시 조회한다.
     *
     * 서비스와 저장소는 실제 빈을 사용한다.
     * 재현 가능한 날짜 검증을 위해 시간 공급자만 고정한다.
     */
    @BeforeEach
    void setUp() {
        transactions = new TransactionTemplate(transactionManager);

        // 이 클래스 전용 Testcontainers DB의 테스트 데이터만 정리한다.
        // FK로 연결된 데이터도 함께 정리하여 테스트 간 집계 누적을 막는다.
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

        // 이후 생성하는 테스트 데이터는 오늘 생성된 것으로 기록한다.
        setAuditingTime(NOW);
    }

    @Test
    void 실제_관리자_로그인_세션으로_집계와_신고_목록을_조회한다()
            throws Exception {
        // 기본 회원 3명은 어제 생성했고 이 회원만 오늘 생성한다.
        saveMember("오늘가입");

        Fixture fixture = createFixture();

        MockHttpSession session = login(admin.getEmail());

        // GET에는 CSRF 토큰을 넣지 않는다.
        // 요청 파라미터로 세션의 인증 정보를 덮어쓸 수 없다.
        expectOk(mockMvc.perform(
                get(PATH)
                        .session(session)
                        .accept(MediaType.APPLICATION_JSON)
                        .queryParam(
                                "actorMemberId",
                                reporter.getId().toString()
                        )
                        .queryParam("credentialVersion", "999")
        ))
                .andExpect(jsonPath("$.queriedAt")
                        .value(NOW.toString()))
                .andExpect(jsonPath("$.baseDate")
                        .value("2026-11-17"))
                .andExpect(jsonPath("$.counts.pendingReportCount")
                        .value(2))
                .andExpect(jsonPath("$.counts.todayReportCount")
                        .value(2))
                .andExpect(jsonPath("$.counts.todayPostCount")
                        .value(1))
                .andExpect(jsonPath("$.counts.todayMemberCount")
                        .value(1))
                .andExpect(jsonPath("$.pendingReports").isArray())
                .andExpect(jsonPath("$.pendingReports.length()")
                        .value(2))
                .andExpect(jsonPath("$.pendingReports[0].reportId")
                        .value(is(fixture.postReportId()), Long.class))
                .andExpect(jsonPath("$.pendingReports[0].targetType")
                        .value("POST"))
                .andExpect(jsonPath("$.pendingReports[0].targetId")
                        .value(is(fixture.postId()), Long.class))
                .andExpect(jsonPath("$.pendingReports[0].reasonType")
                        .value("SPAM_ADVERTISING"))
                .andExpect(jsonPath("$.pendingReports[0].createdAt")
                        .value(NOW.toString()))
                .andExpect(jsonPath("$.pendingReports[1].reportId")
                        .value(is(fixture.commentReportId()), Long.class))
                .andExpect(jsonPath("$.pendingReports[1].targetType")
                        .value("COMMENT"))
                .andExpect(jsonPath("$.pendingReports[1].targetId")
                        .value(is(fixture.commentId()), Long.class))
                .andExpect(jsonPath("$.pendingReports[1].reasonType")
                        .value("ABUSE_HATE"))
                .andExpect(jsonPath("$.pendingReports[1].createdAt")
                        .value(NOW.toString()));
    }

    @Test
    void 집계_대상이_없으면_실제_API에서도_0과_빈_배열을_반환한다()
            throws Exception {
        MockHttpSession session = login(admin.getEmail());

        expectOk(mockMvc.perform(
                get(PATH)
                        .session(session)
                        .accept(MediaType.APPLICATION_JSON)
        ))
                .andExpect(jsonPath("$.queriedAt")
                        .value(NOW.toString()))
                .andExpect(jsonPath("$.baseDate")
                        .value("2026-11-17"))
                .andExpect(jsonPath("$.counts.pendingReportCount")
                        .value(0))
                .andExpect(jsonPath("$.counts.todayReportCount")
                        .value(0))
                .andExpect(jsonPath("$.counts.todayPostCount")
                        .value(0))
                .andExpect(jsonPath("$.counts.todayMemberCount")
                        .value(0))
                .andExpect(jsonPath("$.pendingReports").isArray())
                .andExpect(jsonPath("$.pendingReports").isEmpty());
    }

    @Test
    void 로그인_후_인증_버전이_변경되면_기존_세션은_401로_거부한다()
            throws Exception {
        MockHttpSession session = login(admin.getEmail());

        // 변경 전에는 실제로 조회 가능한 세션인지 확인한다.
        expectOk(mockMvc.perform(
                get(PATH)
                        .session(session)
                        .accept(MediaType.APPLICATION_JSON)
        ));

        int updated = jdbcTemplate.update("""
                UPDATE members
                   SET credential_version = credential_version + 1
                 WHERE id = ?
                """,
                admin.getId()
        );

        assertThat(updated).isEqualTo(1);

        mockMvc.perform(
                        get(PATH)
                                .session(session)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.counts")
                        .doesNotHaveJsonPath())
                .andExpect(jsonPath("$.pendingReports")
                        .doesNotHaveJsonPath())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));
    }

    @Test
    void 게시글과_댓글을_숨기고_복원해도_처리_대기_신고는_유지된다()
            throws Exception {
        Fixture fixture = createFixture();
        MockHttpSession session = login(admin.getEmail());

        assertFixtureDashboard(
                session,
                fixture.postReportId(),
                fixture.commentReportId()
        );

        transactions.executeWithoutResult(transactionStatus -> {
            Post targetPost = entityManager.find(
                    Post.class,
                    fixture.postId()
            );
            Comment targetComment = entityManager.find(
                    Comment.class,
                    fixture.commentId()
            );

            targetPost.hide(NOW);
            targetComment.hide(NOW);
        });

        assertFixtureDashboard(
                session,
                fixture.postReportId(),
                fixture.commentReportId()
        );

        transactions.executeWithoutResult(transactionStatus -> {
            Post targetPost = entityManager.find(
                    Post.class,
                    fixture.postId()
            );
            Comment targetComment = entityManager.find(
                    Comment.class,
                    fixture.commentId()
            );

            assertThat(targetPost.getHiddenAt()).isEqualTo(NOW);
            assertThat(targetComment.getHiddenAt()).isEqualTo(NOW);

            targetPost.restore();
            targetComment.restore();
        });

        assertFixtureDashboard(
                session,
                fixture.postReportId(),
                fixture.commentReportId()
        );

        transactions.executeWithoutResult(transactionStatus -> {
            Post targetPost = entityManager.find(
                    Post.class,
                    fixture.postId()
            );
            Comment targetComment = entityManager.find(
                    Comment.class,
                    fixture.commentId()
            );

            assertThat(targetPost.getHiddenAt()).isNull();
            assertThat(targetComment.getHiddenAt()).isNull();
        });
    }

    @ParameterizedTest(name = "{0} 처리 후 대기 신고만 감소한다")
    @CsvSource({
            "resolve, RESOLVED",
            "dismiss, DISMISSED"
    })
    void 신고를_종결하면_대기_집계와_목록에서만_제외된다(
            String action,
            String expectedStatus
    ) throws Exception {
        Fixture fixture = createFixture();
        MockHttpSession session = login(admin.getEmail());

        assertFixtureDashboard(
                session,
                fixture.postReportId(),
                fixture.commentReportId()
        );

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
                        .value("NO_CONTENT_ACTION"));

        assertFixtureDashboard(
                session,
                fixture.commentReportId()
        );
    }

    private Fixture createFixture() {
        Fixture fixture = transactions.execute(transaction -> {
            Member managedAuthor = entityManager.getReference(
                    Member.class,
                    author.getId()
            );

            Post post = Post.create(
                    managedAuthor,
                    PostType.GENERAL,
                    PostTopic.DAILY,
                    "대시보드 통합 테스트 게시글",
                    "대시보드 통합 테스트 본문입니다."
            );

            entityManager.persist(post);
            entityManager.flush();

            Comment comment = Comment.create(
                    post,
                    managedAuthor,
                    "대시보드 통합 테스트 댓글입니다."
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
                    NOW
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

    private ResultActions expectOk(ResultActions response)
            throws Exception {
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

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsByteArray()
        );
    }

    private void setAuditingTime(Instant time) {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(time));
    }

    private void assertFixtureDashboard(
            MockHttpSession session,
            Long... expectedReportIds
    ) throws Exception {
        ResultActions result = mockMvc.perform(
                        get(PATH)
                                .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(jsonPath("$.counts.pendingReportCount")
                        .value(expectedReportIds.length))
                .andExpect(jsonPath("$.counts.todayReportCount")
                        .value(2))
                .andExpect(jsonPath("$.counts.todayPostCount")
                        .value(1))
                .andExpect(jsonPath("$.counts.todayMemberCount")
                        .value(0))
                .andExpect(jsonPath("$.pendingReports.length()")
                        .value(expectedReportIds.length));

        for(int index = 0; index < expectedReportIds.length; index++) {
            result.andExpect(
                    jsonPath(
                            "$.pendingReports[" + index + "].reportId"
                    ).value(
                            is(expectedReportIds[index]),
                            Long.class
                    )
            );
        }
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

        JsonNode csrfBody = body(csrfResult);
        String headerName = csrfBody.path("headerName").asString();
        String token = csrfBody.path("token").asString();

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
                                        "대시보드 집계 반영을 확인합니다."
                                )
                        ))
        );
    }

    private record Fixture(
            Long postId,
            Long commentId,
            Long postReportId,
            Long commentReportId
    ) {
    }
}