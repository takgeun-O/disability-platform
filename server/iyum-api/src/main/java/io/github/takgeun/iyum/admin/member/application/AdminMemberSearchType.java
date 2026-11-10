package io.github.takgeun.iyum.admin.member.application;

public enum AdminMemberSearchType {

    // 회원 ID 정확히 일치
    MEMBER_ID,

    // 닉네임 부분 일치
    NICKNAME,

    // 정규화된 이메일 정확히 일치
    EMAIL
}
