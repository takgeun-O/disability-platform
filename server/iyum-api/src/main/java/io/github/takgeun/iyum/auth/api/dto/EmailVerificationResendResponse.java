package io.github.takgeun.iyum.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description =
                "요청 접수 결과. 계정 존재·상태·제한 여부와 실제 발송·도착 여부를 나타내지 않습니다."
)
public record EmailVerificationResendResponse(
        @Schema(
                allowableValues = {"ACCEPTED"},
                example = "ACCEPTED"
        ) String status
) {
    public static EmailVerificationResendResponse accepted() {
        return new EmailVerificationResendResponse("ACCEPTED");
    }
}
