package io.github.takgeun.iyum.admin.member.infrastructure;

import io.github.takgeun.iyum.admin.member.domain.MemberModerationAction;
import io.github.takgeun.iyum.admin.member.domain.MemberModerationLog;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Testcontainers
@Transactional
class MemberModerationLogRepositoryTest {

    private static final Instant NOW =
            Instant.parse("2026-11-10T10:00:00Z");

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
    MemberModerationLogRepository logRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean(name = "auditingDateTimeProvider")
    DateTimeProvider dateTimeProvider;

    private Long actorId;
    private Long targetId;

    @BeforeEach
    void setUp() {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(NOW));

        Member actor = activeMember(
                "moderator@example.com",
                "처리관리자"
        );
        memberRepository.saveAndFlush(actor);
        actorId = actor.getId();

        // 테스트 준비용 관리자 지정이다.
        jdbcTemplate.update(
                "UPDATE members SET role = 'ADMIN' WHERE id = ?",
                actorId
        );

        Member target = activeMember(
                "target@example.com",
                "조치대상회원"
        );
        memberRepository.saveAndFlush(target);
        targetId = target.getId();

        entityManager.clear();
    }

    @ParameterizedTest
    @EnumSource(MemberModerationAction.class)
    void 회원_조치_이력을_저장하고_DB에서_다시_조회한다(
            MemberModerationAction action
    ) {
        MemberModerationLog saved = logRepository.saveAndFlush(
                MemberModerationLog.create(
                        actorId,
                        targetId,
                        action,
                        "  운영 정책 검토 결과  ",
                        NOW
                )
        );

        Long logId = saved.getId();
        entityManager.clear();

        MemberModerationLog found =
                logRepository.findById(logId).orElseThrow();

        MemberStatus expectedBefore =
                action == MemberModerationAction.SUSPEND
                        ? MemberStatus.ACTIVE
                        : MemberStatus.SUSPENDED;

        MemberStatus expectedAfter =
                action == MemberModerationAction.SUSPEND
                        ? MemberStatus.SUSPENDED
                        : MemberStatus.ACTIVE;

        assertThat(found.getActorMemberId()).isEqualTo(actorId);
        assertThat(found.getTargetMemberId()).isEqualTo(targetId);
        assertThat(found.getAction()).isEqualTo(action);
        assertThat(found.getBeforeStatus()).isEqualTo(expectedBefore);
        assertThat(found.getAfterStatus()).isEqualTo(expectedAfter);
        assertThat(found.getReason()).isEqualTo("운영 정책 검토 결과");
        assertThat(found.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void 이모지_500_코드포인트의_사유를_DB에_저장할_수_있다() {
        String reason = "😀".repeat(500);

        MemberModerationLog saved = logRepository.saveAndFlush(
                MemberModerationLog.create(
                        actorId,
                        targetId,
                        MemberModerationAction.SUSPEND,
                        reason,
                        NOW
                )
        );

        Long logId = saved.getId();
        entityManager.clear();

        MemberModerationLog found =
                logRepository.findById(logId).orElseThrow();

        assertThat(found.getReason()).isEqualTo(reason);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UNKNOWN_ACTOR",
            "UNKNOWN_TARGET",
            "SAME_MEMBER",
            "INVALID_ACTION",
            "INVALID_BEFORE_STATUS",
            "INVALID_AFTER_STATUS",
            "INVALID_LIFT_TRANSITION",
            "BLANK_REASON",
            "TOO_LONG_REASON",
            "NULL_CREATED_AT"
    })
    void 잘못된_이력은_DB_제약조건으로_차단한다(String scenario) {
        Long actor = actorId;
        Long target = targetId;
        String action = "SUSPEND";
        String beforeStatus = "ACTIVE";
        String afterStatus = "SUSPENDED";
        String reason = "운영 정책 위반";
        Timestamp createdAt = Timestamp.from(NOW);

        switch (scenario) {
            case "UNKNOWN_ACTOR" -> actor = Long.MAX_VALUE;
            case "UNKNOWN_TARGET" -> target = Long.MAX_VALUE;
            case "SAME_MEMBER" -> target = actorId;
            case "INVALID_ACTION" -> action = "HIDE";
            case "INVALID_BEFORE_STATUS" -> beforeStatus = "PENDING";
            case "INVALID_AFTER_STATUS" -> afterStatus = "ACTIVE";
            case "INVALID_LIFT_TRANSITION" -> action = "LIFT_SUSPENSION";
            case "BLANK_REASON" -> reason = "   ";
            case "TOO_LONG_REASON" -> reason = "가".repeat(501);
            case "NULL_CREATED_AT" -> createdAt = null;
            default -> throw new IllegalArgumentException(scenario);
        }

        Object[] parameters = {
                actor,
                target,
                action,
                beforeStatus,
                afterStatus,
                reason,
                createdAt
        };

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO admin_member_moderation_logs (
                    actor_member_id,
                    target_member_id,
                    action,
                    before_status,
                    after_status,
                    reason,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, parameters))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 이력에_연결된_관리자나_대상_회원은_물리_삭제할_수_없다(
            boolean deleteActor
    ) {
        logRepository.saveAndFlush(
                MemberModerationLog.create(
                        actorId,
                        targetId,
                        MemberModerationAction.SUSPEND,
                        "운영 정책 위반",
                        NOW
                )
        );

        Long memberId = deleteActor ? actorId : targetId;

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM members WHERE id = ?",
                memberId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 대상_회원이_탈퇴해도_기존_조치_이력은_유지된다() {
        Member target = memberRepository.findById(targetId).orElseThrow();

        target.suspend();

        MemberModerationLog suspension = logRepository.saveAndFlush(
                MemberModerationLog.create(
                        actorId,
                        targetId,
                        MemberModerationAction.SUSPEND,
                        "운영 정책 위반",
                        NOW
                )
        );

        target.liftSuspension();

        logRepository.saveAndFlush(
                MemberModerationLog.create(
                        actorId,
                        targetId,
                        MemberModerationAction.LIFT_SUSPENSION,
                        "재검토 결과 정지 해제",
                        NOW.plusSeconds(60)
                )
        );

        target.withdraw(NOW.plusSeconds(120));
        memberRepository.flush();

        Long suspensionLogId = suspension.getId();
        entityManager.clear();

        Member withdrawn =
                memberRepository.findById(targetId).orElseThrow();

        MemberModerationLog found =
                logRepository.findById(suspensionLogId).orElseThrow();

        assertThat(withdrawn.getStatus()).isEqualTo(MemberStatus.DELETED);
        assertThat(withdrawn.getEmail()).isNull();
        assertThat(withdrawn.getNickname()).isNull();

        assertThat(found.getTargetMemberId()).isEqualTo(targetId);
        assertThat(found.getAction())
                .isEqualTo(MemberModerationAction.SUSPEND);
        assertThat(found.getBeforeStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(found.getAfterStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(found.getReason()).isEqualTo("운영 정책 위반");
        assertThat(found.getCreatedAt()).isEqualTo(NOW);
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
}