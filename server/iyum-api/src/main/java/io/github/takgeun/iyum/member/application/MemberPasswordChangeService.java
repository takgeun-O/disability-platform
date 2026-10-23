package io.github.takgeun.iyum.member.application;

import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetTokenRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberPasswordChangeFailure;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberPasswordChangeFailureRepository;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.regex.Pattern;

import static io.github.takgeun.iyum.member.application.MemberPasswordChangeResult.Reason.*;

/**
 * 큰 흐름 :요청을 확인하고 -> 안전하게 비밀번호를 변경하고 -> 변경 결과를 DB에 확정해서 반환
 * <p>
 * 예시) 회원이 현재 비밀번호를 한 번 틀렸다가 다시 정확히 입력하는 상황 가정
 * (참고: 실제 DB에는 현재 비밀번호 원문 대신 비밀번호 해시가 저장되어 있음)
 * - 회원 ID : 10
 * - 회원 상태 : ACTIVE
 * - 현재 비밀번호 : OldPass123!
 * - DB의 인증 버전 : 3
 * - 로그인 세션의 인증 버전 : 3
 * - 변경할 비밀번호 : NewPass456!
 * - 기존 실패 기록 : 없음
 * - 기존 재설정 토큰 : 미사용 토큰 1개
 */
@Service
public class MemberPasswordChangeService {

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("(?s)(?=.*[A-Za-z])(?=.*[0-9]).*");

    private final MemberRepository memberRepository;
    private final MemberPasswordChangeFailureRepository failureRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;


