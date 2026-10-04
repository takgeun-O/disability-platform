package io.github.takgeun.iyum.member.domain;

public enum MemberStatus {

    PENDING,    // 이메일 인증 대기
    ACTIVE,     // 활성 회원
    RESTRICTED, // 일부 활동 제한
    SUSPENDED,  // 이용 정지
    WITHDRAWN,  // 탈퇴
    DELETED     // 개인정보 삭제 처리 완료
}
