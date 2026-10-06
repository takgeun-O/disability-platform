package io.github.takgeun.iyum.auth.application;

import io.github.takgeun.iyum.auth.domain.EmailVerificationToken;
import io.github.takgeun.iyum.auth.infrastructure.EmailVerificationTokenGenerator;
import io.github.takgeun.iyum.auth.infrastructure.EmailVerificationTokenHasher;
import io.github.takgeun.iyum.auth.infrastructure.EmailVerificationTokenRepository;
import io.github.takgeun.iyum.global.config.EmailVerificationProperties;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class EmailVerificationTokenIssueService {

    private final MemberRepository memberRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailVerificationTokenGenerator tokenGenerator;
    private final EmailVerificationTokenHasher tokenHasher;
    private final EmailVerificationProperties properties;
    private final Clock clock;

    /**
     * 토큰 한 건을 발급하는 내부 기능 (즉, issue()를 여러 번 호출하면 여러 토큰이 생길 수 있음.)
     */
    @Transactional
    public IssuedEmailVerificationToken issue(Long memberId) {
        if(memberId == null || memberId <= 0) {
            throw new IllegalArgumentException(
                    "유효한 회원 ID가 필요합니다."
            );
        }

        // 회원 조회와 상태 확인
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 회원입니다."
                ));

        if(member.getStatus() != MemberStatus.PENDING) {
            throw new IllegalStateException(
                    "이메일 인증 대기 상태의 회원만 토큰을 발급할 수 있습니다."
            );
        }

        // 원본과 해시 생성
        String rawToken = tokenGenerator.generate();    // 원본 (발급 결과 객체에 담을 것)
        String tokenHash = tokenHasher.hash(rawToken);  // 엔티티에 담을 것 (엔티티에는 tokenHash만 전달)

        // 발급 시각과 만료 시각 계산
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.tokenTtl());

        EmailVerificationToken token = EmailVerificationToken.issue(
                member.getId(),
                tokenHash,
                issuedAt,
                expiresAt
        );

        // 저장과 트랜잭션
        /**
         * 참고로 save() 호출 자체가 트랜잭션 커밋을 뜻하지 않음.
         * - 외부 트랜잭션이 없으면 Spring이 이 서비스의 호출의 트랜잭션을 시작하고 정상 완료 시 커밋
         * - 나중에 @Transactional이 있는 SignupService에서 이 빈을 호출하면 기본적으로 같은 트랜잭션에 참여한다.
         * - 따라서 이후 회원가입 처리에서 실패하면 토큰 저장도 함께 롤백 가능함!
         */
        tokenRepository.save(token);

        return new IssuedEmailVerificationToken(
                member.getId(),
                rawToken,
                expiresAt
        );
    }
}
