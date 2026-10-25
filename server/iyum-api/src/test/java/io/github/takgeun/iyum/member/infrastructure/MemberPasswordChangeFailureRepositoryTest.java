package io.github.takgeun.iyum.member.infrastructure;

import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberPasswordChangeFailure;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Testcontainers
@Transactional
class MemberPasswordChangeFailureRepositoryTest {

    private static final Instant NOW =
            Instant.parse("2026-10-22T03:00:00Z");

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
    MemberPasswordChangeFailureRepository failureRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    void 첫_실패는_차단_없이_저장되고_다섯번째는_15분_차단한다() {
        Long memberId = createMember("차단회원");

        var first = saveFailure(memberId, NOW.minusSeconds(4), 0);
        saveFailure(memberId, NOW.minusSeconds(3), 1);
        saveFailure(memberId, NOW.minusSeconds(2), 2);
        saveFailure(memberId, NOW.minusSeconds(1), 3);
        var fifth = saveFailure(memberId, NOW, 4);

        Long firstId = first.getId();
        Long fifthId = fifth.getId();
        entityManager.clear();

        var reloadedFirst = failureRepository.findById(firstId)
                .orElseThrow();
        var reloadedFifth = failureRepository.findById(fifthId)
                .orElseThrow();

        assertThat(reloadedFirst.getMemberId()).isEqualTo(memberId);
        assertThat(reloadedFirst.getFailedAt())
                .isEqualTo(NOW.minusSeconds(4));
        assertThat(reloadedFirst.getBlockedUntil()).isNull();

        assertThat(reloadedFifth.getBlockedUntil())
                .isEqualTo(NOW.plusSeconds(900));

        assertThat(failureRepository.findBlockedUntil(memberId))
                .contains(NOW.plusSeconds(900));
    }

    @Test
    void 실패_집계는_시작_경계를_제외하고_현재를_포함한다() {
        Long memberId = createMember("집계회원");
        Long otherMemberId = createMember("다른회원");

        Instant start = NOW.minusSeconds(900);

        saveFailure(memberId, start, 0);                // 제외
        saveFailure(memberId, start.plusSeconds(1), 0); // 포함
        saveFailure(memberId, NOW, 1);                  // 포함
        saveFailure(memberId, NOW.plusSeconds(1), 2);    // 미래: 제외
        saveFailure(otherMemberId, NOW, 0);             // 다른 회원: 제외

        entityManager.clear();

        assertThat(failureRepository.countFailuresInWindow(
                memberId, start, NOW
        )).isEqualTo(2);
    }

    @Test
    void 초기_실패가_집계에서_빠져도_차단_종료는_유지된다() {
        Long memberId = createMember("차단유지");

        saveFailure(memberId, NOW.minusSeconds(840), 0);
        saveFailure(memberId, NOW.minusSeconds(830), 1);
        saveFailure(memberId, NOW.minusSeconds(820), 2);
        saveFailure(memberId, NOW.minusSeconds(810), 3);
        saveFailure(memberId, NOW, 4);

        Instant later = NOW.plusSeconds(120);
        Instant start = later.minusSeconds(900);

        // 실제 서비스와 동일하게 회원을 먼저 잠근 뒤 정리한다.
        memberRepository.findByIdForUpdate(memberId).orElseThrow();

        int deleted = failureRepository.deleteExpiredByMemberId(
                memberId, start, later
        );
        entityManager.clear();

        assertThat(deleted).isEqualTo(4);

        assertThat(failureRepository.countFailuresInWindow(
                memberId, start, later
        )).isEqualTo(1);

        // 실패 횟수가 1회로 줄어도 기존 차단은 계속 유지되어야 한다.
        assertThat(failureRepository.findBlockedUntil(memberId))
                .contains(NOW.plusSeconds(900));
    }

    @Test
    void 차단이_끝난_기록은_정확히_종료_시각부터_정리한다() {
        Long memberId = createMember("만료회원");

        saveFailure(memberId, NOW, 4);

        Instant end = NOW.plusSeconds(900);
        memberRepository.findByIdForUpdate(memberId).orElseThrow();

        int deleted = failureRepository.deleteExpiredByMemberId(
                memberId,
                end.minusSeconds(900),
                end
        );
        entityManager.clear();

        assertThat(deleted).isEqualTo(1);
        assertThat(failureRepository.findBlockedUntil(memberId))
                .isEmpty();
        assertThat(failureRepository.countFailuresInWindow(
                memberId, NOW, end
        )).isZero();
    }

    @Test
    void 성공_후_초기화는_해당_회원의_기록만_삭제한다() {
        Long memberId = createMember("초기화회원");
        Long otherMemberId = createMember("보존회원");

        saveFailure(memberId, NOW.minusSeconds(1), 0);
        saveFailure(memberId, NOW, 1);
        saveFailure(otherMemberId, NOW, 0);

        memberRepository.findByIdForUpdate(memberId).orElseThrow();

        int deleted = failureRepository.deleteAllByMemberId(memberId);
        entityManager.clear();

        assertThat(deleted).isEqualTo(2);

        assertThat(failureRepository.countFailuresInWindow(
                memberId, NOW.minusSeconds(900), NOW
        )).isZero();

        assertThat(failureRepository.countFailuresInWindow(
                otherMemberId, NOW.minusSeconds(900), NOW
        )).isEqualTo(1);

        assertThat(memberRepository.findById(memberId)).isPresent();
    }

    private Long createMember(String nickname) {
        Member member = Member.createPending(
                nickname + "@example.com",
                "test-only-password-hash",
                nickname
        );
        member.activeAfterEmailVerification();

        return memberRepository.saveAndFlush(member).getId();
    }

    private MemberPasswordChangeFailure saveFailure(
            Long memberId,
            Instant failedAt,
            long previousFailures
    ) {
        return failureRepository.saveAndFlush(
                MemberPasswordChangeFailure.record(
                        memberId,
                        failedAt,
                        previousFailures
                )
        );
    }
}