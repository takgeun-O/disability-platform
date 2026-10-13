package io.github.takgeun.iyum.auth.session.api.dto;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 성공 후 반환하는 회원 정보")
public record LoginResponse(

        @Schema(
                description = "회원 고유 식별자",
                example = "1"
        )
        Long memberId,  // 프론트엔드가 로그인한 회원이 누구인지 식별할 수 있도록

        @Schema(
                description = "회원 이메일",
                example = "member@example.com"
        )
        String email,

        @Schema(
                description = "회원 닉네임",
                example = "타끈"
        )
        String nickname,

        @Schema(
                description = "회원 권한",
                example = "USER"
        )
        MemberRole role
) {

    // 주의) 이 메서드 자체가 비밀번호나 회원 상태를 검증하지 않음.
    // 인증과 세션 처리가 성공한 뒤에 from() 호출할 것.
    public static LoginResponse from(MemberPrincipal principal) {
        return new LoginResponse(
                principal.getMemberId(),
                principal.getUsername(),    // 여기에 이메일이 들어있음.
                principal.getNickname(),
                principal.getRole()
        );
    }
}
