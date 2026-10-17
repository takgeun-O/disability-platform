package io.github.takgeun.iyum.community.comment.application;

import io.github.takgeun.iyum.member.domain.Member;

public record CommentAuthorResult(
        Long memberId,
        String nickname
) {

    public static CommentAuthorResult from(Member member) {
        return new CommentAuthorResult(
                member.getId(),
                member.getNickname()
        );
    }
}
