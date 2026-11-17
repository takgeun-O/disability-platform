package io.github.takgeun.iyum.admin.member.infrastructure;

import io.github.takgeun.iyum.admin.member.application.AdminMemberSummaryCountsResult;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true"
})
@Testcontainers
@Transactional
class AdminMemberSummaryReadRepositoryTest {

    // 한국 시간 2026-11-18 00:00
    private static final Instant START =
            Instant.parse("2026-11-17T15:00:00Z");

    // 한국 시간 2026-11-19 00:00
    private static final Instant END =
            Instant.parse("2026-11-18T15:00:00Z");

    private static final Instant NOW =
            Instant.parse("2026-11-18T03:00:00Z");

    private static final Instant BEFORE =
            START.minusSeconds(86_400);

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
    AdminMemberSummaryReadRepository repository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean(name = "auditingDateTimeProvider")
    DateTimeProvider dateTimeProvider;

    @Test
    void 회원이_없으면_모든_집계는_0이다() {
        assertCounts(
                0L,
                0L,
                0L,
                0L
        );
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING,    1, 1, 1, 0",
            "ACTIVE,     1, 1, 0, 0",
            "RESTRICTED, 1, 1, 0, 0",
            "SUSPENDED,  1, 1, 0, 1",
            "WITHDRAWN,  0, 1, 0, 0",
            "DELETED,    0, 1, 0, 0"
    })
    void 현재_상태에_따라_집계하고_오늘_가입은_모든_상태를_포함한다(
            MemberStatus status,
            long currentCount,
            long todayCount,
            long pendingCount,
            long suspendedCount
    ) {
        saveMember(
                "StateUser",
                status,
                MemberRole.USER,
                NOW
        );

        assertCounts(
                currentCount,
                todayCount,
                pendingCount,
                suspendedCount
        );
    }

    @Test
    void 오늘_가입은_시작을_포함하고_다음날_시작은_제외한다() {
        // PostgreSQL 저장 정밀도에 맞춰 마이크로초 단위로 검증한다.
        List<Instant> createdTimes = List.of(
                START.minusNanos(1_000),
                START,
                END.minusNanos(1_000),
                END
        );

        for (int i = 0; i < createdTimes.size(); i++) {
            saveMember(
                    "Boundary" + i,
                    MemberStatus.PENDING,
                    MemberRole.USER,
                    createdTimes.get(i)
            );
        }

        // 현재 회원·인증 대기는 날짜와 무관하게 4건이다.
        // 오늘 가입에는 START와 END 직전의 2건만 포함한다.
        assertCounts(
                4L,
                2L,
                4L,
                0L
        );
    }

    @Test
    void 현재_회원과_인증_대기와_이용_정지는_가입일과_무관하게_집계한다() {
        saveMember(
                "PendingOne",
                MemberStatus.PENDING,
                MemberRole.USER,
                BEFORE
        );
        saveMember(
                "PendingTwo",
                MemberStatus.PENDING,
                MemberRole.USER,
                BEFORE
        );

        for (int i = 0; i < 3; i++) {
            saveMember(
                    "Active" + i,
                    MemberStatus.ACTIVE,
                    MemberRole.USER,
                    BEFORE
            );
        }

        saveMember(
                "Restricted",
                MemberStatus.RESTRICTED,
                MemberRole.USER,
                BEFORE
        );
        saveMember(
                "Suspended",
                MemberStatus.SUSPENDED,
                MemberRole.USER,
                BEFORE
        );
        saveMember(
                "Withdrawn",
                MemberStatus.WITHDRAWN,
                MemberRole.USER,
                BEFORE
        );
        saveMember(
                "Deleted",
                MemberStatus.DELETED,
                MemberRole.USER,
                BEFORE
        );

        // 각 수치를 다르게 만들어 반환 필드가 뒤바뀌는 오류도 검출한다.
        assertCounts(
                7L,
                0L,
                2L,
                1L
        );
    }

    @Test
    void 관리자_계정도_현재_회원과_오늘_가입과_인증_대기에_포함한다() {
        saveMember(
                "ActiveUser",
                MemberStatus.ACTIVE,
                MemberRole.USER,
                NOW
        );
        saveMember(
                "ActiveAdmin",
                MemberStatus.ACTIVE,
                MemberRole.ADMIN,
                NOW
        );
        saveMember(
                "PendingAdmin",
                MemberStatus.PENDING,
                MemberRole.ADMIN,
                BEFORE
        );

        assertCounts(
                3L,
                2L,
                1L,
                0L
        );
    }

    @Test
    void 인증과_정지와_해제와_탈퇴에_따라_현재_수치만_변경된다() {
        Long memberId = saveMember(
                "ChangingUser",
                MemberStatus.PENDING,
                MemberRole.USER,
                NOW
        );

        assertCounts(
                1L,
                1L,
                1L,
                0L
        );

        // assertCounts에서 clear하므로 매번 관리 상태로 다시 조회한다.
        setAuditingTime(NOW.plusSeconds(60));

        Member member = entityManager.find(
                Member.class,
                memberId
        );
        member.activeAfterEmailVerification();

        assertCounts(
                1L,
                1L,
                0L,
                0L
        );

        setAuditingTime(NOW.plusSeconds(120));

        member = entityManager.find(
                Member.class,
                memberId
        );
        assertThat(member.suspend()).isTrue();

        // 정지는 탈퇴가 아니므로 현재 회원 수는 유지한다.
        assertCounts(
                1L,
                1L,
                0L,
                1L
        );

        setAuditingTime(NOW.plusSeconds(180));

        member = entityManager.find(
                Member.class,
                memberId
        );
        assertThat(member.liftSuspension()).isTrue();

        assertCounts(
                1L,
                1L,
                0L,
                0L
        );

        Instant withdrawnAt = NOW.plusSeconds(240);
        setAuditingTime(withdrawnAt);

        member = entityManager.find(
                Member.class,
                memberId
        );
        member.withdraw(withdrawnAt);

        // 탈퇴해도 오늘 계정이 생성됐다는 사실은 유지한다.
        assertCounts(
                0L,
                1L,
                0L,
                0L
        );
    }

    private Long saveMember(
            String nickname,
            MemberStatus status,
            MemberRole role,
            Instant createdAt
    ) {
        setAuditingTime(createdAt);

        Member member = Member.createPending(
                nickname.toLowerCase(Locale.ROOT) + "@example.com",
                "test-only-password-hash",
                nickname
        );

        if (status != MemberStatus.PENDING) {
            member.activeAfterEmailVerification();
        }

        if (status == MemberStatus.SUSPENDED) {
            member.suspend();
        }

        Instant withdrawnAt =
                status == MemberStatus.WITHDRAWN
                        || status == MemberStatus.DELETED
                        ? createdAt.plusSeconds(120)
                        : null;

        if (status == MemberStatus.DELETED) {
            member.withdraw(withdrawnAt);
        }

        entityManager.persist(member);
        entityManager.flush();

        Long memberId = member.getId();

        // 현재 도메인 메서드가 없는 상태 전환만
        // 테스트 데이터 구성 목적으로 SQL을 사용한다.
        if (status == MemberStatus.RESTRICTED
                || status == MemberStatus.WITHDRAWN) {
            jdbcTemplate.update(
                    """
                    update members
                       set status = ?,
                           withdrawn_at = ?
                     where id = ?
                    """,
                    status.name(),
                    withdrawnAt == null
                            ? null
                            : Timestamp.from(withdrawnAt),
                    memberId
            );
        }

        // 관리자 계정도 집계에 포함되는지 확인하기 위한 테스트 설정이다.
        if (role == MemberRole.ADMIN) {
            jdbcTemplate.update(
                    """
                    update members
                       set role = ?
                     where id = ?
                    """,
                    role.name(),
                    memberId
            );
        }

        // SQL 변경 전의 엔티티가 1차 캐시에 남지 않도록 한다.
        entityManager.clear();

        return memberId;
    }

    private void setAuditingTime(
            Instant time
    ) {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(time));
    }

    private void assertCounts(
            long currentMemberCount,
            long todayMemberCount,
            long pendingMemberCount,
            long suspendedMemberCount
    ) {
        entityManager.flush();
        entityManager.clear();

        AdminMemberSummaryCountsResult result =
                repository.findCounts(
                        START,
                        END
                );

        assertThat(result).isEqualTo(
                new AdminMemberSummaryCountsResult(
                        currentMemberCount,
                        todayMemberCount,
                        pendingMemberCount,
                        suspendedMemberCount
                )
        );
    }
}