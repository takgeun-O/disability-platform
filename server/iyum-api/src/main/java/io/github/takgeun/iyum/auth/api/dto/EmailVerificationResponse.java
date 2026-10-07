package io.github.takgeun.iyum.auth.api.dto;

import io.github.takgeun.iyum.auth.application.EmailVerificationResult;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {
 *   "status": "ACTIVE"
 * }
 */
@Schema(
        description = "이메일 인증 결과. "
        + "ACTIVE는 회원 활성화를 의미하며 로그인 상태가 아닙니다."
)
public record EmailVerificationResponse(

        @Schema(
                description = "인증 완료 후 회원 상태",
                example = "ACTIVE",
                allowableValues = {"ACTIVE"},
                // 클라이언트가 요청할 때 해당 필드를 반드시 포함해야 한다고 문서화
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        MemberStatus status
) {

    public static EmailVerificationResponse from(
            EmailVerificationResult result
    ) {
        // result에는 memberId도 있지만 HTTP 응답에는 프론트엔드가 필요로 하는 status만 포함시키기
        return new EmailVerificationResponse(result.status());
    }
}
