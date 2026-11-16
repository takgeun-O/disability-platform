package io.github.takgeun.iyum.admin.report.api;

import io.github.takgeun.iyum.admin.moderation.domain.ContentModerationLog;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationReasonType;
import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.report.domain.ContentReport;
import io.github.takgeun.iyum.community.report.domain.ContentReportHandlingOutcome;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import io.github.takgeun.iyum.community.report.infrastructure.ContentReportRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true",
        "iyum.auth.password-reset.mail.enabled=false",
        "iyum.auth.email-verification.mail.enabled=false",
        "iyum.cors.allowed-origin=http://127.0.0.1:8443",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("dev")
@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
class AdminReportHideAndResolveSessionIntegrationTest {

    private static final String ORIGIN =
            "http://127.0.0.1:8443";

    private static final String PASSWORD =
            "ReportHandling123!";

    private static final long INITIAL_VERSION = 3L;

    private static final Instant CREATED_AT =
            Instant.parse("2026-11-12T10:00:00Z");

    private static final Instant PREVIOUS_HIDDEN_AT =
            Instant.parse("2026-11-13T01:00:00Z");

    private static final String POST_TITLE =
            "숨김 처리 세션 테스트";

    private static final String POST_CONTENT =
            "신고 대상 게시글 내용입니다.";

    private static final String COMMENT_CONTENT =
            "신고 대상 댓글 내용입니다.";

    private static final String REPORT_REASON =
            "접수 당시 신고 사유";

    private static final String RAW_HANDLING_REASON =
            " \t 운영 정책 위반을 확인했습니다. \n";

    private static final String HANDLING_REASON =
            "운영 정책 위반을 확인했습니다.";

    private static final String PREVIOUS_REASON =
            "기존에 숨김 처리한 사유";

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
    ContentReportRepository reportRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    private TransactionTemplate transactions;
    private Fixture fixture;

    @BeforeEach
    void setUp() {
        transactions = new TransactionTemplate(transactionManager);

        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);

        String passwordHash = passwordEncoder.encode(PASSWORD);

