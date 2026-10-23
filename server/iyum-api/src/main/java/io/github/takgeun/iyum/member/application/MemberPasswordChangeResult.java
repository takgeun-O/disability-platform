package io.github.takgeun.iyum.member.application;

import io.github.takgeun.iyum.member.domain.MemberRole;

import java.time.Instant;
import java.util.Objects;

/**
 * 표현하는 내용 : 비밀번호 변경 자체가 성공했는가? (MemberPasswordChangeSessionUpdateResult와 구분 지어야 함)
 */
// 서비스 처리 결과 정의
// sealed interface: 이 결과를 구현할 수 있는 타입을 제한하는 문법
// 같은 파일 안에 정의한 Changed, Rejected만 반환하게 된다.
// 이 성공 결과에 2가지 버전을 담은 이유는 세션 갱신에서 어떤 인증 버전으로 요청했고
// 어떤 버전으로 변경되었는지 확인하기 위해서임
public sealed interface MemberPasswordChangeResult {

    // 이번 변경에서 확정한 회원 정보와 인증 버전만 전달한다.
    // 비밀번호 원문과 해시는 포함하지 않는다.
    record Changed(
            Long memberId,
            String email,
            String nickname,
            MemberRole role,
            long previousCredentialVersion,
            long credentialVersion
    ) implements MemberPasswordChangeResult {

        @Override
        public String toString() {
            return "MemberPasswordChangeResult.Changed[REDACTED]";
        }
    }

    record Rejected(
            Reason reason,
            Instant blockedUntil
    ) implements MemberPasswordChangeResult {

        public Rejected {
            Objects.requireNonNull(reason, "거절 사유가 필요합니다.");

            // 횟수 제한일 때만 차단 종료 시각을 함께 전달한다.
            // 횟수 제한 사유인가? + 차단 종료 시각이 있는가?
            // true + true -> 통과
            // true + false -> 예외: 횟수 제한인데 종료 시각이 없음
            // false + true -> 예외: 다른 사유인데 종료 시각이 있음
            // false + false -> 통과
            // 즉 예외 없이 통과하려면
            // 1) 횟수 제한이 사유라면 종료 시각이 있어야 한다.
            // 2) 횟수 제한 사유가 아니라면 종료 시각이 없어야 한다.
            if ((reason == Reason.RATE_LIMITED) != (blockedUntil != null)) {
                throw new IllegalArgumentException(
                        "차단 종료 시각은 횟수 제한 결과에만 필요합니다."
                );
            }
        }
    }

    enum Reason {
        AUTHENTICATION_REQUIRED,
        INVALID_CURRENT_PASSWORD,
        INVALID_NEW_PASSWORD,
        CURRENT_PASSWORD_MISMATCH,
        PASSWORD_UNCHANGED,
        RATE_LIMITED
    }
}
