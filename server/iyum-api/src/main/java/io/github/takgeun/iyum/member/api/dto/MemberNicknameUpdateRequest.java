package io.github.takgeun.iyum.member.api.dto;

import io.github.takgeun.iyum.member.api.validation.ValidMemberNicknameUpdateRequest;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "내 닉네임 수정 요청")
@ValidMemberNicknameUpdateRequest
public record MemberNicknameUpdateRequest(
        // 여기서는 @NotBlank, @Size, @Pattern 필요 없음.
        // 기존 MemberInputPolicy를 호출하는 검증기 하나로 일관되게 검사할 것
        @Schema(
                description = """
                        앞뒤 공백을 제거한 뒤 한글, 영문, 숫자, 밑줄로
                        2~20자여야 합니다. 예약된 닉네임은 사용할 수 없습니다.
                        """,
                example = "새로운닉네임",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String nickname
) {
}
