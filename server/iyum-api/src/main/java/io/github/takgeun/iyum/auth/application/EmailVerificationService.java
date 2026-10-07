package io.github.takgeun.iyum.auth.application;

import io.github.takgeun.iyum.auth.domain.EmailVerificationToken;
import io.github.takgeun.iyum.auth.infrastructure.EmailVerificationTokenHasher;
import io.github.takgeun.iyum.auth.infrastructure.EmailVerificationTokenRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("[A-Za-z0-9_-]{43}");

    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailVerificationTokenHasher tokenHasher;
    private final MemberRepository memberRepository;
    private final Clock clock;

    @Transactional
    public EmailVerificationResult verify(String rawToken) {

        // 0. 원본 토큰 형식 검사
        validateTokenFormat(rawToken);

        String tokenHash = tokenHasher.hash(rawToken);

        // 1. 이 토큰의 주인이 누구인지 확인한다. (아직 잠금 없음)
        Long memberId = tokenRepository
                .findMemberIdByTokenHash(tokenHash)
                .orElseThrow(
                        InvalidEmailVerificationTokenException::new
                );

        // 2. 해당 회원 행을 잠근다.
        Member member = memberRepository
                .findByIdForUpdate(memberId)
                .orElseThrow(
                        InvalidEmailVerificationTokenException::new
                );

        // 3. 해당 토큰 행도 잠근다
        EmailVerificationToken token = tokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow(
                        InvalidEmailVerificationTokenException::new
                );

        // 4. 잠금을 확보한 상태에서 회원이나 토큰 상태를 검사하고 변경한다.
        // 잠금을 기다리는 동안 만료될 수 있으므로
        // 잠금을 얻은 뒤 현재 시각을 확인한다.
        Instant now = clock.instant();

        if (member.getStatus() != MemberStatus.PENDING
                // isUsable()에는 다음 검사가 이미 구현되어 있음.
                // - 발급 시각에 도달했는가?
                // - 만료 시각 이전인가?
                // - 아직 사용하지 않았는가?
                // - 폐기되지 않았는가?
                || !token.isUsable(now)) {
            throw new InvalidEmailVerificationTokenException();
        }

        // 두 엔티티를 함께 변경
        // 이 엔티티들은 같은 트랜잭션에서 조회되어 영속성 컨텍스트가 관리하고 있음.
        // 따라서 별도로 save()를 호출하지 않아도 변경 감지를 통해 DB에 반영된다.
        token.use(now);
        member.activeAfterEmailVerification();

        return new EmailVerificationResult(
                member.getId(),
                member.getStatus()
        );
    }

    private void validateTokenFormat(String rawToken) {
        if (rawToken == null
                || !TOKEN_PATTERN.matcher(rawToken).matches()) {
            throw new InvalidEmailVerificationTokenException();
        }
    }
}
