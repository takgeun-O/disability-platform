package io.github.takgeun.iyum.admin.member.application;

import io.github.takgeun.iyum.admin.member.domain.MemberModerationAction;
import io.github.takgeun.iyum.admin.member.domain.MemberModerationLog;
import io.github.takgeun.iyum.admin.member.infrastructure.MemberModerationLogRepository;
import io.github.takgeun.iyum.member.domain.Member;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
class AdminMemberModerationServiceIntegrationTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-11-11T10:00:00Z");

    private static final Instant UPDATED_AT =
            CREATED_AT.plusSeconds(60);

    private static final long ADMIN_VERSION = 3L;
    private static final long TARGET_VERSION = 5L;

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
    AdminMemberModerationService service;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    MemberModerationLogRepository logRepository;

    @MockitoBean(name = "auditingDateTimeProvider")
    DateTimeProvider dateTimeProvider;

    private TransactionTemplate transactionTemplate;

    private Long adminId;
    private Long targetId;

    @BeforeEach
    void setUp() {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(CREATED_AT));

        transactionTemplate =
                new TransactionTemplate(transactionManager);

        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8);

        // 준비 데이터를 별도 트랜잭션으로 커밋한다.
        transactionTemplate.executeWithoutResult(status -> {
            Member admin = activeMember(
                    "admin-" + suffix + "@example.com",
                    "관리자" + suffix
            );
            entityManager.persist(admin);

            Member target = activeMember(
                    "target-" + suffix + "@example.com",
                    "대상회원" + suffix
            );
            entityManager.persist(target);

            entityManager.flush();

            adminId = admin.getId();
            targetId = target.getId();

            jdbcTemplate.update("""
                    UPDATE members
                    SET role = 'ADMIN',
                        credential_version = ?
                    WHERE id = ?
                    """, ADMIN_VERSION, adminId);

            jdbcTemplate.update("""
                    UPDATE members
                    SET credential_version = ?
                    WHERE id = ?
                    """, TARGET_VERSION, targetId);

            entityManager.clear();
        });

        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(UPDATED_AT));
    }

    @ParameterizedTest
    @EnumSource(MemberModerationAction.class)
    void 상태와_인증버전과_이력이_함께_커밋된다(
            MemberModerationAction action
    ) {
        prepareInitialState(action);

        AdminMemberModerationResult result =
                execute(action, "  운영 정책 검토 결과  ");

        // 서비스 트랜잭션이 종료된 뒤 DB에서 직접 읽는다.
        MemberSnapshot member = readTarget();
        StoredLog log = readSingleLog();

        assertThat(result.memberId()).isEqualTo(targetId);
        assertThat(result.status()).isEqualTo(action.afterStatus());
        assertThat(result.changed()).isTrue();

        assertThat(member.status())
                .isEqualTo(action.afterStatus().name());
        assertThat(member.credentialVersion())
                .isEqualTo(TARGET_VERSION + 1L);
        assertThat(member.updatedAt()).isEqualTo(UPDATED_AT);

        assertThat(countLogs()).isEqualTo(1L);

        assertThat(log.actorId()).isEqualTo(adminId);
        assertThat(log.targetId()).isEqualTo(targetId);
        assertThat(log.action()).isEqualTo(action.name());
        assertThat(log.beforeStatus())
                .isEqualTo(action.beforeStatus().name());
        assertThat(log.afterStatus())
                .isEqualTo(action.afterStatus().name());
        assertThat(log.reason()).isEqualTo("운영 정책 검토 결과");
        assertThat(log.createdAt()).isNotNull();

        // 대상 회원 조치로 처리 관리자의 인증 버전은 변경되지 않는다.
        assertThat(jdbcTemplate.queryForObject("""
                SELECT credential_version
                FROM members
                WHERE id = ?
                """, Long.class, adminId))
                .isEqualTo(ADMIN_VERSION);
    }

    @ParameterizedTest
    @EnumSource(MemberModerationAction.class)
    void 반복_요청은_인증버전과_수정시각과_기존_이력을_변경하지_않는다(
            MemberModerationAction action
    ) {
        prepareInitialState(action);

        AdminMemberModerationResult first =
                execute(action, "최초 처리 사유");

        MemberSnapshot afterFirst = readTarget();
        StoredLog firstLog = readSingleLog();

        assertThat(first.changed()).isTrue();
        assertThat(countLogs()).isEqualTo(1L);

        // 두 번째 요청이 불필요한 UPDATE를 실행하면 시각이 달라진다.
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(UPDATED_AT.plusSeconds(60)));

        AdminMemberModerationResult second =
                execute(action, "두 번째 요청 사유");

        assertThat(second.memberId()).isEqualTo(targetId);
        assertThat(second.status()).isEqualTo(action.afterStatus());
        assertThat(second.changed()).isFalse();

        assertThat(readTarget()).isEqualTo(afterFirst);
        assertThat(countLogs()).isEqualTo(1L);
        assertThat(readSingleLog()).isEqualTo(firstLog);
    }

    @ParameterizedTest
    @EnumSource(MemberModerationAction.class)
    void DB에_반영한_뒤_실패하면_회원_변경과_이력이_함께_롤백된다(
            MemberModerationAction action
    ) {
        prepareInitialState(action);

        MemberSnapshot before = readTarget();

        IllegalStateException failure =
                new IllegalStateException("테스트용 저장 후 실패");

        doAnswer(invocation -> {
            MemberModerationLog log = invocation.getArgument(0);

            // 이 테스트에서만 저장소의 save 동작을 대체한다.
            // 이력을 실제 INSERT하고 회원 변경도 DB에 반영한다.
            entityManager.persist(log);
            entityManager.flush();

            // 같은 트랜잭션에서는 반영된 값을 읽을 수 있어야 한다.
            assertThat(countLogs()).isEqualTo(1L);
            assertThat(readTarget().status())
                    .isEqualTo(action.afterStatus().name());
            assertThat(readTarget().credentialVersion())
                    .isEqualTo(TARGET_VERSION + 1L);

            // SQL 실행 후, 트랜잭션 커밋 전에 실패시킨다.
            throw failure;
        }).when(logRepository).save(any(MemberModerationLog.class));

        assertThatThrownBy(() ->
                execute(action, "롤백 검증 사유")
        ).isSameAs(failure);

        // 서비스 트랜잭션 종료 후 다시 조회한다.
        assertThat(readTarget()).isEqualTo(before);
        assertThat(countLogs()).isZero();
    }

    private void prepareInitialState(MemberModerationAction action) {
        // 테스트 시작 상태를 만들기 위한 SQL이다.
        // 이 준비 작업 자체에 대한 운영 이력은 만들지 않는다.
        transactionTemplate.executeWithoutResult(status ->
                jdbcTemplate.update("""
                        UPDATE members
                        SET status = ?
                        WHERE id = ?
                        """, action.beforeStatus().name(), targetId)
        );
    }

    private AdminMemberModerationResult execute(
            MemberModerationAction action,
            String reason
    ) {
        return switch (action) {
            case SUSPEND -> service.suspend(
                    adminId,
                    ADMIN_VERSION,
                    targetId,
                    reason
            );
            case LIFT_SUSPENSION -> service.liftSuspension(
                    adminId,
                    ADMIN_VERSION,
                    targetId,
                    reason
            );
        };
    }

    private Member activeMember(String email, String nickname) {
        Member member = Member.createPending(
                email,
                "test-only-password-hash",
                nickname
        );
        member.activeAfterEmailVerification();
        return member;
    }

    private MemberSnapshot readTarget() {
        return jdbcTemplate.queryForObject("""
                SELECT status,
                       credential_version,
                       updated_at
                FROM members
                WHERE id = ?
                """,
                (rs, rowNum) -> new MemberSnapshot(
                        rs.getString("status"),
                        rs.getLong("credential_version"),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                targetId
        );
    }

    private long countLogs() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM admin_member_moderation_logs
                WHERE target_member_id = ?
                """,
                Long.class,
                targetId
        );

        return count.longValue();
    }

    private StoredLog readSingleLog() {
        return jdbcTemplate.queryForObject("""
                SELECT id,
                       actor_member_id,
                       target_member_id,
                       action,
                       before_status,
                       after_status,
                       reason,
                       created_at
                FROM admin_member_moderation_logs
                WHERE target_member_id = ?
                """,
                (rs, rowNum) -> new StoredLog(
                        rs.getLong("id"),
                        rs.getLong("actor_member_id"),
                        rs.getLong("target_member_id"),
                        rs.getString("action"),
                        rs.getString("before_status"),
                        rs.getString("after_status"),
                        rs.getString("reason"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                targetId
        );
    }

    private record MemberSnapshot(
            String status,
            long credentialVersion,
            Instant updatedAt
    ) {
    }

    private record StoredLog(
            Long id,
            Long actorId,
            Long targetId,
            String action,
            String beforeStatus,
            String afterStatus,
            String reason,
            Instant createdAt
    ) {
    }
}