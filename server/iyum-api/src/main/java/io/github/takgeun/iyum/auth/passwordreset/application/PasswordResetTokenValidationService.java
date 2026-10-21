package io.github.takgeun.iyum.auth.passwordreset.application;

import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenHasher;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenRepository;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class PasswordResetTokenValidationService {
    private final PasswordResetTokenRepository tokenRepository;
    private final MemberRepository memberRepository;
    private final PasswordResetTokenHasher tokenHasher;
    private final Clock clock;

    // 링크 열람은 변경이 아니다. 읽기 전용으로 조회하며 쓰기 잠금이나 토큰 소비를 하지 않는다.
    // 이 결과는 현재 시점의 안내일 뿐이다. 완료 API는 잠금 획득 후 반드시 다시 검증한다.
    @Transactional(readOnly = true)
    public void validate(String rawToken) {

        PasswordResetTokenInput.validate(rawToken);

        // DB에는 원본 대신 해시를 저장한다. 요청 URL/원본 토큰은 로그나 예외에 넣지 않는다.
        var token = tokenRepository.findByTokenHash(
                        tokenHasher.hash(rawToken)
                )
                .orElseThrow(PasswordResetException::invalidToken);
        if (!token.isUsable(clock.instant())) {
            throw PasswordResetException.invalidToken();
        }
        // 토큰이 남아 있어도 탈퇴·제한된 회원에게 비밀번호 입력을 안내하면 안 된다.
        if (!memberRepository.existsByIdAndStatus(
                token.getMemberId(), MemberStatus.ACTIVE)
        ) {
            throw PasswordResetException.invalidToken();
        }
    }
}