    public MemberPasswordChangeService(
            MemberRepository memberRepository,
            MemberPasswordChangeFailureRepository failureRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            Clock clock,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager
    ) {
        this.memberRepository = memberRepository;
        this.failureRepository = failureRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.events = events;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * 첫 번째 요청: 현재 비밀번호를 잘못 입력
     * - 컨트롤러가 아래와 같이 호출
     * service.change(
     * 10L, // 요청 본문이 아닌 인증된 MemberPrincipal에서 가져오는 값
     * 3L,  // 요청 본문이 아닌 인증된 MemberPrincipal에서 가져오는 값
     * "WrongPass123!",
     * "NewPass456!"
     * );
     * 두 번째 요청 예시: 현재 비밀번호를 정확히 입력
     * service.change(
     * 10L,
     * 3L,
     * "OldPass123!",
     * "NewPass456!"
     * );
     */
    // propagation = Propagation.NEVER
    // Spring 프록시를 통한 호출에서 이미 진행 중인 트랜잭션에 들어온 상태로
    // 이 메서드를 호출하지 마라는 의미. (단, 내부에서 새 트랜잭션을 시작하는 것까지 금지하지 않음)
    // TransactionTemplate이 직접 트랜잭션을 시작하고 커밋까지 마쳐서
    // 결과 반환 시점을 통제하기 위함
    @Transactional(propagation = Propagation.NEVER)
    public MemberPasswordChangeResult change(
            Long memberId,
            long expectedCredentialVersion,
            String currentPassword,
            String newPassword
    ) {

        /**
         * change()가 입력 형식 확인
         *  - 첫 번째 요청 : 회원 ID와 버전이 정상이라 통과
         */
        if (memberId == null
                || memberId <= 0
                || expectedCredentialVersion < 0) {
            return reject(AUTHENTICATION_REQUIRED);
        }

        /**
         * 비밀번호 입력 조건 검사
         *  - 첫 번째 요청 : WrongPass123!은 현재 비밀번호와 일치하지는 않지만 입력 형식은 정상 -> 통과
         */
        if (!validCurrentPassword(currentPassword)) {
            return reject(INVALID_CURRENT_PASSWORD);
        }
        if (!validNewPassword(newPassword)) {
            return reject(INVALID_NEW_PASSWORD);
        }

        /**
         * 트랜잭션 시작 : 이 안에서 수행하는 DB 작업들을 하나의 트랜잭션으로 묶는다.
         */
        return Objects.requireNonNull(
                transaction.execute(
                        status -> changeInTransaction(
                                memberId,
                                expectedCredentialVersion,
                                currentPassword,
                                newPassword
                        ))
        );
    }

    private MemberPasswordChangeResult changeInTransaction(
            Long memberId,
            long expectedCredentialVersion,
            String currentPassword,
            String newPassword
    ) {
        /**
         * 회원을 잠근 다음, 변경 가능한 상태인지 다시 확인한다.
         *  - 첫 번째 요청 예시 : 회원 10번 행을 잠근다. 그러면 같은 회원 행의 잠금이 필요한 다른 변경 요청은 이 트랜잭션이 끝날 때까지 기다린다.
         */
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElse(null);  // 바로 아래의 member == null 검사로 처리하려는 의도

        // 보안 필터를 통과한 이후 다른 요청이 상태나 버전을 바꿨을 수 있다.
        // 따라서 잠금을 획득한 뒤 반드시 다시 검사한다.
        /**
         *  - 첫 번째 요청 예시 : 회원이 존재하고, ACTIVE이고, DB와 세션의 버전이 모두 3이니까 통과
         */
        if (member == null
                || member.getStatus() != MemberStatus.ACTIVE
                || member.getCredentialVersion() != expectedCredentialVersion) {
            return reject(AUTHENTICATION_REQUIRED);
        }

        Instant now = now();

        /**
         * 차단 여부 확인하고, 틀린 비밀번호를 기록한다.
         *  - 첫 번째 예시 기준 : 기존 실패 기록이 없으므로 차단 종료 시각도 없음.
         *      따라서 차단 검사를 통과하고, 오래된 실패 기록 정리
         */
        Instant blockedUntil = failureRepository.findBlockedUntil(memberId)
                .orElse(null);

        if (blockedUntil != null && now.isBefore(blockedUntil)) {
            // 차단 중에는 비밀번호를 비교하거나 실패 기록을 추가하지 않는다.
            // 기존 차단 종료 시각도 연장하지 않는다.
            return new MemberPasswordChangeResult.Rejected(
                    RATE_LIMITED,
                    blockedUntil
            );
        }

        /**
         *  - 첫 번째 예시 기준 : 지울 기록이 없다.
         */
        failureRepository.deleteExpiredByMemberId(
                memberId,
                now.minus(MemberPasswordChangeFailure.FAILURE_WINDOW),
                now
        );

        /**
         * 이제서야 실제 비밀번호 비교.
         *  - 첫 번째 예시 기준: WrongPass123! 은 저장된 비밀번호와 다르므로 false
         *      -> return recordPasswordMismatch(memberId);
         *      해당 메서드는 다음 작업을 한다
         *      - 비밀번호 비교가 끝난 시점의 시간을 구한다.
         *      - 집계 구간 안의 기존 실패 횟수를 조회한다.
         *      - 이번 실패 기록을 생성하고 저장한다.
         *      - 차단 기준에 도달했다면 RATE_LIMITED,
         *      - 아니라면 CURRENT_PASSWORD_MISMATCH 반환
         *     여기서 중요한 건 비밀번호 변경은 거절됐지만 실패는 기록되어야 한다.
         *    최종 반환 결과)
         *      - 비밀번호 : 기존 비밀번호 유지
         *      - 인증 버전 : 3 유지
         *      - 실패 기록 : 1개 저장
         *      - 반환 결과 : CURRENT_PASSWORD_MISMATCH
         *  - 두 번째 예시 기준 : 비밀번호 비교 성공
         */
        if (!passwordEncoder.matches(
                currentPassword,
                member.getPasswordHash()
        )) {
            return recordPasswordMismatch(memberId);
        }

        /**
         * 두 번째 예시 기준: 새 비밀번호가 기존 비밀번호와 같은지 검사 -> false
         *  만약 바꾸려는 비밀번호가 현재 비밀번호와 같았으면 reject(PASSWORD_UNCHANGED) 반환
         */
        // 현재 비밀번호가 맞는 것을 확인한 뒤,
        // 새 비밀번호가 기존 비밀번호와 같은지도 해시 비교로 검사한다.
        if (passwordEncoder.matches(
                newPassword,
                member.getPasswordHash()
        )) {
            return reject(PASSWORD_UNCHANGED);
        }

        /**
         * 비밀번호 변경과 관련된 DB 작업을 함께 수행한다.
         * 먼저 새 비밀번호를 해시로 바꾼다.
         */
        String newPasswordHash = passwordEncoder.encode(newPassword);

        // 회원 -> 재설정 토큰 순서로 잠근다.
        // 기존 재설정 서비스와 같은 잠금 순서를 유지한다.
        /**
         * 미사용 재설정 토큰을 잠금 조회한다.
         * 항상 회원 -> 재설정 토큰 순서로 잠금을 얻는 건, 다른 비밀번호 관련 서비스와 잠금 순서를 맞춰
         * 교착 상태의 위험을 줄이기 위한 것.
         */
        var tokens = tokenRepository.findUnconsumedForUpdate(memberId);

        /**
         * 이제 실제 변경을 수행한다.
         */
        Instant changedAt = now();

        // 이 메서드는 기존 재설정에서도 사용하는 도메인 동작
        // 비밀번호 해시를 바꾸고 credentialVersion을 정확히 1 증가시킨다.
        /**
         * 비밀번호 해시 변경, 인증 버전 3 -> 4 갱신
         */
        member.resetPassword(newPasswordHash);

        // 만료 여부와 관계없이 아직 사용·폐기되지 않은 패스워드 리셋 토큰을 폐기한다.
        /**
         * 기존 재설정 토큰 폐기
         */
        tokens.forEach(token -> token.revoke(changedAt));

        // 변경 성공 시 실패 기록 초기화도 같은 트랜잭션에 포함한다.
        /**
         * 첫 요청(비밀번호 불일치)에서 남긴 실패 기록 삭제
         * 이 때 회원과 토큰이 영속 상태라면 객체의 값을 변경한 내용은
         * JPA의 변경 감지로 반영된다.
         */
        failureRepository.deleteAllByMemberId(memberId);

        // SQL을 실행해 저장 오류를 확인한다.
        // flush는 커밋이 아니므로 이후 실패하면 함께 롤백한다.
        /**
         * SQL 실행 -> 커밋 전에 오류 발생 시 앞의 변경도 함께 롤백됨.
         */
        memberRepository.flush();

        // AFTER_COMMIT 리스너를 연결할 이벤트
        /**
         * 이벤트 발행
         * 이 이벤트에 AFTER_COMMIT 트랜잭션 이벤트 리스너를 연결했다면
         * 해당 리스너는 커밋 성공 후 실행된다.
         */
        events.publishEvent(new MemberPasswordChanged(
                memberId,
                member.getEmail(),
                changedAt
        ));

        // DB의 최신 버전을 나중에 다시 조회해서 덮어쓰지 않고,
        // 이번 변경이 만들어 낸 정확한 버전을 반환한다.
        /**
         * 비밀번호: 새 비밀번호의 해시로 변경
         * 인증 버전: 4
         * 기존 재설정 토큰: 폐기
         * 실패 기록: 삭제
         * 반환 결과: Changed, 이전 버전 3, 변경 버전 4
         */
        return new MemberPasswordChangeResult.Changed(
                memberId,
                member.getEmail(),
                member.getNickname(),
                member.getRole(),
                expectedCredentialVersion,  // 3
                member.getCredentialVersion()   // 4
        );
    }

    private MemberPasswordChangeResult recordPasswordMismatch(
            Long memberId
    ) {
        // 비밀번호 비교에 걸린 시간을 반영하여 실패 시각을 다시 확인한다.
        Instant failedAt = now();

        // 이번 실패 시각을 기준으로 집계 구간 안의 기존 실패 횟수를 조회한다.
        long previousFailures = failureRepository.countFailuresInWindow(
                memberId,
                failedAt.minus(MemberPasswordChangeFailure.FAILURE_WINDOW),
                failedAt
        );

        MemberPasswordChangeFailure failure =
                MemberPasswordChangeFailure.record(
                        memberId,
                        failedAt,
                        previousFailures
                );

        failureRepository.saveAndFlush(failure);

        // 예외를 던지지 않고 결과로 반환해야 실패 기록이 커밋된다.
        if (failure.getBlockedUntil() != null) {
            return new MemberPasswordChangeResult.Rejected(
                    RATE_LIMITED,
                    failure.getBlockedUntil()
            );
        }

        return reject(CURRENT_PASSWORD_MISMATCH);
    }

    private boolean validNewPassword(String password) {
        return validCurrentPassword(password)
                && password.length() >= 8
                && PASSWORD_PATTERN.matcher(password).matches();
    }

    private boolean validCurrentPassword(String password) {
        return password != null
                && !password.isBlank()
                && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    private MemberPasswordChangeResult.Rejected reject(
            MemberPasswordChangeResult.Reason reason
    ) {
        return new MemberPasswordChangeResult.Rejected(reason, null);
    }

    private Instant now() {
        // PostgreSQL 저장 정밀도에 맞춰 나노초 단위의 경계 차이를 줄인다.
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
