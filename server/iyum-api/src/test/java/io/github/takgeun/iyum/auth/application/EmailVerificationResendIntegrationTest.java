package io.github.takgeun.iyum.auth.application;

import io.github.takgeun.iyum.auth.domain.EmailVerificationToken;
import io.github.takgeun.iyum.auth.infrastructure.EmailVerificationTokenHasher;
import io.github.takgeun.iyum.auth.infrastructure.EmailVerificationTokenRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberAgreementRepository;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=", "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
        "iyum.auth.email-verification.mail.enabled=false",
        "iyum.cors.allowed-origin=http://127.0.0.1:8443",
        "springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers
@Import({EmailVerificationMailRequestedListener.class, EmailVerificationResendIntegrationTest.EventsConfig.class})
class EmailVerificationResendIntegrationTest {
    static final Instant START = Instant.parse("2026-10-10T00:00:00Z");
    static final String EMAIL = "resend@example.com";
    static final String PATH = "/api/v1/auth/email/resend";
    @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired EmailVerificationResendService resendService;
    @Autowired EmailVerificationTokenIssueService issueService;
    @Autowired EmailVerificationService verifyService;
    @Autowired MemberRepository members;
    @Autowired MemberAgreementRepository agreements;
    @MockitoSpyBean EmailVerificationTokenRepository tokens;
    @Autowired EmailVerificationTokenHasher hasher;
    @MockitoBean Clock clock;
    @MockitoBean EmailVerificationMailSender sender;
    @Autowired ThreadPoolTaskExecutor emailVerificationMailExecutor;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MailEvents events;
    long memberId;
    String original;

    @TestConfiguration(proxyBeanMethods = false)
    static class EventsConfig {
        @Bean MailEvents mailEvents() { return new MailEvents(); }
    }
    static class MailEvents {
        final List<EmailVerificationMailRequested> values = new CopyOnWriteArrayList<>();
        @EventListener public void capture(EmailVerificationMailRequested event) { values.add(event); }
    }

    @BeforeEach void setUp() {
        when(clock.instant()).thenReturn(START);
        tokens.deleteAllInBatch();
        agreements.deleteAllInBatch();
        members.deleteAllInBatch();
        events.values.clear();
        memberId = members.saveAndFlush(Member.createPending(EMAIL, "test-only-hash", "ResendTest")).getId();
        original = issueService.issue(memberId).rawToken();
    }
    @AfterEach void drainMail() {
        await().until(() -> emailVerificationMailExecutor.getActiveCount() == 0
                && emailVerificationMailExecutor.getQueueSize() == 0);
    }
    void at(long seconds) { when(clock.instant()).thenReturn(START.plusSeconds(seconds)); }
    EmailVerificationToken oldToken() { return tokens.findByTokenHash(hasher.hash(original)).orElseThrow(); }
    void unchanged() {
        assertThat(tokens.count()).isEqualTo(1);
        assertThat(oldToken().getRevokedAt()).isNull();
        assertThat(events.values).isEmpty();
        verifyNoInteractions(sender);
    }

