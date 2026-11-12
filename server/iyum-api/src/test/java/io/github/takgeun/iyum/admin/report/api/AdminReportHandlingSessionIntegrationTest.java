package io.github.takgeun.iyum.admin.report.api;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.report.domain.ContentReport;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import io.github.takgeun.iyum.community.report.infrastructure.ContentReportRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
class AdminReportHandlingSessionIntegrationTest {

    private static final String ORIGIN =
            "http://127.0.0.1:8443";

    private static final String PASSWORD =
            "ReportHandling123!";

    private static final long INITIAL_VERSION = 3L;

    private static final Instant CREATED_AT =
            Instant.parse("2026-11-12T10:00:00Z");

    private static final String POST_TITLE =
            "신고 처리 세션 테스트";

    private static final String POST_CONTENT =
            " \n게시글 첫 줄\n두 번째 줄\t ";

    private static final String COMMENT_CONTENT =
            " \n댓글 첫 줄\n두 번째 줄\t ";

    private static final String REPORT_REASON =
            "접수 당시 신고 사유";

    private static final String RAW_HANDLING_REASON =
            " \t 검토를 완료했습니다. \n";

    private static final String HANDLING_REASON =
            "검토를 완료했습니다.";

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
                            "handling-admin-" + suffix + "@example.com",
                            "관리" + suffix,
                            passwordHash,
                            MemberRole.ADMIN
                    );

                    Member reporter = createMember(
                            "handling-reporter-" + suffix + "@example.com",
                            "신고" + suffix,
                            passwordHash,
                            MemberRole.USER
                    );

                    Member author = createMember(
                            "handling-author-" + suffix + "@example.com",
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

    @ParameterizedTest
    @CsvSource({
            "POST, resolve",
            "POST, dismiss",
            "COMMENT, resolve",
            "COMMENT, dismiss"
    })
    void 실제_관리자_세션으로_신고를_처리하고_DB에_저장한다(
            String target,
            String action
    ) throws Exception {
        CsrfSession client = login(fixture.adminEmail());
        Long reportId = reportId(target);

        // 클라이언트가 추가 필드로 처리 주체·시각·상태를
        // 지정하려 해도 서버의 결정에 영향을 주지 않아야 한다.
        String requestBody = objectMapper.writeValueAsString(
                Map.of(
                        "handlingReason", RAW_HANDLING_REASON,
                        "handledByMemberId", fixture.reporterId(),
                        "credentialVersion", 999,
                        "handledAt", "2000-01-01T00:00:00Z",
                        "status", "RECEIVED"
                )
        );

        Instant before = Instant.now();

        MvcResult result = submit(
                action,
                reportId,
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
                .andExpect(jsonPath("$.reportId").value(reportId))
                .andExpect(jsonPath("$.status")
                        .value(expectedStatus(action).name()))
                .andExpect(jsonPath("$.handledByMemberId")
                        .value(fixture.adminId()))
                .andExpect(jsonPath("$.handlingReason")
                        .value(HANDLING_REASON))
                .andExpect(jsonPath("$.*", hasSize(5)))
                .andReturn();

        Instant after = Instant.now();

        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsByteArray()
        );

        Instant responseHandledAt = Instant.parse(
                response.path("handledAt").asString()
        );

        assertThat(responseHandledAt).isBetween(before, after);

        ContentReport found = readReport(reportId);

        assertThat(found.getStatus())
                .isEqualTo(expectedStatus(action));
        assertThat(found.getHandledByMemberId())
                .isEqualTo(fixture.adminId());
        assertThat(found.getHandlingReason())
                .isEqualTo(HANDLING_REASON);
        assertThat(found.getHandledAt()).isNotNull();

        // PostgreSQL의 마이크로초 정밀도에 따른 반올림을 허용한다.
        assertThat(found.getHandledAt()).isBetween(
                responseHandledAt.minusNanos(1_000),
                responseHandledAt.plusNanos(1_000)
        );

        assertOriginalReportFields(found);

        Long otherReportId = target.equals("POST")
                ? fixture.commentReportId()
                : fixture.postReportId();

        assertReceived(otherReportId);
        assertContentAndMembersUnchanged();

        assertThat(client.session().isInvalid()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "resolve, resolve",
            "resolve, dismiss",
            "dismiss, resolve",
            "dismiss, dismiss"
    })
    void 같은_신고를_다시_처리하면_409이며_기존_정보를_보존한다(
            String firstAction,
            String secondAction
    ) throws Exception {
        CsrfSession client = login(fixture.adminEmail());

        submit(
                firstAction,
                fixture.postReportId(),
                client,
                body("최초 처리 사유")
        )
                .andExpect(status().isOk());

        ContentReport beforeRetry =
                readReport(fixture.postReportId());

        ContentReportStatus originalStatus = beforeRetry.getStatus();
        Long originalHandler = beforeRetry.getHandledByMemberId();
        Instant originalHandledAt = beforeRetry.getHandledAt();
        String originalReason = beforeRetry.getHandlingReason();

        submit(
                secondAction,
                fixture.postReportId(),
                client,
                body("덮어쓰려는 사유")
        )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("REPORT_ALREADY_HANDLED"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        ContentReport found = readReport(fixture.postReportId());

        assertThat(found.getStatus()).isEqualTo(originalStatus);
        assertThat(found.getHandledByMemberId())
                .isEqualTo(originalHandler);
        assertThat(found.getHandledAt()).isEqualTo(originalHandledAt);
        assertThat(found.getHandlingReason()).isEqualTo(originalReason);

        assertOriginalReportFields(found);
    }

    @ParameterizedTest
    @CsvSource({
            "resolve, MISSING",
            "dismiss, MISSING",
            "resolve, NULL",
            "dismiss, NULL",
            "resolve, BLANK",
            "dismiss, BLANK",
            "resolve, TOO_LONG",
            "dismiss, TOO_LONG"
    })
    void 잘못된_처리_사유는_400이며_DB를_변경하지_않는다(
            String action,
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

        submit(
                action,
                fixture.postReportId(),
                client,
                requestBody
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_HANDLING_REASON"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        assertReceived(fixture.postReportId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"resolve", "dismiss"})
    void 보조_평면_문자_500개인_사유를_실제_DB에_저장한다(
            String action
    ) throws Exception {
        CsrfSession client = login(fixture.adminEmail());

        String reason = "\uD83D\uDE00".repeat(500);

        submit(
                action,
                fixture.postReportId(),
                client,
                body("\u3000" + reason + "\u00A0")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handlingReason").value(reason));

        ContentReport found = readReport(fixture.postReportId());

        assertThat(found.getHandlingReason()).isEqualTo(reason);
        assertOriginalReportFields(found);
    }

    @ParameterizedTest
    @ValueSource(strings = {"resolve", "dismiss"})
    void 일반_회원의_실제_로그인_세션은_403이다(
            String action
    ) throws Exception {
        CsrfSession client = login(fixture.reporterEmail());

        submit(
                action,
                fixture.postReportId(),
                client,
                body(HANDLING_REASON)
        )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertReceived(fixture.postReportId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"resolve", "dismiss"})
    void 비로그인은_실제_CSRF_토큰이_있어도_401이다(
            String action
    ) throws Exception {
        CsrfSession anonymous = fetchCsrf(null);

        submit(
                action,
                fixture.postReportId(),
                anonymous,
                body(HANDLING_REASON)
        )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        assertReceived(fixture.postReportId());
    }

    @ParameterizedTest
    @CsvSource({
            "resolve, false",
            "resolve, true",
            "dismiss, false",
            "dismiss, true"
    })
    void 실제_관리자_세션도_CSRF가_없거나_틀리면_403이다(
            String action,
            boolean invalidToken
    ) throws Exception {
        CsrfSession client = login(fixture.adminEmail());

        MockHttpServletRequestBuilder builder = request(
                action,
                fixture.postReportId(),
                body(HANDLING_REASON)
        )
                .session(client.session())
                .header(HttpHeaders.ORIGIN, ORIGIN);

        if (invalidToken) {
            builder.header(client.headerName(), "invalid-csrf-token");
        }

        mockMvc.perform(builder)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        assertReceived(fixture.postReportId());
    }

    @ParameterizedTest
    @CsvSource({
            "resolve, STATUS",
            "dismiss, STATUS",
            "resolve, VERSION",
            "dismiss, VERSION",
            "resolve, ROLE",
            "dismiss, ROLE"
    })
    void 로그인_후_관리자_정보가_바뀌면_기존_세션을_차단한다(
            String action,
            String change
    ) throws Exception {
        CsrfSession oldClient = login(fixture.adminEmail());

        Member before = memberRepository
                .findById(fixture.adminId())
                .orElseThrow();

        long originalVersion = before.getCredentialVersion();

        assertThat(before.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(before.getRole()).isEqualTo(MemberRole.ADMIN);
        assertThat(oldClient.session().isInvalid()).isFalse();

        transactions.executeWithoutResult(status -> {
            Member admin = memberRepository
                    .findById(fixture.adminId())
                    .orElseThrow();

            switch (change) {
                case "STATUS" -> {
                    /*
                     * 세션 필터의 상태 검증을 위한 테스트 데이터 준비다.
                     * 관리자 정지 기능을 허용하거나 구현하는 것이 아니다.
                     *
                     * 상태만 변경하고 권한과 인증 버전은 유지해,
                     * 비활성 상태 자체로 세션이 차단되는지 검증한다.
                     */
                    ReflectionTestUtils.setField(
                            admin,
                            "status",
                            MemberStatus.SUSPENDED
                    );
                }

                case "VERSION" -> {
                    /*
                     * 인증 버전만 달라진 상황을 준비한다.
                     * 비밀번호 재설정 API 자체의 검증은 별도 테스트에서 한다.
                     */
                    admin.resetPassword(admin.getPasswordHash());
                }

                case "ROLE" -> {
                    // 현재 권한과 로그인 당시 권한이 달라진 상황을 준비한다.
                    ReflectionTestUtils.setField(
                            admin,
                            "role",
                            MemberRole.USER
                    );
                }

                default -> throw new IllegalArgumentException(
                        "지원하지 않는 변경: " + change
                );
            }
        });

        // 준비한 변경이 실제로 커밋됐는지 확인한다.
        Member changed = memberRepository
                .findById(fixture.adminId())
                .orElseThrow();

        switch (change) {
            case "STATUS" -> {
                assertThat(changed.getStatus())
                        .isEqualTo(MemberStatus.SUSPENDED);
                assertThat(changed.getRole())
                        .isEqualTo(MemberRole.ADMIN);
                assertThat(changed.getCredentialVersion())
                        .isEqualTo(originalVersion);
            }

            case "VERSION" -> {
                assertThat(changed.getStatus())
                        .isEqualTo(MemberStatus.ACTIVE);
                assertThat(changed.getRole())
                        .isEqualTo(MemberRole.ADMIN);
                assertThat(changed.getCredentialVersion())
                        .isEqualTo(originalVersion + 1);
            }

            case "ROLE" -> {
                assertThat(changed.getStatus())
                        .isEqualTo(MemberStatus.ACTIVE);
                assertThat(changed.getRole())
                        .isEqualTo(MemberRole.USER);
                assertThat(changed.getCredentialVersion())
                        .isEqualTo(originalVersion);
            }

            default -> throw new IllegalArgumentException(
                    "지원하지 않는 변경: " + change
            );
        }

        // 아직 HTTP 요청을 보내지 않았으므로 세션 자체는 남아 있다.
        assertThat(oldClient.session().isInvalid()).isFalse();

        // 변경 전에 확보한 세션과 정상 CSRF 토큰으로 바로 요청한다.
        // 중간에 CSRF 조회 요청을 보내지 않는다.
        submit(
                action,
                fixture.postReportId(),
                oldClient,
                body(HANDLING_REASON)
        )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        // 요청 과정에서 기존 세션이 무효화되어야 한다.
        assertThat(oldClient.session().isInvalid()).isTrue();

        // 신고 처리 정보는 변경되지 않아야 한다.
        assertReceived(fixture.postReportId());
        assertReceived(fixture.commentReportId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"resolve", "dismiss"})
    void 존재하지_않는_신고는_404다(
            String action
    ) throws Exception {
        CsrfSession client = login(fixture.adminEmail());

        submit(
                action,
                Long.MAX_VALUE,
                client,
                body(HANDLING_REASON)
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("REPORT_NOT_FOUND"));

        assertReceived(fixture.postReportId());
        assertReceived(fixture.commentReportId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"resolve", "dismiss"})
    void 허용되지_않은_Origin은_정상_세션과_토큰이_있어도_거부한다(
            String action
    ) throws Exception {
        CsrfSession client = login(fixture.adminEmail());

        // request()에는 Origin 기본값이 없으므로 헤더가 중복되지 않는다.
        mockMvc.perform(
                        request(
                                action,
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

        assertReceived(fixture.postReportId());
    }

    @ParameterizedTest
    @CsvSource({
            "POST, RECEIVED",
            "POST, RESOLVED",
            "POST, DISMISSED",
            "COMMENT, RECEIVED",
            "COMMENT, RESOLVED",
            "COMMENT, DISMISSED"
    })
    void 상세를_다시_조회하면_저장된_처리_정보를_반환한다(
            String target,
            ContentReportStatus expectedStatus
    ) throws Exception {
        CsrfSession client = login(fixture.adminEmail());
        Long targetReportId = reportId(target);

        if (expectedStatus != ContentReportStatus.RECEIVED) {
            String action = expectedStatus == ContentReportStatus.RESOLVED
                    ? "resolve"
                    : "dismiss";

            submit(
                    action,
                    targetReportId,
                    client,
                    body(RAW_HANDLING_REASON)
            )
                    .andExpect(status().isOk());
        }

        ContentReport stored = readReport(targetReportId);

        // 상세 GET에는 CSRF 헤더를 보내지 않는다.
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
                .andExpect(jsonPath("$.reportId").value(targetReportId))
                .andExpect(jsonPath("$.status").value(expectedStatus.name()))
                .andExpect(jsonPath("$.targetType").value(target))
                .andExpect(jsonPath("$.reporterMemberId")
                        .value(fixture.reporterId()))
                .andExpect(jsonPath("$.reasonDetail").value(REPORT_REASON))
                .andExpect(jsonPath("$.createdAt")
                        .value(CREATED_AT.toString()))
                .andReturn();

        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsByteArray()
        );

        // null인 필드도 응답에서 생략되면 안 된다.
        assertThat(response.has("handledByMemberId")).isTrue();
        assertThat(response.has("handledAt")).isTrue();
        assertThat(response.has("handlingReason")).isTrue();

        if (expectedStatus == ContentReportStatus.RECEIVED) {
            assertThat(response.path("handledByMemberId").isNull()).isTrue();
            assertThat(response.path("handledAt").isNull()).isTrue();
            assertThat(response.path("handlingReason").isNull()).isTrue();

            assertReceived(targetReportId);
        } else {
            assertThat(stored.getStatus()).isEqualTo(expectedStatus);
            assertThat(stored.getHandledByMemberId())
                    .isEqualTo(fixture.adminId());
            assertThat(stored.getHandledAt()).isNotNull();
            assertThat(stored.getHandlingReason())
                    .isEqualTo(HANDLING_REASON);

            assertThat(response.path("handledByMemberId").asLong())
                    .isEqualTo(stored.getHandledByMemberId());

            // POST応答ではなく、DBに保存された時刻と比較する。
            assertThat(Instant.parse(response.path("handledAt").asString()))
                    .isEqualTo(stored.getHandledAt());

            assertThat(response.path("handlingReason").asString())
                    .isEqualTo(stored.getHandlingReason());
        }

        assertThat(response.path("contentSnapshot").asString())
                .isEqualTo(stored.getContentSnapshot());

        assertOriginalReportFields(stored);
        assertContentAndMembersUnchanged();
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

        return new CsrfSession(actualSession, headerName, token);
    }

    private String body(String reason) {
        return objectMapper.writeValueAsString(
                Collections.singletonMap("handlingReason", reason)
        );
    }

    private MockHttpServletRequestBuilder request(
            String action,
            Long reportId,
            String requestBody
    ) {
        return post(
                "/api/v1/admin/reports/" + reportId + "/" + action
        )
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody);
    }

    private ResultActions submit(
            String action,
            Long reportId,
            CsrfSession client,
            String requestBody
    ) throws Exception {
        return mockMvc.perform(
                request(action, reportId, requestBody)
                        .session(client.session())
                        .header(client.headerName(), client.token())
                        .header(HttpHeaders.ORIGIN, ORIGIN)
        );
    }

    private Long reportId(String target) {
        return switch (target) {
            case "POST" -> fixture.postReportId();
            case "COMMENT" -> fixture.commentReportId();
            default -> throw new IllegalArgumentException(target);
        };
    }

    private ContentReportStatus expectedStatus(String action) {
        return switch (action) {
            case "resolve" -> ContentReportStatus.RESOLVED;
            case "dismiss" -> ContentReportStatus.DISMISSED;
            default -> throw new IllegalArgumentException(action);
        };
    }

    private ContentReport readReport(Long reportId) {
        return reportRepository.findById(reportId).orElseThrow();
    }

    private void assertReceived(Long reportId) {
        ContentReport report = readReport(reportId);

        assertThat(report.getStatus())
                .isEqualTo(ContentReportStatus.RECEIVED);
        assertThat(report.getHandledByMemberId()).isNull();
        assertThat(report.getHandledAt()).isNull();
        assertThat(report.getHandlingReason()).isNull();

        assertOriginalReportFields(report);
    }

    private void assertOriginalReportFields(ContentReport report) {
        assertThat(report.getReporterMemberId())
                .isEqualTo(fixture.reporterId());
        assertThat(report.getReasonType())
                .isEqualTo(ContentReportReasonType.OTHER);
        assertThat(report.getReasonDetail()).isEqualTo(REPORT_REASON);
        assertThat(report.getCreatedAt()).isEqualTo(CREATED_AT);

        if (report.getId().equals(fixture.postReportId())) {
            assertThat(report.getPostId()).isEqualTo(fixture.postId());
            assertThat(report.getCommentId()).isNull();
            assertThat(report.getTitleSnapshot()).isEqualTo(POST_TITLE);
            assertThat(report.getContentSnapshot()).isEqualTo(POST_CONTENT);
        } else {
            assertThat(report.getId()).isEqualTo(fixture.commentReportId());
            assertThat(report.getPostId()).isNull();
            assertThat(report.getCommentId())
                    .isEqualTo(fixture.commentId());
            assertThat(report.getTitleSnapshot()).isNull();
            assertThat(report.getContentSnapshot())
                    .isEqualTo(COMMENT_CONTENT);
        }
    }

    private void assertContentAndMembersUnchanged() {
        transactions.executeWithoutResult(status -> {
            Post post = entityManager.find(
                    Post.class,
                    fixture.postId()
            );

            Comment comment = entityManager.find(
                    Comment.class,
                    fixture.commentId()
            );

            assertThat(post.getHiddenAt()).isNull();
            assertThat(post.getDeletedAt()).isNull();
            assertThat(comment.getHiddenAt()).isNull();
            assertThat(comment.getDeletedAt()).isNull();

            for (Long memberId : new Long[]{
                    fixture.adminId(),
                    fixture.reporterId(),
                    fixture.authorId()
            }) {
                Member member = entityManager.find(
                        Member.class,
                        memberId
                );

                assertThat(member.getStatus())
                        .isEqualTo(MemberStatus.ACTIVE);
                assertThat(member.getCredentialVersion())
                        .isEqualTo(INITIAL_VERSION);
            }
        });
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
}