package io.github.takgeun.iyum.auth.passwordreset.application;

import io.github.takgeun.iyum.auth.passwordreset.domain.PasswordResetToken;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenHasher;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 1. 입력값을 검증한다.
 * 2. 토큰 해시로 회원 ID를 찾는다.
 * 3. 회원 -> 토큰 순서로 잠금을 획득한다.
 * 4. ACTIVE 회원인지, 사용할 수 있는 토큰인지 확인한다.
 * 5. 기존 비밀번호와 다른지 확인하고 새 해시를 생성한다.
 * 6. 비밀번호 변경·인증 버전 증가·토큰 사용·다른 토큰 폐기를 한 트랜잭션에서 처리한다.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    // 회원가입과 동일하게 영문과 숫자를 각각 1개 이상 요구한다.
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("(?s)(?=.*[A-Za-z])(?=.*[0-9]).*");

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_BYTES = 72;

    private final MemberRepository memberRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordResetTokenHasher tokenHasher;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    private final ApplicationEventPublisher events;

    @Transactional
    public void reset(String rawToken, String newPassword) {
        PasswordResetTokenInput.validate(rawToken);
        validateNewPassword(newPassword);

        String tokenHash = tokenHasher.hash(rawToken);

        // 이 조회는 회원 잠금을 먼저 획득하기 위한 ID 조회
        // 아직 토큰이 유효하다고 판단하지 않음
        Long memberId = tokenRepository.findMemberIdByTokenHash(tokenHash)
                .orElseThrow(PasswordResetException::invalidToken);

        // 발급 서비스와 동일하게 회원부터 잠그기
        // 재설정에서도 같은 순서를 사용하면 같은 회원의 재발급과 비밀번호 변경이 서로 조정되고
        // 잠금 순서가 반대여서 발생할 수 있는 교착 위험을 줄일 수 있다.
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(PasswordResetException::invalidToken);

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw PasswordResetException.invalidToken();
        }

        // 회원 잠금을 획득한 뒤 토큰의 최신 상태를 조회한다.
        // 처음 조회한 회원 ID만으로 처리하지 않고 잠금을 획득한 뒤 토큰을 다시 조회하는 이유는
        // 그 사이 다른 요청이 토큰을 사용하거나 폐기했을 수 있기 때문
        PasswordResetToken token = tokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(PasswordResetException::invalidToken);

        if (!memberId.equals(token.getMemberId())) {
            throw PasswordResetException.invalidToken();
        }

        if (!token.isUsable(clock.instant())) {
            throw PasswordResetException.invalidToken();
        }

        // 해시 문자열끼리 비교하지 않고 원본 비밀번호를 대조한다.
        if (passwordEncoder.matches(
                newPassword,
                member.getPasswordHash()
        )) {
            throw PasswordResetException.unchangedPassword();
        }

        String newPasswordHash = passwordEncoder.encode(newPassword);

        // 현재 토큰을 포함한 미사용·미폐기 토큰을 잠근다.
        List<PasswordResetToken> unconsumedTokens =
                tokenRepository.findUnconsumedForUpdate(memberId);

        // 비밀번호 해시 생성과 잠금 대기 중 만료될 수 있으므로
        // 실제 변경 직전에 시간을 다시 확인한다.
        Instant now = clock.instant();

        if(!token.isUsable(now)) {
            throw PasswordResetException.invalidToken();
        }

        // 여기부터 실제 상태 변경을 시작한다.
        member.resetPassword(newPasswordHash);
        token.use(now);

        for (PasswordResetToken other : unconsumedTokens) {
            // 현재 토큰은 위에서 사용 처리했으므로 제외한다.
            // 이전에 구현한 revoke()는 사용된 토큰이면 변경하지 않는다.
            other.revoke(now);
        }

        // 같은 영속성 컨텍스트의 회원과 토큰 변경을 DB에 반영한다.
        // flush는 commit이 아니며, 이후 실패하면 함께 롤백
        memberRepository.flush();

        // 같은 트랜잭션 안에서 변경 완료 이벤트를 발행한다.
        // 실제 메일은 AFTER_COMMIT 리스너에서 처리한다.
        // 이벤트 발행 시점과 메일 발송 시점은 다르다.
        // 이벤트는 트랜잭션 안에서 발행하지만,
        // 메일 작업은 리스너가 커밋 성공한 후에만 실행하도록 할 것
        events.publishEvent(
                new PasswordResetCompleted(
                        memberId,
                        member.getEmail(),
                        now // 비밀번호 변경 직전에 확인한 시각
                )
        );
    }

    private void validateNewPassword(String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw PasswordResetException.invalidPassword(
                    "비밀번호를 입력해주세요."
            );
        }

        if (newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw PasswordResetException.invalidPassword(
                    "비밀번호는 8자 이상이어야 합니다."
            );
        }

        if (!PASSWORD_PATTERN.matcher(newPassword).matches()) {
            throw PasswordResetException.invalidPassword(
                    "비밀번호는 영문과 숫자를 각각 1개 이상 포함해야 합니다."
            );
        }

        if (newPassword.getBytes(StandardCharsets.UTF_8).length
                > MAX_PASSWORD_BYTES) {
            throw PasswordResetException.invalidPassword(
                    "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다."
            );
        }
    }
}
