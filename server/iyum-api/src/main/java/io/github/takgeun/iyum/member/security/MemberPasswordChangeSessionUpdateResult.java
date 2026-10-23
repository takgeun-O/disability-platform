package io.github.takgeun.iyum.member.security;

/**
 * 비밀번호 변경이 커밋된 뒤 수행하는 세션 갱신의 결과
 *
 * 두 결과 모두 비밀번호 변경 자체는 완료된 상태
 *
 * 표현하는 내용 : 변경 성공 후 현재 로그인을 유지했는가? (MemberPasswordChangeResult와 구분지어야 함)
 * MemberPasswordChangeSessionUpdate의 update()에서 사용할 것
 */
public enum MemberPasswordChangeSessionUpdateResult {

    // 현재 세션에 변경 후 인증 정보를 저장하고 확인했다.
    SESSION_RETAINED,

    // 비밀번호는 변경됐지만 현재 요청의 로그인 유지에 실패했다.
    // 주의할 점은 이걸 받았다고 해서 비밀번호 변경 서비스를 다시 호출하면 안된다.
    // 이미 변경된 비밀번호로 다시 로그인하도록 안내 필요
    RELOGIN_REQUIRED
}
