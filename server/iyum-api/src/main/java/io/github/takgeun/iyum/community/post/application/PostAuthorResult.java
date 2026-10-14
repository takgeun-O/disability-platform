package io.github.takgeun.iyum.community.post.application;

import io.github.takgeun.iyum.member.domain.Member;

/**
 * 게시글 조회 시 서비스가 반환하는 작성자 정보
 * 회원 엔티티 대신 공개에 필요한 값만 담는다.
 */
public record PostAuthorResult(
        Long memberId,
        String nickname
) {

    public static PostAuthorResult from(Member member) {
        return new PostAuthorResult(
                member.getId(),
                member.getNickname()
        );
    }
}
