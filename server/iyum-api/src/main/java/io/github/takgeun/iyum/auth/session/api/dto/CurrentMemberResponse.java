package io.github.takgeun.iyum.auth.session.api.dto;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.swagger.v3.oas.annotations.media.Schema;

// 현재 회원 조회 응답 DTO
@Schema(description = "현재 로그인한 회원 정보")
public record CurrentMemberResponse(
        Long memberId,
        String email,
        String nickname,
        MemberRole role
) {

    public static CurrentMemberResponse from(
            MemberPrincipal principal
    ) {
        return new CurrentMemberResponse(
                principal.getMemberId(),
                principal.getUsername(),
                principal.getNickname(),
                principal.getRole()
        );
    }
}