    @Test void allowedResendRevokesOldTokenAndOnlyNewTokenCanActivate() {
        at(60);
        resendService.resend("  RESEND@EXAMPLE.COM  ");
        assertThat(tokens.count()).isEqualTo(2);
        assertThat(oldToken().getRevokedAt()).isEqualTo(START.plusSeconds(60));
        assertThat(events.values).hasSize(1);
        var issued = events.values.getFirst().issuedToken();
        assertThat(issued.expiresAt()).isEqualTo(START.plusSeconds(60 + 1800));
        assertThatThrownBy(() -> verifyService.verify(original)).isInstanceOf(InvalidEmailVerificationTokenException.class);
        assertThat(verifyService.verify(issued.rawToken()).status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(members.findById(memberId).orElseThrow().getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test void firstSignupIssueCountsAndExactSixtySecondsIsAllowed() {
        resendService.resend(EMAIL);
        when(clock.instant()).thenReturn(START.plusSeconds(60).minusNanos(1000));
        resendService.resend(EMAIL);
        unchanged();
        at(60);
        resendService.resend(EMAIL);
        assertThat(tokens.count()).isEqualTo(2);
        assertThat(events.values).hasSize(1);
    }

    @Test void slidingHourCountsRevokedHistoryAndExcludesExactHourBoundary() {
        for (int seconds : new int[]{60, 120, 180, 240}) {
            at(seconds);
            resendService.resend(EMAIL);
        }
        drainMail();
        clearInvocations(sender);
        events.values.clear();
        var latest = tokens.findAll().stream().filter(t -> t.getRevokedAt() == null).findFirst().orElseThrow();
        at(300);
        resendService.resend(EMAIL);
        when(clock.instant()).thenReturn(START.plusSeconds(3600).minusNanos(1000));
        resendService.resend(EMAIL);
        assertThat(tokens.count()).isEqualTo(5);
        assertThat(tokens.findById(latest.getId()).orElseThrow().getRevokedAt()).isNull();
        assertThat(events.values).isEmpty();
        verifyNoInteractions(sender);
        at(3600);
        resendService.resend(EMAIL);
        assertThat(tokens.count()).isEqualTo(6);
        assertThat(events.values).hasSize(1);
    }

    @Test void expiredTokensAreRevokedAndUsedHistoryIsPreserved() {
        var expired = tokens.saveAndFlush(EmailVerificationToken.issue(memberId, "a".repeat(64),
                START.minusSeconds(4000), START.minusSeconds(2000)));
        var used = EmailVerificationToken.issue(memberId, "b".repeat(64),
                START.minusSeconds(4000), START.minusSeconds(2000));
        used.use(START.minusSeconds(3000));
        used = tokens.saveAndFlush(used);
        at(60);
        resendService.resend(EMAIL);
        assertThat(tokens.findById(expired.getId()).orElseThrow().getRevokedAt()).isEqualTo(START.plusSeconds(60));
        var preserved = tokens.findById(used.getId()).orElseThrow();
        assertThat(preserved.getUsedAt()).isEqualTo(START.minusSeconds(3000));
        assertThat(preserved.getRevokedAt()).isNull();
    }

    @ParameterizedTest @EnumSource(value = MemberStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    void nonPendingMemberIsUnchanged(MemberStatus status) {
        jdbc.update("update members set status = ? where id = ?", status.name(), memberId);
        at(60);
        resendService.resend(EMAIL);
        unchanged();
    }
    @Test void missingEmailIsUnchanged() { at(60); resendService.resend("absent@example.com"); unchanged(); }

    @Test void saveFailureRollsBackEvenFlushedRevocation() {
        at(60);
        doAnswer(invocation -> {
            tokens.flush();
            throw new DataIntegrityViolationException("test storage failure");
        }).when(tokens).save(any(EmailVerificationToken.class));
        assertThatThrownBy(() -> resendService.resend(EMAIL)).isInstanceOf(DataIntegrityViolationException.class);
        unchanged();
    }

    @Test void rollbackAfterPublishingDoesNotQueueMailOrRevokeToken() {
        at(60);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            resendService.resend(EMAIL);
            assertThat(events.values).hasSize(1);
            verifyNoInteractions(sender);
            status.setRollbackOnly();
        });
        assertThat(oldToken().getRevokedAt()).isNull();
        assertThat(tokens.count()).isEqualTo(1);
        verifyNoInteractions(sender);
    }

    @Test void concurrentResendsIssueOnlyOnce() throws Exception {
        at(60);
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Void> request = () -> { barrier.await(5, TimeUnit.SECONDS); resendService.resend(EMAIL); return null; };
            var first = pool.submit(request);
            var second = pool.submit(request);
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
        assertThat(tokens.count()).isEqualTo(2);
        assertThat(events.values).hasSize(1);
        drainMail();
        verify(sender, times(1)).send(eq(EMAIL), any());
    }

    @Test void clockIsReadAfterWaitingForMemberLock() throws Exception {
        at(59);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var holder = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                members.findByIdForUpdate(memberId).orElseThrow();
                locked.countDown();
                awaitLatch(release);
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var request = pool.submit(() -> resendService.resend(EMAIL));
            try {
                assertThatThrownBy(() -> request.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                at(60);
            } finally { release.countDown(); }
            holder.get(10, TimeUnit.SECONDS);
            request.get(10, TimeUnit.SECONDS);
        }
        assertThat(tokens.count()).isEqualTo(2);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void verifyAndResendSerializeInBothOrders(boolean verificationFirst) throws Exception {
        at(60);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                members.findByIdForUpdate(memberId).orElseThrow();
                if (verificationFirst) verifyService.verify(original); else resendService.resend(EMAIL);
                locked.countDown();
                awaitLatch(release);
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> {
                if (verificationFirst) resendService.resend(EMAIL);
                else assertThatThrownBy(() -> verifyService.verify(original)).isInstanceOf(InvalidEmailVerificationTokenException.class);
            });
            try {
                assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally { release.countDown(); }
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
        if (verificationFirst) {
            assertThat(tokens.count()).isEqualTo(1);
            assertThat(events.values).isEmpty();
            assertThat(oldToken().getUsedAt()).isNotNull();
        } else {
            assertThat(tokens.count()).isEqualTo(2);
            assertThat(events.values).hasSize(1);
            assertThat(verifyService.verify(events.values.getFirst().issuedToken().rawToken()).status()).isEqualTo(MemberStatus.ACTIVE);
        }
        assertThat(members.findById(memberId).orElseThrow().getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }
    static void awaitLatch(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("latch timeout"); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
    }

    @Test void mailSeesCommittedDataAndSlowSmtpDoesNotBlockService() throws Exception {
        at(60);
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var committed = new AtomicBoolean();
        doAnswer(invocation -> {
            var issued = (IssuedEmailVerificationToken) invocation.getArgument(1);
            committed.set(tokens.findByTokenHash(hasher.hash(issued.rawToken())).isPresent()
                    && oldToken().getRevokedAt() != null);
            entered.countDown();
            awaitLatch(finish);
            return null;
        }).when(sender).send(anyString(), any());
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                resendService.resend(EMAIL);
                verifyNoInteractions(sender);
            });
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(committed.get()).isTrue();
            // SMTP는 아직 finish를 기다리지만 서비스 트랜잭션은 반환했다.
            assertThat(finish.getCount()).isEqualTo(1);
        } finally { finish.countDown(); }
    }

    @Test void smtpFailureKeepsCommittedReplacementAndAcceptedResponse() throws Exception {
        at(60);
        doThrow(new MailSendException("test SMTP failure")).when(sender).send(anyString(), any());
        accepted(EMAIL);
        drainMail();
        verify(sender).send(eq(EMAIL), any());
        assertThat(tokens.count()).isEqualTo(2);
        assertThat(oldToken().getRevokedAt()).isNotNull();
    }

    @Test void externalContractIsIdenticalForEligibleLimitedAbsentAndActive() throws Exception {
        accepted(EMAIL); // initial interval limit
        accepted("absent@example.com");
        at(60);
        accepted("  RESEND@EXAMPLE.COM  ");
        for (int seconds : new int[]{120, 180, 240}) { at(seconds); accepted(EMAIL); }
        at(300);
        accepted(EMAIL); // hourly limit
        assertThat(tokens.count()).isEqualTo(5);
        verifyService.verify(events.values.getLast().issuedToken().rawToken());
        at(3600);
        accepted(EMAIL); // active
        assertThat(tokens.count()).isEqualTo(5);
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"email\":null}", "{\"email\":\"\"}",
            "{\"email\":\"bad-address\"}", "{\"email\":\"a b@example.com\"}"})
    void inputValidationUsesCommonContract(String body) throws Exception {
        postJson(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        unchanged();
    }
    @Test void oversizedEmailAndMalformedJsonAreRejected() throws Exception {
        postJson(json.writeValueAsString(java.util.Map.of("email", "a".repeat(250) + "@example.com")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        postJson("{broken").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
        unchanged();
    }
    @Test void missingAndWrongCsrfAreRejected() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"resend@example.com\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
        mvc.perform(post(PATH).with(csrf().useInvalidToken()).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"resend@example.com\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
        unchanged();
    }
    @Test void realCsrfSessionAndNarrowCorsWork() throws Exception {
        var csrfResult = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        var csrfBody = json.readTree(csrfResult.getResponse().getContentAsString());
        mvc.perform(post(PATH).session(session).header("X-CSRF-TOKEN", csrfBody.get("token").asString())
                        .header("Origin", "http://127.0.0.1:8443").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"resend@example.com\"}"))
                .andExpect(status().isAccepted()).andExpect(unauthenticated())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:8443"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(options(PATH).header("Origin", "http://127.0.0.1:8443")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type,X-CSRF-TOKEN"))
                .andExpect(status().isOk());
        mvc.perform(options(PATH).header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
    @Test void databaseFailureIsNotHiddenAsAccepted() throws Exception {
        at(60);
        doThrow(new DataAccessResourceFailureException("test database unavailable")).when(tokens).findLatestIssuedAt(memberId);
        postJson("{\"email\":\"resend@example.com\"}").andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
        unchanged();
    }
    @Test void openApiDocumentsAcceptedAndCsrfWithoutSensitiveResponseFields() throws Exception {
        var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var doc = json.readTree(result.getResponse().getContentAsString());
        var operation = doc.at("/paths/~1api~1v1~1auth~1email~1resend/post");
        assertThat(operation.at("/responses/202/content/application~1json/schema/$ref").asString())
                .isEqualTo("#/components/schemas/EmailVerificationResendResponse");
        assertThat(operation.at("/parameters/0/name").asString()).isEqualTo("X-CSRF-TOKEN");
        assertThat(doc.at("/components/schemas/EmailVerificationResendResponse/properties").propertyNames()).containsExactly("status");
        assertThat(doc.at("/components/schemas/EmailVerificationResendRequest/properties/email/writeOnly").asBoolean()).isTrue();
    }
    ResultActions postJson(String body) throws Exception {
        return mvc.perform(post(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }
    void accepted(String email) throws Exception {
        var response = postJson(json.writeValueAsString(java.util.Map.of("email", email)))
                .andExpect(status().isAccepted()).andExpect(unauthenticated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).andReturn().getResponse();
        assertThat(response.getContentAsString()).isEqualTo("{\"status\":\"ACCEPTED\"}");
    }
}
