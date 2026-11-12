package io.github.takgeun.iyum.community.report.api;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.community.report.domain.ContentReport;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import io.github.takgeun.iyum.community.report.infrastructure.ContentReportRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

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
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
@AutoConfigureMockMvc(addFilters = true)
@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
class ContentReportCreateSessionIntegrationTest {

    private static final String PASSWORD =
            "ReportSession123!";

    private static final long REPORTER_VERSION = 3L;

    private static final String POST_TITLE =
            "신고 세션 통합 테스트 게시글";

    private static final String POST_CONTENT =
            "게시글 본문 첫 줄\n게시글 본문 둘째 줄";

    private static final String COMMENT_CONTENT =
            "댓글 본문 첫 줄\n댓글 본문 둘째 줄";

    private static final String REASON_DETAIL =
            "  반복적인 광고 게시를 확인했습니다.  ";

    private static final String NORMALIZED_REASON_DETAIL =
            "반복적인 광고 게시를 확인했습니다.";

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
    ContentReportRepository reportRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;

    private Long reporterId;
    private String reporterEmail;

    private Long authorId;
    private String authorEmail;

    private Long postId;
    private Long commentId;

    /*
     * 클래스와 테스트 메서드에 @Transactional을 붙이지 않는다.
     *
     * 준비한 데이터와 회원 상태 변경을 실제로 커밋한 뒤,
     * HTTP 요청의 서비스·보안 필터가 DB에서 다시 확인하도록 한다.
     *
     * 테스트마다 고유한 회원과 콘텐츠를 생성하고,
     * 해당 대상의 신고 건수만 확인한다.
     * 전체 테이블을 삭제하지 않는다.
     */
    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);

        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);

        String passwordHash = passwordEncoder.encode(PASSWORD);

        transaction.executeWithoutResult(status -> {
            Member reporter = saveActiveMember(
                    "report-session-" + suffix + "@example.com",
                    "신고자" + suffix,
                    passwordHash
            );

            reporterId = reporter.getId();
            reporterEmail = reporter.getEmail();

            Member author = saveActiveMember(
                    "report-author-" + suffix + "@example.com",
                    "작성자" + suffix,
                    passwordHash
            );

            authorId = author.getId();
            authorEmail = author.getEmail();

            Post post = postRepository.saveAndFlush(
                    Post.create(
                            author,
                            PostType.GENERAL,
                            PostTopic.DAILY,
                            POST_TITLE,
                            POST_CONTENT
                    )
            );

            postId = post.getId();

            Comment comment = commentRepository.saveAndFlush(
                    Comment.create(
                            post,
                            author,
                            COMMENT_CONTENT
                    )
            );

            commentId = comment.getId();
        });

        // 로그인 전에 인증 버전을 설정하는 테스트 준비용 SQL이다.
        int changedRows = jdbcTemplate.update("""
                UPDATE members
                   SET credential_version = ?
                 WHERE id = ?
                """,
                REPORTER_VERSION,
                reporterId
        );

        assertThat(changedRows).isEqualTo(1);
        assertThat(reportCount("POST")).isZero();
        assertThat(reportCount("COMMENT")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "COMMENT"})
    void 실제_세션으로_신고를_접수하고_스냅샷을_저장한다(
            String target
    ) throws Exception {
        CsrfSession client = login(reporterEmail);

        ContentReport stored = expectCreated(
                mockMvc.perform(
                        reportRequest(target, client, validBody())
                                // 신고자와 인증 버전은 세션의 값을 사용한다.
                                .param(
                                        "reporterMemberId",
                                        authorId.toString()
                                )
                                .param("credentialVersion", "999")
                )
        );

        assertThat(stored.getReporterMemberId())
                .isEqualTo(reporterId);
        assertThat(stored.getReasonType())
                .isEqualTo(ContentReportReasonType.SPAM_ADVERTISING);
        assertThat(stored.getReasonDetail())
                .isEqualTo(NORMALIZED_REASON_DETAIL);

        assertTargetSnapshot(stored, target);

        assertThat(reportCount(target)).isEqualTo(1L);

        // 신고 접수만으로 콘텐츠를 숨기거나 작성자를 정지하지 않는다.
        assertContentAndMembersUnchanged();

        mockMvc.perform(
                        get("/api/v1/auth/me")
                                .session(client.session())
                )
                .andExpect(status().isOk());

        assertThat(client.session().isInvalid()).isFalse();
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
    void 기존_신고_상태와_관계없이_재신고는_409다(
            String target,
            ContentReportStatus existingStatus
    ) throws Exception {
        CsrfSession client = login(reporterEmail);

        ContentReport first = expectCreated(
                submit(target, client, validBody())
        );

        /*
         * 처리 완료·기각된 기존 기록을 준비한다.
         * 관리자 신고 처리 API를 구현한 것으로 가정하는 코드는 아니다.
         */
        int changedRows = jdbcTemplate.update("""
                UPDATE content_reports
                   SET status = ?
                 WHERE id = ?
                """,
                existingStatus.name(),
                first.getId()
        );

        assertThat(changedRows).isEqualTo(1);

        submit(
                target,
                client,
                body("OTHER", "다른 사유로 다시 신고")
        )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("REPORT_ALREADY_EXISTS"));

        assertThat(reportCount(target)).isEqualTo(1L);

        ContentReport after = reportRepository
                .findById(first.getId())
                .orElseThrow();

        assertThat(after.getStatus()).isEqualTo(existingStatus);
        assertThat(after.getReporterMemberId())
                .isEqualTo(first.getReporterMemberId());
        assertThat(after.getReasonType())
                .isEqualTo(first.getReasonType());
        assertThat(after.getReasonDetail())
                .isEqualTo(first.getReasonDetail());
        assertThat(after.getTitleSnapshot())
                .isEqualTo(first.getTitleSnapshot());
        assertThat(after.getContentSnapshot())
                .isEqualTo(first.getContentSnapshot());
        assertThat(after.getCreatedAt())
                .isEqualTo(first.getCreatedAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "COMMENT"})
    void 자신의_콘텐츠는_신고할_수_없다(
            String target
    ) throws Exception {
        CsrfSession authorClient = login(authorEmail);

        submit(target, authorClient, validBody())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("SELF_REPORT_NOT_ALLOWED"));

        assertThat(reportCount(target)).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "POST, POST_HIDDEN, POST_NOT_FOUND",
            "POST, POST_DELETED, POST_NOT_FOUND",
            "COMMENT, POST_HIDDEN, POST_NOT_FOUND",
            "COMMENT, POST_DELETED, POST_NOT_FOUND",
            "COMMENT, COMMENT_HIDDEN, COMMENT_NOT_FOUND",
            "COMMENT, COMMENT_DELETED, COMMENT_NOT_FOUND"
    })
    void 숨김이나_삭제된_대상은_신고를_저장하지_않는다(
            String target,
            String targetState,
            String expectedCode
    ) throws Exception {
        CsrfSession client = login(reporterEmail);

        transaction.executeWithoutResult(status -> {
            switch (targetState) {
                case "POST_HIDDEN" ->
                        postRepository.findById(postId)
                                .orElseThrow()
                                .hide(Instant.now());

                case "POST_DELETED" ->
                        postRepository.findById(postId)
                                .orElseThrow()
                                .delete(Instant.now());

                case "COMMENT_HIDDEN" ->
                        commentRepository.findById(commentId)
                                .orElseThrow()
                                .hide(Instant.now());

                case "COMMENT_DELETED" ->
                        commentRepository.findById(commentId)
                                .orElseThrow()
                                .delete(Instant.now());

                default ->
                        throw new IllegalArgumentException(
                                "지원하지 않는 테스트 상태: " + targetState
                        );
            }
        });

        submit(target, client, validBody())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(expectedCode));

        assertThat(reportCount(target)).isZero();
    }

    @Test
    void 댓글의_부모_게시글이_다르면_404다()
            throws Exception {
        CsrfSession client = login(reporterEmail);

        Long otherPostId = transaction.execute(status -> {
            Member author = memberRepository.findById(authorId)
                    .orElseThrow();

            return postRepository.saveAndFlush(
                    Post.create(
                            author,
                            PostType.GENERAL,
                            PostTopic.DAILY,
                            "다른 게시글",
                            "다른 게시글 본문"
                    )
            ).getId();
        });

        mockMvc.perform(
                        post(
                                "/api/v1/community/posts/{postId}"
                                        + "/comments/{commentId}/reports",
                                otherPostId,
                                commentId
                        )
                                .session(client.session())
                                .header(
                                        client.headerName(),
                                        client.token()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validBody())
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("COMMENT_NOT_FOUND"));

        assertThat(reportCount("COMMENT")).isZero();
    }

    @ParameterizedTest
    @MethodSource("invalidReasons")
    void 실제_사유_검증에_실패하면_신고를_저장하지_않는다(
            String target,
            String reasonType,
            String reasonDetail
    ) throws Exception {
        CsrfSession client = login(reporterEmail);

        submit(
                target,
                client,
                body(reasonType, reasonDetail)
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REASON"));

        assertThat(reportCount(target)).isZero();
    }

    static Stream<Arguments> invalidReasons() {
        return Stream.of("POST", "COMMENT")
                .flatMap(target -> Stream.of(
                        Arguments.of(target, null, null),
                        Arguments.of(target, "OTHER", null),
                        Arguments.of(target, "OTHER", " \t\n "),
                        Arguments.of(
                                target,
                                "OTHER",
                                "😀".repeat(501)
                        ),
                        Arguments.of(
                                target,
                                "SPAM_ADVERTISING",
                                "😀".repeat(501)
                        )
                ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "COMMENT"})
    void 상세_사유는_앞뒤_공백_제거_후_500_코드_포인트까지_허용한다(
            String target
    ) throws Exception {
        CsrfSession client = login(reporterEmail);

        String detail = "😀".repeat(500);

        ContentReport stored = expectCreated(
                submit(
                        target,
                        client,
                        body("OTHER", " \t" + detail + "\n ")
                )
        );

        assertThat(stored.getReasonType())
                .isEqualTo(ContentReportReasonType.OTHER);
        assertThat(stored.getReasonDetail()).isEqualTo(detail);
        assertThat(reportCount(target)).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "COMMENT"})
    void 비로그인_세션은_실제_CSRF가_있어도_401이다(
            String target
    ) throws Exception {
        CsrfSession anonymous = fetchCsrf(null);

        submit(target, anonymous, validBody())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        assertThat(reportCount(target)).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "POST, false",
            "POST, true",
            "COMMENT, false",
            "COMMENT, true"
    })
    void 로그인해도_CSRF가_없거나_틀리면_403이다(
            String target,
            boolean sendInvalidToken
    ) throws Exception {
        CsrfSession client = login(reporterEmail);

        MockHttpServletRequestBuilder request =
                post(path(target))
                        .session(client.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody());

        if (sendInvalidToken) {
            request.header(
                    client.headerName(),
                    "invalid-csrf-token"
            );
        }

        mockMvc.perform(request)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        assertThat(reportCount(target)).isZero();

        // 올바른 토큰을 사용하면 같은 로그인 세션으로 접수할 수 있다.
        expectCreated(submit(target, client, validBody()));
        assertThat(reportCount(target)).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "COMMENT"})
    void 같은_회원이라도_다른_세션의_CSRF는_사용할_수_없다(
            String target
    ) throws Exception {
        CsrfSession first = login(reporterEmail);
        CsrfSession second = login(reporterEmail);

        assertThat(first.session().getId())
                .isNotEqualTo(second.session().getId());

        mockMvc.perform(
                        post(path(target))
                                .session(first.session())
                                .header(
                                        second.headerName(),
                                        second.token()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validBody())
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("CSRF_TOKEN_INVALID"));

        assertThat(reportCount(target)).isZero();

        expectCreated(submit(target, first, validBody()));
        assertThat(reportCount(target)).isEqualTo(1L);
    }

    @ParameterizedTest
    @CsvSource({
            "POST, SUSPEND",
            "COMMENT, SUSPEND",
            "POST, VERSION",
            "COMMENT, VERSION"
    })
    void 로그인_후_정지되거나_인증_버전이_바뀌면_신고가_차단된다(
            String target,
            String change
    ) throws Exception {
        CsrfSession oldClient = login(reporterEmail);

        transaction.executeWithoutResult(status -> {
            Member member = memberRepository.findById(reporterId)
                    .orElseThrow();

            switch (change) {
                case "SUSPEND" -> member.suspend();

                /*
                 * 인증 버전 변경 상황을 준비한다.
                 * 비밀번호 API 자체의 동작은 해당 기능의 테스트에서 검증한다.
                 */
                case "VERSION" ->
                        member.resetPassword(member.getPasswordHash());

                default ->
                        throw new IllegalArgumentException(
                                "지원하지 않는 변경: " + change
                        );
            }
        });

        assertThat(
                memberRepository.findById(reporterId)
                        .orElseThrow()
                        .getCredentialVersion()
        ).isEqualTo(REPORTER_VERSION + 1);

        /*
         * 여기서 CSRF를 다시 조회하지 않는다.
         * 변경 전에 확보한 세션과 정상 토큰으로 바로 신고를 요청한다.
         */
        assertThat(oldClient.session().isInvalid()).isFalse();

        submit(target, oldClient, validBody())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        assertThat(oldClient.session().isInvalid()).isTrue();
        assertThat(reportCount(target)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "COMMENT"})
    void 정지_해제_후에도_기존_세션은_차단하고_새_로그인은_허용한다(
            String target
    ) throws Exception {
        CsrfSession oldClient = login(reporterEmail);

        transaction.executeWithoutResult(status -> {
            Member member = memberRepository.findById(reporterId)
                    .orElseThrow();

            member.suspend();
        });

        // 정지 중 oldClient로 HTTP 요청을 보내지 않는다.
        transaction.executeWithoutResult(status -> {
            Member member = memberRepository.findById(reporterId)
                    .orElseThrow();

            member.liftSuspension();
        });

        Member restored = memberRepository.findById(reporterId)
                .orElseThrow();

        assertThat(restored.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(restored.getCredentialVersion())
                .isEqualTo(REPORTER_VERSION + 2);
        assertThat(oldClient.session().isInvalid()).isFalse();

        submit(target, oldClient, validBody())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        assertThat(oldClient.session().isInvalid()).isTrue();
        assertThat(reportCount(target)).isZero();

        CsrfSession newClient = login(reporterEmail);

        expectCreated(submit(target, newClient, validBody()));

        assertThat(newClient.session().isInvalid()).isFalse();
        assertThat(reportCount(target)).isEqualTo(1L);
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
        assertThat(authenticatedSession.isInvalid()).isFalse();

        // 로그인 성공 시 기존 CSRF 토큰이 폐기되므로 다시 발급받는다.
        return fetchCsrf(authenticatedSession);
    }

    private CsrfSession fetchCsrf(
            MockHttpSession session
    ) throws Exception {
        MockHttpServletRequestBuilder request =
                get("/api/v1/auth/csrf");

        if (session != null) {
            request.session(session);
        }

        MvcResult result = mockMvc.perform(request)
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

    private String path(String target) {
        return switch (target) {
            case "POST" ->
                    "/api/v1/community/posts/" + postId + "/reports";

            case "COMMENT" ->
                    "/api/v1/community/posts/" + postId
                            + "/comments/" + commentId + "/reports";

            default ->
                    throw new IllegalArgumentException(
                            "지원하지 않는 테스트 대상: " + target
                    );
        };
    }

    private String validBody() {
        return body(
                "SPAM_ADVERTISING",
                REASON_DETAIL
        );
    }

    private String body(
            String reasonType,
            String reasonDetail
    ) {
        // null 입력값도 JSON으로 전송하기 위해 Map.of 대신 사용한다.
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("reasonType", reasonType);
        request.put("reasonDetail", reasonDetail);

        return objectMapper.writeValueAsString(request);
    }

    private MockHttpServletRequestBuilder reportRequest(
            String target,
            CsrfSession client,
            String body
    ) {
        return post(path(target))
                .session(client.session())
                .header(client.headerName(), client.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private ResultActions submit(
            String target,
            CsrfSession client,
            String body
    ) throws Exception {
        return mockMvc.perform(
                reportRequest(target, client, body)
        );
    }

    private ContentReport expectCreated(
            ResultActions actions
    ) throws Exception {
        MvcResult result = actions
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(jsonPath("$.reportId").isNumber())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andReturn();

        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsByteArray()
        );

        Long reportId = response.path("reportId").asLong();
        Instant responseCreatedAt = Instant.parse(
                response.path("createdAt").asString()
        );

        assertThat(reportId).isPositive();

        // HTTP 요청의 트랜잭션이 끝난 후 실제 저장된 기록을 조회한다.
        ContentReport stored = reportRepository.findById(reportId)
                .orElseThrow();

        assertThat(stored.getStatus())
                .isEqualTo(ContentReportStatus.RECEIVED);
        assertThat(stored.getCreatedAt()).isNotNull();

        /*
         * Java Instant와 PostgreSQL timestamp의 정밀도 차이를 허용한다.
         * 동일한 접수 시각인지 1ms 미만 차이로 검증한다.
         */
        Duration difference = Duration.between(
                stored.getCreatedAt(),
                responseCreatedAt
        ).abs();

        assertThat(difference).isLessThan(Duration.ofMillis(1));

        return stored;
    }

    private long reportCount(String target) {
        String sql;
        Long targetId;

        switch (target) {
            case "POST" -> {
                sql = """
                        SELECT COUNT(*)
                          FROM content_reports
                         WHERE post_id = ?
                        """;
                targetId = postId;
            }

            case "COMMENT" -> {
                sql = """
                        SELECT COUNT(*)
                          FROM content_reports
                         WHERE comment_id = ?
                        """;
                targetId = commentId;
            }

            default ->
                    throw new IllegalArgumentException(
                            "지원하지 않는 테스트 대상: " + target
                    );
        }

        Long count = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                targetId
        );

        assertThat(count).isNotNull();

        return count;
    }

    private void assertTargetSnapshot(
            ContentReport stored,
            String target
    ) {
        switch (target) {
            case "POST" -> {
                assertThat(stored.getPostId()).isEqualTo(postId);
                assertThat(stored.getCommentId()).isNull();
                assertThat(stored.getTitleSnapshot())
                        .isEqualTo(POST_TITLE);
                assertThat(stored.getContentSnapshot())
                        .isEqualTo(POST_CONTENT);
            }

            case "COMMENT" -> {
                assertThat(stored.getPostId()).isNull();
                assertThat(stored.getCommentId()).isEqualTo(commentId);
                assertThat(stored.getTitleSnapshot()).isNull();
                assertThat(stored.getContentSnapshot())
                        .isEqualTo(COMMENT_CONTENT);
            }

            default ->
                    throw new IllegalArgumentException(
                            "지원하지 않는 테스트 대상: " + target
                    );
        }
    }

    private void assertContentAndMembersUnchanged() {
        Post storedPost = postRepository.findById(postId)
                .orElseThrow();

        Comment storedComment = commentRepository.findById(commentId)
                .orElseThrow();

        Member storedAuthor = memberRepository.findById(authorId)
                .orElseThrow();

        Member storedReporter = memberRepository.findById(reporterId)
                .orElseThrow();

        assertThat(storedPost.getHiddenAt()).isNull();
        assertThat(storedPost.getDeletedAt()).isNull();
        assertThat(storedPost.getTitle()).isEqualTo(POST_TITLE);
        assertThat(storedPost.getContent()).isEqualTo(POST_CONTENT);

        assertThat(storedComment.getHiddenAt()).isNull();
        assertThat(storedComment.getDeletedAt()).isNull();
        assertThat(storedComment.getContent()).isEqualTo(COMMENT_CONTENT);

        assertThat(storedAuthor.getStatus())
                .isEqualTo(MemberStatus.ACTIVE);
        assertThat(storedAuthor.getCredentialVersion()).isZero();

        assertThat(storedReporter.getStatus())
                .isEqualTo(MemberStatus.ACTIVE);
        assertThat(storedReporter.getCredentialVersion())
                .isEqualTo(REPORTER_VERSION);
    }

    private record CsrfSession(
            MockHttpSession session,
            String headerName,
            String token
    ) {
    }
}