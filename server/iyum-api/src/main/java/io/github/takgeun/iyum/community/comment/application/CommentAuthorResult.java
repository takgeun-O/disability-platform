package io.github.takgeun.iyum.community.comment.application;

import io.github.takgeun.iyum.member.application.MemberDisplayName;
import io.github.takgeun.iyum.member.domain.Member;

public record CommentAuthorResult(
        Long memberId,
        String nickname
) {

    public static CommentAuthorResult from(Member member) {
        return new CommentAuthorResult(
                // memberId는 기존 값 유지.
                // DB에서 회원과 컨텐츠의 연결을 유지하고,
                // 회원 탈퇴 구현 시 기존 API 구조도 유지하기 위한 선택
                // 같은 회원의 콘텐츠를 연결할 수 있는 ID는 남으니 완전한 익명화는 아님을 유념할 것.
                member.getId(),
                MemberDisplayName.resolve(member)
        );
    }
}