        fixture = Objects.requireNonNull(
                transactions.execute(status -> {
                    Member admin = createMember(
                            "hide-admin-" + suffix + "@example.com",
                            "관리" + suffix,
                            passwordHash,
                            MemberRole.ADMIN
                    );

                    Member reporter = createMember(
                            "hide-reporter-" + suffix + "@example.com",
                            "신고" + suffix,
                            passwordHash,
                            MemberRole.USER
                    );

                    Member author = createMember(
                            "hide-author-" + suffix + "@example.com",
                            "작성" + suffix,
                            passwordHash,
                            MemberRole.USER
                    );

                    Post post = Post.create(
                            author,
                            PostType.GENERAL,
                            PostTopic.DAILY,
                            POST_TITLE,
                            POST_CONTENT
                    );
                    entityManager.persist(post);

                    Comment comment = Comment.create(
                            post,
                            author,
                            COMMENT_CONTENT
                    );
                    entityManager.persist(comment);
                    entityManager.flush();

                    ContentReport postReport = ContentReport.forPost(
                            reporter.getId(),
                            post.getId(),
                            ContentReportReasonType.OTHER,
                            REPORT_REASON,
                            POST_TITLE,
                            POST_CONTENT,
                            CREATED_AT
                    );

                    ContentReport commentReport = ContentReport.forComment(
                            reporter.getId(),
                            comment.getId(),
                            ContentReportReasonType.OTHER,
                            REPORT_REASON,
                            COMMENT_CONTENT,
                            CREATED_AT
                    );

                    entityManager.persist(postReport);
                    entityManager.persist(commentReport);
                    entityManager.flush();

                    return new Fixture(
                            admin.getId(),
                            admin.getEmail(),
                            reporter.getId(),
                            reporter.getEmail(),
                            author.getId(),
                            post.getId(),
                            comment.getId(),
                            postReport.getId(),
                            commentReport.getId()
                    );
                })
        );
    }

    @AfterEach
    void tearDown() {
        if (fixture == null) {
            return;
        }

        transactions.executeWithoutResult(status -> {
            jdbcTemplate.update(
                    """
                    DELETE FROM admin_content_moderation_logs
                    WHERE post_id = ? OR comment_id = ?
                    """,
                    fixture.postId(),
                    fixture.commentId()
            );

            jdbcTemplate.update(
                    """
                    DELETE FROM content_reports
                    WHERE post_id = ? OR comment_id = ?
                    """,
                    fixture.postId(),
                    fixture.commentId()
            );

            jdbcTemplate.update(
                    "DELETE FROM community_comments WHERE id = ?",
                    fixture.commentId()
            );

            jdbcTemplate.update(
                    "DELETE FROM community_posts WHERE id = ?",
                    fixture.postId()
            );

            jdbcTemplate.update(
                    "DELETE FROM members WHERE id IN (?, ?, ?)",
                    fixture.adminId(),
                    fixture.reporterId(),
                    fixture.authorId()
            );
        });
    }

    @ParameterizedTest
    @CsvSource({
            "POST, false",
            "POST, true",
            "COMMENT, false",
            "COMMENT, true"
    })
    void 실제_관리자_세션으로_숨김과_신고_처리_결과를_저장한다(
            Target target,
            boolean alreadyHidden
    ) throws Exception {
        if (alreadyHidden) {
            prepareAlreadyHidden(target);
        }

        CsrfSession client = login(fixture.adminEmail());
        DatabaseSnapshot before = snapshot();

        ContentReportHandlingOutcome expectedOutcome = alreadyHidden
                ? ContentReportHandlingOutcome.CONTENT_ALREADY_HIDDEN
                : ContentReportHandlingOutcome.CONTENT_HIDDEN;

        // 추가 필드로 서버가 정하는 처리 결과를 위조해 본다.
        String requestBody = objectMapper.writeValueAsString(
                Map.of(
                        "handlingReason", RAW_HANDLING_REASON,
                        "actorMemberId", fixture.reporterId(),
                        "credentialVersion", 999,
                        "reportId", Long.MAX_VALUE,
                        "handledByMemberId", fixture.reporterId(),
                        "handledAt", "2000-01-01T00:00:00Z",
                        "status", "DISMISSED",
                        "handlingOutcome", "NO_CONTENT_ACTION"
                )
        );

        Instant requestStartedAt = Instant.now();

        MvcResult result = submit(
                reportId(target),
                client,
                requestBody
        )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ORIGIN
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                        "true"
                ))
                .andExpect(jsonPath("$.reportId")
                        .value(reportId(target)))
                .andExpect(jsonPath("$.status")
                        .value("RESOLVED"))
                .andExpect(jsonPath("$.handledByMemberId")
                        .value(fixture.adminId()))
                .andExpect(jsonPath("$.handlingReason")
                        .value(HANDLING_REASON))
                .andExpect(jsonPath("$.handlingOutcome")
                        .value(expectedOutcome.name()))
                .andExpect(jsonPath("$.*", hasSize(6)))
                .andReturn();

        Instant requestFinishedAt = Instant.now();

        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsByteArray()
        );

        Instant responseHandledAt = Instant.parse(
                response.path("handledAt").asString()
        );

        assertThat(responseHandledAt)
                .isBetween(requestStartedAt, requestFinishedAt);

        ContentReport stored = readReport(reportId(target));

        assertThat(stored.getStatus())
                .isEqualTo(ContentReportStatus.RESOLVED);
        assertThat(stored.getHandledByMemberId())
                .isEqualTo(fixture.adminId());
        assertThat(stored.getHandlingReason())
                .isEqualTo(HANDLING_REASON);
        assertThat(stored.getHandlingOutcome())
                .isEqualTo(expectedOutcome);

        // PostgreSQL 저장 시 마이크로초 정밀도 차이를 허용한다.
        assertThat(stored.getHandledAt()).isBetween(
                responseHandledAt.minusNanos(1_000),
                responseHandledAt.plusNanos(1_000)
        );

        assertOriginalReportFields(stored, target);
        assertReceived(otherReportId(target));

        assertContentAndLog(
                target,
                alreadyHidden,
                stored.getHandledAt()
        );

        DatabaseSnapshot after = snapshot();

        // 콘텐츠 숨김과 신고 처리로 회원 상태를 변경하지 않는다.
        assertThat(after.members()).isEqualTo(before.members());

        if (target == Target.POST) {
            // 게시글을 숨겨도 댓글 자체를 개별 숨김 처리하지 않는다.
            assertThat(after.comments()).isEqualTo(before.comments());
        } else {
            // 댓글 숨김으로 부모 게시글을 변경하지 않는다.
            assertThat(after.posts()).isEqualTo(before.posts());
        }

        if (alreadyHidden) {
            assertThat(after.posts()).isEqualTo(before.posts());
            assertThat(after.comments()).isEqualTo(before.comments());
            assertThat(after.logs()).isEqualTo(before.logs());
        }

        assertThat(client.session().isInvalid()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(Target.class)
    void 같은_신고를_다시_처리하면_409이며_DB를_보존한다(
            Target target
    ) throws Exception {
        CsrfSession client = login(fixture.adminEmail());

        submit(
                reportId(target),
                client,
                body(HANDLING_REASON)
        )
                .andExpect(status().isOk());

        DatabaseSnapshot beforeRetry = snapshot();

        submit(
                reportId(target),
                client,
                body("다시 처리하려는 사유")
        )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("REPORT_ALREADY_HANDLED"));

        assertThat(snapshot()).isEqualTo(beforeRetry);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "MISSING",
            "NULL",
            "BLANK",
            "TOO_LONG"
    })
    void 잘못된_사유는_400이며_콘텐츠와_신고를_변경하지_않는다(
            String inputType
    ) throws Exception {
        CsrfSession client = login(fixture.adminEmail());

        String requestBody = switch (inputType) {
            case "MISSING" -> "{}";
            case "NULL" -> body(null);
            case "BLANK" -> body(" \t\u00A0\u3000\n");
            case "TOO_LONG" -> body("가".repeat(501));
            default -> throw new IllegalArgumentException(inputType);
        };

        DatabaseSnapshot before = snapshot();

        submit(
                fixture.postReportId(),
                client,
                requestBody
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_HANDLING_REASON"));

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void 일반_회원의_실제_로그인_세션은_403이다()
            throws Exception {
        CsrfSession client = login(fixture.reporterEmail());
        DatabaseSnapshot before = snapshot();

        submit(
                fixture.postReportId(),
                client,
                body(HANDLING_REASON)
        )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("ACCESS_DENIED"));

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void 비로그인은_실제_CSRF_토큰이_있어도_401이다()
            throws Exception {
        CsrfSession anonymous = fetchCsrf(null);
        DatabaseSnapshot before = snapshot();

        submit(
                fixture.postReportId(),
                anonymous,
                body(HANDLING_REASON)
        )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 실제_관리자_세션도_CSRF가_없거나_틀리면_403이다(
            boolean invalidToken
    ) throws Exception {
        CsrfSession client = login(fixture.adminEmail());
        DatabaseSnapshot before = snapshot();

        MockHttpServletRequestBuilder builder = request(
                fixture.postReportId(),
                body(HANDLING_REASON)
        )
                .session(client.session())
                .header(HttpHeaders.ORIGIN, ORIGIN);

        if (invalidToken) {
            builder.header(
                    client.headerName(),
                    "invalid-csrf-token"
            );
        }

        mockMvc.perform(builder)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "STATUS",
            "VERSION",
            "ROLE"
    })
    void 로그인_후_관리자_정보가_바뀌면_기존_세션을_차단한다(
            String change
    ) throws Exception {
        CsrfSession oldClient = login(fixture.adminEmail());

        transactions.executeWithoutResult(status -> {
            Member admin = memberRepository
                    .findById(fixture.adminId())
                    .orElseThrow();

            switch (change) {
                case "STATUS" -> {
                    /*
                     * 상태 검증용 테스트 데이터를 준비한다.
                     * 관리자에게 suspend()를 호출하지 않는다.
                     * 관리자 정지 기능을 추가하는 것도 아니다.
                     */
                    ReflectionTestUtils.setField(
                            admin,
                            "status",
                            MemberStatus.SUSPENDED
                    );
                }

                case "VERSION" ->
                        admin.resetPassword(admin.getPasswordHash());

                case "ROLE" ->
                        ReflectionTestUtils.setField(
                                admin,
                                "role",
                                MemberRole.USER
                        );

                default -> throw new IllegalArgumentException(change);
            }
        });

        Member changed = memberRepository
                .findById(fixture.adminId())
                .orElseThrow();

        assertThat(changed.getStatus()).isEqualTo(
                change.equals("STATUS")
                        ? MemberStatus.SUSPENDED
                        : MemberStatus.ACTIVE
        );

        assertThat(changed.getRole()).isEqualTo(
                change.equals("ROLE")
                        ? MemberRole.USER
                        : MemberRole.ADMIN
        );

        assertThat(changed.getCredentialVersion()).isEqualTo(
                change.equals("VERSION")
                        ? INITIAL_VERSION + 1
                        : INITIAL_VERSION
        );

        DatabaseSnapshot beforeRequest = snapshot();

        assertThat(oldClient.session().isInvalid()).isFalse();

        // 로그인 당시 확보한 세션과 정상 토큰으로 바로 요청한다.
        // 중간에 CSRF를 다시 조회하지 않는다.
        submit(
                fixture.postReportId(),
                oldClient,
                body(HANDLING_REASON)
        )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        assertThat(oldClient.session().isInvalid()).isTrue();
        assertThat(snapshot()).isEqualTo(beforeRequest);
    }

    @Test
    void 없는_신고는_404이며_DB를_변경하지_않는다()
            throws Exception {
        CsrfSession client = login(fixture.adminEmail());
        DatabaseSnapshot before = snapshot();

        submit(
                Long.MAX_VALUE,
                client,
                body(HANDLING_REASON)
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("REPORT_NOT_FOUND"));

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void 허용되지_않은_Origin은_정상_세션과_토큰이_있어도_거부한다()
            throws Exception {
        CsrfSession client = login(fixture.adminEmail());
        DatabaseSnapshot before = snapshot();

        mockMvc.perform(
                        request(
                                fixture.postReportId(),
                                body(HANDLING_REASON)
                        )
                                .session(client.session())
                                .header(
                                        client.headerName(),
                                        client.token()
                                )
                                .header(
                                        HttpHeaders.ORIGIN,
                                        "https://untrusted.example"
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));

        assertThat(snapshot()).isEqualTo(before);
        assertThat(client.session().isInvalid()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "POST, RECEIVED",
            "COMMENT, RECEIVED",
            "POST, CONTENT_HIDDEN",
            "COMMENT, CONTENT_HIDDEN",
            "POST, CONTENT_ALREADY_HIDDEN",
            "COMMENT, CONTENT_ALREADY_HIDDEN",
            "POST, RESOLVED_NO_ACTION",
            "COMMENT, RESOLVED_NO_ACTION",
            "POST, DISMISSED_NO_ACTION",
            "COMMENT, DISMISSED_NO_ACTION",
            "POST, LEGACY_RESOLVED",
            "COMMENT, LEGACY_RESOLVED",
            "POST, LEGACY_DISMISSED",
            "COMMENT, LEGACY_DISMISSED"
    })
    void 상세_재조회는_저장된_처리_결과와_명시적_null을_반환한다(
            Target target,
            String scenario
    ) throws Exception {
        CsrfSession client = login(fixture.adminEmail());
        Long targetReportId = reportId(target);

        ContentReportStatus expectedStatus;
        ContentReportHandlingOutcome expectedOutcome;

        switch (scenario) {
            case "RECEIVED" -> {
                expectedStatus = ContentReportStatus.RECEIVED;
                expectedOutcome = null;
            }

            case "CONTENT_HIDDEN" -> {
                submit(
                        targetReportId,
                        client,
                        body(HANDLING_REASON)
                )
                        .andExpect(status().isOk());

                expectedStatus = ContentReportStatus.RESOLVED;
                expectedOutcome = ContentReportHandlingOutcome.CONTENT_HIDDEN;
            }

            case "CONTENT_ALREADY_HIDDEN" -> {
                prepareAlreadyHidden(target);

                submit(
                        targetReportId,
                        client,
                        body(HANDLING_REASON)
                )
                        .andExpect(status().isOk());

                expectedStatus = ContentReportStatus.RESOLVED;
                expectedOutcome =
                        ContentReportHandlingOutcome.CONTENT_ALREADY_HIDDEN;
            }

            case "RESOLVED_NO_ACTION",
                 "DISMISSED_NO_ACTION",
                 "LEGACY_RESOLVED",
                 "LEGACY_DISMISSED" -> {
                boolean dismissed = scenario.equals("DISMISSED_NO_ACTION")
                        || scenario.equals("LEGACY_DISMISSED");

                String action = dismissed ? "dismiss" : "resolve";

                mockMvc.perform(
                                post(
                                        "/api/v1/admin/reports/"
                                                + targetReportId
                                                + "/"
                                                + action
                                )
                                        .session(client.session())
                                        .header(
                                                client.headerName(),
                                                client.token()
                                        )
                                        .header(HttpHeaders.ORIGIN, ORIGIN)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body(HANDLING_REASON))
                        )
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.handlingOutcome")
                                .value("NO_CONTENT_ACTION"));

                expectedStatus = dismissed
                        ? ContentReportStatus.DISMISSED
                        : ContentReportStatus.RESOLVED;

                if (scenario.startsWith("LEGACY_")) {
                    /*
                     * 마이그레이션으로 보존된 과거 기록을 준비한다.
                     * 새 처리 요청에서 LEGACY_UNKNOWN을 지정하는 것은 아니다.
                     */
                    transactions.executeWithoutResult(transactionStatus -> {
                        int changedRows = jdbcTemplate.update(
                                """
                                UPDATE content_reports
                                   SET handling_outcome = ?
                                 WHERE id = ?
                                """,
                                ContentReportHandlingOutcome.LEGACY_UNKNOWN.name(),
                                targetReportId
                        );

                        assertThat(changedRows).isEqualTo(1);
                    });

                    expectedOutcome =
                            ContentReportHandlingOutcome.LEGACY_UNKNOWN;
                } else {
                    expectedOutcome =
                            ContentReportHandlingOutcome.NO_CONTENT_ACTION;
                }
            }

            default -> throw new IllegalArgumentException(scenario);
        }

        ContentReport stored = readReport(targetReportId);

        assertThat(stored.getStatus()).isEqualTo(expectedStatus);
        assertThat(stored.getHandlingOutcome()).isEqualTo(expectedOutcome);

        DatabaseSnapshot beforeRead = snapshot();

        MvcResult result = mockMvc.perform(
                        get("/api/v1/admin/reports/" + targetReportId)
                                .session(client.session())
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ORIGIN
                ))
                .andExpect(jsonPath("$.reportId").value(targetReportId))
                .andExpect(jsonPath("$.status").value(expectedStatus.name()))
                .andExpect(jsonPath("$.targetType").value(target.name()))
                .andReturn();

        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsByteArray()
        );

        // 값이 null이더라도 필드 자체는 반드시 존재해야 한다.
        assertThat(response.has("handlingOutcome")).isTrue();
        assertThat(response.has("handledByMemberId")).isTrue();
        assertThat(response.has("handledAt")).isTrue();
        assertThat(response.has("handlingReason")).isTrue();

        if (expectedStatus == ContentReportStatus.RECEIVED) {
            assertThat(response.path("handlingOutcome").isNull()).isTrue();
            assertThat(response.path("handledByMemberId").isNull()).isTrue();
            assertThat(response.path("handledAt").isNull()).isTrue();
            assertThat(response.path("handlingReason").isNull()).isTrue();
        } else {
            assertThat(response.path("handlingOutcome").asString())
                    .isEqualTo(expectedOutcome.name());

            assertThat(response.path("handledByMemberId").asLong())
                    .isEqualTo(stored.getHandledByMemberId());

            assertThat(
                    Instant.parse(response.path("handledAt").asString())
            )
                    .isEqualTo(stored.getHandledAt());

            assertThat(response.path("handlingReason").asString())
                    .isEqualTo(stored.getHandlingReason());
        }

        assertThat(response.path("contentSnapshot").asString())
                .isEqualTo(stored.getContentSnapshot());

        assertOriginalReportFields(stored, target);

        // 조회 과정에서 신고나 콘텐츠를 수정하지 않아야 한다.
        assertThat(snapshot()).isEqualTo(beforeRead);
    }

    @ParameterizedTest
    @CsvSource({
            "POST, false",
            "POST, true",
            "COMMENT, false",
            "COMMENT, true"
    })
    void 콘텐츠가_복구되어도_상세_조회는_과거_처리_결과를_유지한다(
            Target target,
            boolean alreadyHidden
    ) throws Exception {
        if (alreadyHidden) {
            prepareAlreadyHidden(target);
        }

        CsrfSession client = login(fixture.adminEmail());
        Long targetReportId = reportId(target);

        ContentReportHandlingOutcome expectedOutcome = alreadyHidden
                ? ContentReportHandlingOutcome.CONTENT_ALREADY_HIDDEN
                : ContentReportHandlingOutcome.CONTENT_HIDDEN;

        submit(
                targetReportId,
                client,
                body(HANDLING_REASON)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handlingOutcome")
                        .value(expectedOutcome.name()));

        ContentReport beforeRestore = readReport(targetReportId);

        Long originalHandler = beforeRestore.getHandledByMemberId();
        Instant originalHandledAt = beforeRestore.getHandledAt();
        String originalReason = beforeRestore.getHandlingReason();

        /*
         * 처리 이후 콘텐츠가 복구된 상황을 준비한다.
         * 복구 API 자체의 검증은 해당 기능의 테스트에서 담당한다.
         */
        transactions.executeWithoutResult(transactionStatus -> {
            boolean changed;

            if (target == Target.POST) {
                changed = entityManager.find(
                        Post.class,
                        fixture.postId()
                ).restore();
            } else {
                changed = entityManager.find(
                        Comment.class,
                        fixture.commentId()
                ).restore();
            }

            assertThat(changed).isTrue();
        });

        DatabaseSnapshot beforeRead = snapshot();

        MvcResult result = mockMvc.perform(
                        get("/api/v1/admin/reports/" + targetReportId)
                                .session(client.session())
                                .header(HttpHeaders.ORIGIN, ORIGIN)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.handlingOutcome")
                        .value(expectedOutcome.name()))
                .andReturn();

        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsByteArray()
        );

        JsonNode currentTarget = response.path("currentTarget");

        assertThat(currentTarget.isObject()).isTrue();
        assertThat(currentTarget.has("hiddenAt")).isTrue();
        assertThat(currentTarget.path("hiddenAt").isNull()).isTrue();

        // 현재 콘텐츠는 복구됐어도 당시 처리 결과와 처리 정보는 유지된다.
        assertThat(response.path("handledByMemberId").asLong())
                .isEqualTo(originalHandler);

        assertThat(
                Instant.parse(response.path("handledAt").asString())
        )
                .isEqualTo(originalHandledAt);

        assertThat(response.path("handlingReason").asString())
                .isEqualTo(originalReason);

        ContentReport afterRead = readReport(targetReportId);

        assertThat(afterRead.getHandlingOutcome())
                .isEqualTo(expectedOutcome);
        assertThat(afterRead.getHandledByMemberId())
                .isEqualTo(originalHandler);
        assertThat(afterRead.getHandledAt())
                .isEqualTo(originalHandledAt);
        assertThat(afterRead.getHandlingReason())
                .isEqualTo(originalReason);

        assertOriginalReportFields(afterRead, target);
        assertThat(snapshot()).isEqualTo(beforeRead);
    }

    private Member createMember(
            String email,
            String nickname,
            String passwordHash,
            MemberRole role
    ) {
        Member member = Member.createPending(
                email,
                passwordHash,
                nickname
        );

        member.activeAfterEmailVerification();

        ReflectionTestUtils.setField(member, "role", role);
        ReflectionTestUtils.setField(
                member,
                "credentialVersion",
                INITIAL_VERSION
        );

        entityManager.persist(member);

        return member;
    }

    private void prepareAlreadyHidden(Target target) {
        transactions.executeWithoutResult(status -> {
            ContentModerationLog log;

            if (target == Target.POST) {
                entityManager.find(
                        Post.class,
                        fixture.postId()
                ).hide(PREVIOUS_HIDDEN_AT);

                log = ContentModerationLog.forPost(
                        fixture.adminId(),
                        fixture.postId(),
                        ModerationAction.HIDE,
                        ModerationReasonType.OTHER,
                        PREVIOUS_REASON,
                        PREVIOUS_HIDDEN_AT
                );
            } else {
                entityManager.find(
                        Comment.class,
                        fixture.commentId()
                ).hide(PREVIOUS_HIDDEN_AT);

                log = ContentModerationLog.forComment(
                        fixture.adminId(),
                        fixture.commentId(),
                        ModerationAction.HIDE,
                        ModerationReasonType.OTHER,
                        PREVIOUS_REASON,
                        PREVIOUS_HIDDEN_AT
                );
            }

            entityManager.persist(log);
        });
    }

    private void assertContentAndLog(
            Target target,
            boolean alreadyHidden,
            Instant handledAt
    ) {
        transactions.executeWithoutResult(status -> {
            Post post = entityManager.find(
                    Post.class,
                    fixture.postId()
            );

            Comment comment = entityManager.find(
                    Comment.class,
                    fixture.commentId()
            );

            Instant expectedHiddenAt = alreadyHidden
                    ? PREVIOUS_HIDDEN_AT
                    : handledAt;

            if (target == Target.POST) {
                assertThat(post.getHiddenAt())
                        .isEqualTo(expectedHiddenAt);
                assertThat(comment.getHiddenAt()).isNull();
            } else {
                assertThat(comment.getHiddenAt())
                        .isEqualTo(expectedHiddenAt);
                assertThat(post.getHiddenAt()).isNull();
            }

            assertThat(post.getDeletedAt()).isNull();
            assertThat(comment.getDeletedAt()).isNull();

            List<ContentModerationLog> logs =
                    entityManager.createQuery(
                                    """
                                    SELECT log
                                    FROM ContentModerationLog log
                                    WHERE log.postId = :postId
                                       OR log.commentId = :commentId
                                    ORDER BY log.id
                                    """,
                                    ContentModerationLog.class
                            )
                            .setParameter("postId", fixture.postId())
                            .setParameter("commentId", fixture.commentId())
                            .getResultList();

            assertThat(logs).hasSize(1);

            ContentModerationLog log = logs.getFirst();

            assertThat(log.getActorMemberId())
                    .isEqualTo(fixture.adminId());
            assertThat(log.getAction())
                    .isEqualTo(ModerationAction.HIDE);
            assertThat(log.getReasonType())
                    .isEqualTo(ModerationReasonType.OTHER);
            assertThat(log.getReasonDetail()).isEqualTo(
                    alreadyHidden ? PREVIOUS_REASON : HANDLING_REASON
            );
            assertThat(log.getCreatedAt())
                    .isEqualTo(expectedHiddenAt);

            if (target == Target.POST) {
                assertThat(log.getPostId())
                        .isEqualTo(fixture.postId());
                assertThat(log.getCommentId()).isNull();
            } else {
                assertThat(log.getPostId()).isNull();
                assertThat(log.getCommentId())
                        .isEqualTo(fixture.commentId());
            }
        });
    }

    private void assertReceived(Long reportId) {
        ContentReport report = readReport(reportId);

        assertThat(report.getStatus())
                .isEqualTo(ContentReportStatus.RECEIVED);
        assertThat(report.getHandledByMemberId()).isNull();
        assertThat(report.getHandledAt()).isNull();
        assertThat(report.getHandlingReason()).isNull();
        assertThat(report.getHandlingOutcome()).isNull();

        Target target = reportId.equals(fixture.postReportId())
                ? Target.POST
                : Target.COMMENT;

        assertOriginalReportFields(report, target);
    }

    private void assertOriginalReportFields(
            ContentReport report,
            Target target
    ) {
        assertThat(report.getReporterMemberId())
                .isEqualTo(fixture.reporterId());
        assertThat(report.getReasonType())
                .isEqualTo(ContentReportReasonType.OTHER);
        assertThat(report.getReasonDetail())
                .isEqualTo(REPORT_REASON);
        assertThat(report.getCreatedAt())
                .isEqualTo(CREATED_AT);

        if (target == Target.POST) {
            assertThat(report.getPostId())
                    .isEqualTo(fixture.postId());
            assertThat(report.getCommentId()).isNull();
            assertThat(report.getTitleSnapshot())
                    .isEqualTo(POST_TITLE);
            assertThat(report.getContentSnapshot())
                    .isEqualTo(POST_CONTENT);
        } else {
            assertThat(report.getPostId()).isNull();
            assertThat(report.getCommentId())
                    .isEqualTo(fixture.commentId());
            assertThat(report.getTitleSnapshot()).isNull();
            assertThat(report.getContentSnapshot())
                    .isEqualTo(COMMENT_CONTENT);
        }
    }

    private ContentReport readReport(Long reportId) {
        return reportRepository.findById(reportId).orElseThrow();
    }

    private Long reportId(Target target) {
        return target == Target.POST
                ? fixture.postReportId()
                : fixture.commentReportId();
    }

    private Long otherReportId(Target target) {
        return target == Target.POST
                ? fixture.commentReportId()
                : fixture.postReportId();
    }

    private CsrfSession login(String email) throws Exception {
        CsrfSession beforeLogin = fetchCsrf(null);

        MvcResult result = mockMvc.perform(
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
                (MockHttpSession) result
                        .getRequest()
                        .getSession(false);

        assertThat(authenticatedSession).isNotNull();
        assertThat(authenticatedSession.isInvalid()).isFalse();

        // 로그인 성공 후 CSRF 토큰이 교체되므로 다시 조회한다.
        return fetchCsrf(authenticatedSession);
    }

    private CsrfSession fetchCsrf(
            MockHttpSession session
    ) throws Exception {
        MockHttpServletRequestBuilder builder =
                get("/api/v1/auth/csrf");

        if (session != null) {
            builder.session(session);
        }

        MvcResult result = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession actualSession =
                (MockHttpSession) result
                        .getRequest()
                        .getSession(false);

        assertThat(actualSession).isNotNull();
        assertThat(actualSession.isInvalid()).isFalse();

        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsByteArray()
        );

        String headerName = response.path("headerName").asString();
        String token = response.path("token").asString();

        assertThat(headerName).isEqualTo("X-CSRF-TOKEN");
        assertThat(token).isNotBlank();

        return new CsrfSession(
                actualSession,
                headerName,
                token
        );
    }

    private String body(String reason) {
        return objectMapper.writeValueAsString(
                Collections.singletonMap("handlingReason", reason)
        );
    }

    private MockHttpServletRequestBuilder request(
            Long reportId,
            String requestBody
    ) {
        return post(
                "/api/v1/admin/reports/"
                        + reportId
                        + "/hide-and-resolve"
        )
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody);
    }

    private ResultActions submit(
            Long reportId,
            CsrfSession client,
            String requestBody
    ) throws Exception {
        return mockMvc.perform(
                request(reportId, requestBody)
                        .session(client.session())
                        .header(client.headerName(), client.token())
                        .header(HttpHeaders.ORIGIN, ORIGIN)
        );
    }

    private DatabaseSnapshot snapshot() {
        // JPA 캐시가 아닌 실제 DB의 전체 행을 비교한다.
        return new DatabaseSnapshot(
                jdbcTemplate.queryForList(
                        """
                        SELECT *
                        FROM members
                        WHERE id IN (?, ?, ?)
                        ORDER BY id
                        """,
                        fixture.adminId(),
                        fixture.reporterId(),
                        fixture.authorId()
                ),
                jdbcTemplate.queryForList(
                        """
                        SELECT *
                        FROM community_posts
                        WHERE id = ?
                        """,
                        fixture.postId()
                ),
                jdbcTemplate.queryForList(
                        """
                        SELECT *
                        FROM community_comments
                        WHERE id = ?
                        """,
                        fixture.commentId()
                ),
                jdbcTemplate.queryForList(
                        """
                        SELECT *
                        FROM content_reports
                        WHERE post_id = ? OR comment_id = ?
                        ORDER BY id
                        """,
                        fixture.postId(),
                        fixture.commentId()
                ),
                jdbcTemplate.queryForList(
                        """
                        SELECT *
                        FROM admin_content_moderation_logs
                        WHERE post_id = ? OR comment_id = ?
                        ORDER BY id
                        """,
                        fixture.postId(),
                        fixture.commentId()
                )
        );
    }

    private enum Target {
        POST,
        COMMENT
    }

    private record CsrfSession(
            MockHttpSession session,
            String headerName,
            String token
    ) {
    }

    private record Fixture(
            Long adminId,
            String adminEmail,
            Long reporterId,
            String reporterEmail,
            Long authorId,
            Long postId,
            Long commentId,
            Long postReportId,
            Long commentReportId
    ) {
    }

    private record DatabaseSnapshot(
            List<Map<String, Object>> members,
            List<Map<String, Object>> posts,
            List<Map<String, Object>> comments,
            List<Map<String, Object>> reports,
            List<Map<String, Object>> logs
    ) {
    }
}