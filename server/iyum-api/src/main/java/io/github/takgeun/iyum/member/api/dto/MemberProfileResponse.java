package io.github.takgeun.iyum.member.api.dto;

import io.github.takgeun.iyum.member.application.MemberProfileResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "현재 로그인한 회원의 최신 프로필")
public record MemberProfileResponse(

        @Schema(description = "회원 ID", example = "3")
        Long memberId,

        @Schema(
                description = "이메일",
                example = "member@example.com"
        )
        String email,

        @Schema(description = "닉네임", example = "타끈")
        String nickname,

        @Schema(description = "가입 시각")
        Instant createdAt
) {

    public static MemberProfileResponse from(
            MemberProfileResult result
    ) {
        return new MemberProfileResponse(
                result.memberId(),
                result.email(),
                result.nickname(),
                result.createdAt()
        );
    }
}
