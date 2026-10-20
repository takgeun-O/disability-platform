package io.github.takgeun.iyum.member.application;

import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;

// 작성자 표시 이름을 결정하는 공통 클래스 추가
// 추가 배경)
// 탈퇴 서비스는 닉네임을 null로 지우므로, 기존처럼 member.getNickname()을
// 그대로 응답하면 프론트에서 정상적인 작성자 정보로 처리하지 못할 수 있음.
// nickname == null 만 보고 탈퇴 여부를 판단하지 않고
// 회원 상태를 기준으로 표시 이름을 결정하도록 함
public final class MemberDisplayName {

    private static final String WITHDRAWN_MEMBER_NAME = "탈퇴한 회원";

    private MemberDisplayName() {
    }

    public static String resolve(Member member) {
        MemberStatus status = member.getStatus();

        if (status == MemberStatus.WITHDRAWN
                || status == MemberStatus.DELETED) {
            return WITHDRAWN_MEMBER_NAME;
        }

        return member.getNickname();
    }
}
