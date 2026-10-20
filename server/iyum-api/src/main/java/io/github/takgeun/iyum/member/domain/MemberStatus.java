package io.github.takgeun.iyum.member.domain;

public enum MemberStatus {

    PENDING,    // 이메일 인증 대기
    ACTIVE,     // 활성 회원
    RESTRICTED, // 일부 활동 제한
    SUSPENDED,  // 이용 정지
    WITHDRAWN,  // 이용은 종료됐지만 개인정보 정리가 완료되지 않은 상태 (이번 MVP에서는 별도 저장 안함)
    DELETED     // 회원 계정의 개인정보 정리까지 완료된 상태
}
