package io.github.takgeun.iyum.auth.passwordreset.application;

import io.github.takgeun.iyum.auth.passwordreset.config.PasswordResetProperties;
import io.github.takgeun.iyum.auth.passwordreset.domain.PasswordResetToken;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenGenerator;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenHasher;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenRepository;
import io.github.takgeun.iyum.member.domain.MemberInputPolicy;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PasswordResetTokenIssueService {

    private final MemberRepository memberRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordResetTokenGenerator tokenGenerator;
    private final PasswordResetTokenHasher tokenHasher;
    private final PasswordResetProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public void issue(String email) {
        String normalizedEmail = MemberInputPolicy.normalizeEmail(email);

        // 발급·재설정·탈퇴에서 회원 -> 토큰 잠금 순서를 유지한다.
        var member = memberRepository
                .findByEmailForUpdate(normalizedEmail)
                .orElse(null);

        // 가입 여부와 회원 상태를 호출자에게 구분해서 반환하지 않는다.
        if (member == null || member.getStatus() != MemberStatus.ACTIVE) {
            return;
        }

        Long memberId = member.getId();

        // 잠금을 기다린 시간이 있을 수 있으므로 잠금 획득 후 시각을 구한다.
        Instant now = clock.instant();

        var latestIssuedAt = tokenRepository.findLatestIssuedAt(memberId);

        if (latestIssuedAt.isPresent()
                && now.isBefore(
                latestIssuedAt.get()
                        .plus(properties.resendMinInterval())
        )) {
            return;
        }

        long issuedCount = tokenRepository.countIssuedInWindow(
                memberId,
                now.minus(Duration.ofHours(1)),
                now
        );

        if(issuedCount >= properties.resendMaxPerHour()) {
            return;
        }

        // 발급할 수 있을 때만 기존 토큰을 폐기한다.
        // 제한이 걸린 요청으로 기존 유효 링크를 무효화하지 않는다.
        tokenRepository.findUnconsumedForUpdate(memberId)
                .forEach(token -> token.revoke(now));

        String rawToken = tokenGenerator.generate();
        String tokenHash = tokenHasher.hash(rawToken);
        Instant expiresAt = now.plus(properties.tokenTtl());

        // 실제 이메일은 AFTER_COMMIT 리스너로 연결해서 커밋 성공 후에 보낼 것
        // saveAndFlush() 했다고 해서 커밋까지 완료하는 건 아님
        tokenRepository.saveAndFlush(
                PasswordResetToken.issue(
                        memberId,
                        tokenHash,
                        now,
                        expiresAt
                )
        );

        // 실제 메일 전송은 이후 추가될 커밋 후 리스너가 담당한다.
        events.publishEvent(
                new PasswordResetMailRequested(
                        memberId,
                        member.getEmail(),
                        rawToken,
                        expiresAt
                )
        );
    }
}
