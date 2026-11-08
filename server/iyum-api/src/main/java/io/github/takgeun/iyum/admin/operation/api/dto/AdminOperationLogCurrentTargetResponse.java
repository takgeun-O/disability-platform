package io.github.takgeun.iyum.admin.operation.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.takgeun.iyum.admin.operation.application.AdminOperationLogCurrentTargetResult;
import jakarta.annotation.Nullable;

// 현재 대상 요약 응답 DTO
@Schema(description = "조회 시점의 현재 대상 요약")
public record AdminOperationLogCurrentTargetResponse(
        @Schema(description = "콘텐츠 작성자. 처리 관리자와 구분하며 null이 아님", requiredMode = Schema.RequiredMode.REQUIRED)
        AdminOperationLogTargetAuthorResponse author,

        // 댓글의 title도 생략하지 않고 명시적인 null로 반환하도록 설정
        @Nullable
        @JsonInclude(JsonInclude.Include.ALWAYS)
        @Schema(description = "게시글의 현재 제목. 댓글은 필드를 생략하지 않고 null", requiredMode = Schema.RequiredMode.REQUIRED, types = {"string", "null"}, nullable = true)
        String title,

        @Schema(description = "현재 본문을 공백 정리 후 최대 120 코드 포인트로 자름. 초과 시 … 추가. null이 아니며 빈 문자열 가능", requiredMode = Schema.RequiredMode.REQUIRED, example = "댓글 경험을 공유합니다.")
        String contentPreview
) {

    public static AdminOperationLogCurrentTargetResponse from(
            AdminOperationLogCurrentTargetResult result
    ) {
        return new AdminOperationLogCurrentTargetResponse(
                AdminOperationLogTargetAuthorResponse.from(
                        result.author()
                ),
                result.title(),
                result.contentPreview()
        );
    }
}
