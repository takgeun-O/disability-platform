package io.github.takgeun.iyum.auth.application;

import io.github.takgeun.iyum.member.domain.MemberStatus;

// 서비스 내부 결과
// 이후 컨트롤러에서 기존 SignupResponse로 변환하여 필요한 정보만 응답하는 용도 (비밀번호와 비밀번호 해시는 결과에 포함 X)
public record SignupResult(
        Long memberId,  // 생성된 회원 식별자. 이후 이메일 인증 처리 등에 사용
        MemberStatus status // 가입 직후 회원 상태인 PENDING
) {
}
