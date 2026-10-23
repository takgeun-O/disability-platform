package io.github.takgeun.iyum.member.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 비밀번호 변경 후에는 프론트가 로그인 유지 여부를 알아야 하므로
 * 응답 본문을 사용하는 형태로 연결한다.
 *
 * 변경 성공 + 현재 세션 유지 -> 200 응답 -> reloginRequired false
 * 변경 성공 + 세션 유지 중단 -> 200 -> reloginRequired true
 */
@Schema(description = "비밀번호 변경 성공 응답")
public record MemberPasswordChangeResponse(

        @Schema(
                description = "true이면 비밀번호 변경은 완료됐으나 "
                + "현재 세션을 유지하지 못해 재로그인이 필요합니다.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        boolean reloginRequired
) {
}
