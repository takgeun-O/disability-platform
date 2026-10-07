package io.github.takgeun.iyum.auth.application;

import io.github.takgeun.iyum.member.domain.MemberStatus;

/**
 * 인증 성공 후 서비스가 반환할 객체
 * 이 객체는 서비스 내부 결과이므로, 추후 Controller에서 필요한 필드만 HTTP 응답 DTO로 옮기면 됨.
 */
public record EmailVerificationResult(
        Long memberId,
        MemberStatus status
) {
}
