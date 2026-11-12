package io.github.takgeun.iyum.admin.report.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 신고 처리 완료·기각 요청")
public record AdminReportHandlingRequest(

        // handlingReason이 비어있거나 null인 경우 서비스에서 INVALID_HANDLING_REASON 처리
        @Schema(
                description = """
                        처리 사유. 필수입니다.
                        앞뒤 유니코드 공백(NBSP·전각 공백 포함)을 제거한 뒤
                        유니코드 코드 포인트 기준 1~500자여야 합니다.
                        내부 공백과 줄바꿈은 유지합니다. UTF-16 길이나 grapheme cluster 개수가 아닙니다.
                        제한은 정규화 이후 적용되며 원문 길이 제한이 아닙니다.
                        """,
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "검토 결과 필요한 운영 조치를 완료했습니다."
        )
        String handlingReason

) {
}