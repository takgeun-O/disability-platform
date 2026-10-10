package io.github.takgeun.iyum.auth.application;

import io.github.takgeun.iyum.auth.infrastructure.EmailVerificationTokenRepository;
import io.github.takgeun.iyum.global.config.EmailVerificationProperties;
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

/**
 * 이메일 인증 메일 재전송 조건을 확인하고
 * 조건을 충족하면 기존 토큰을 폐기한 뒤
 * 새 토큰과 메일 발송 이벤트를 만드는 서비스
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationResendService {

    private final MemberRepository memberRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailVerificationTokenIssueService issueService;
    private final EmailVerificationProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    // 1. 트랜잭션 시작
    // 이번 처리에서 일어나는 기존 토큰 폐기와 새 토큰 저장을 하나의 작업으로 묶는다.
    @Transactional
    public void resend(String email) {
        // 회원 엔티티를 미리 읽지 않는다.
        // 인증과 동일하게 회원 → 토큰 순으로 잠근다.
        var member = memberRepository
                // 2. 이메일을 정규화하고 회원을 잠그면서 조회한다.
                .findByEmailForUpdate(
                        MemberInputPolicy.normalizeEmail(email)
                )
                .orElse(null);

        // 3. 재전송 대상인지 확인한다.
        // 해당 이메일의 회원이 없음
        // 이미 인증된 ACTIVE 회원 등, PENDING이 아닌 회원은 그대로 종료
        if (member == null
                || member.getStatus() != MemberStatus.PENDING) {
            return;
        }

        // 4. 잠금을 얻은 후의 현재 시각을 구한다.
        // 잠금을 얻기까지 기다렸을 수 있기 때문에 잠금 획득 후의 시각으로 재전송 조건을 판단한다.
        // 예) 16:00:00에 요청이 들어왔지만 잠금을 얻은 시각은 16:00:05라면 후자의 시각을 기준으로 검사
        Instant now = clock.instant();

        // 5. 마지막 발급 이후 최소 간격이 지났는지 확인한다.
        var latest = tokenRepository.findLatestIssuedAt(
                member.getId()
        );

        // 정책: PENDING만 발급, 최초 가입 포함 60초 간격·최근 1시간 최대 5회입니다.
        // 마지막 발급이 있고, 아직 다음 발급 가능 시각 전이라면 종료한다.
        if (latest.isPresent()
                && now.isBefore(latest.get().plus(properties.resendMinInterval())
        )) {
            return;
        }

        // 이동 구간 (now - 1시간, now].
        // 6. 최근 1시간의 발급 횟수를 확인하여 정확히 1시간 전의 발급은 제외한다.
        if (tokenRepository.countByMemberIdAndCreatedAtGreaterThan(
                member.getId(),
                now.minus(Duration.ofHours(1))
        ) >= properties.resendMaxPerHour()) {
            return;
        }

        // 7. 기존 미사용 토큰을 폐기한다.
        // 조회된 기존 토큰 각각에 revoke(now)를 호출한다. (새 인증 메일을 발급한 뒤 예전 메일의 토큰이 계속 사용되지 않도록 하는 게 목적)
        // findUnconsumedForUpdate()에서 조회한 토큰이 영속 상태의 JPA 엔티티이고
        // revoke()가 필도를 변경한다면 별도의 save() 없이도 변경 감지를 통해 DB에 반영된다.
        tokenRepository
                .findUnconsumedForUpdate(member.getId())
                .forEach(token -> token.revoke(now));

        // 8. 새 토큰을 발급하고 저장한다.
        //  1) 회원이 인증 대기 상태인지 확인
        //  2) 새로운 원본 토큰 생성
        //  3) 발급 및 만료 시각 계산
        //  4) DB에 토큰 해시 저장
        //  5) 원본 토큰이 포함된 발급 결과 반환
        // 참고로 issue()가 기본 전파 속성의 @Transactional을 사용하므로
        // 이번 issue() 호출에서는 resend()의 트랜잭션에 참여한다.
        // -> 따라서 새 토큰 저장이 실패하면 기존 토큰 폐기도 함께 롤백
        var issued = issueService.issue(member.getId());

        // events.publishEvent(...) : Spring한테 "이 이벤트가 발생했으니, 등록된 처리 대상에게 전달해줘" 라고 알림
        // 여기 서비스에서는 이벤트만 발행하면 Spring이 어떤 리스너가 처리해야 할 지 관리한다.
        // 즉, 여기서 실제로 메일이 발송 처리되는 건 아니고 리스너에서 트랜잭션만 정상 처리되면 실제로 메일 발송 처리함.
        events.publishEvent(
                // 이벤트 객체 생성
                // 이렇게 이벤트 객체를 생성하는 것만으로는 리스너가 실행되지 않는다.
                // publishEvent()로 Spring에 전달하는 과정이 필요하다.
                new EmailVerificationMailRequested(
                        member.getEmail(),  // 수신자 이메일
                        issued  // 새 원본 토큰과 만료 시각 등의 발급 결과
                )
        );
    }
}