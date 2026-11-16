package io.github.takgeun.iyum.admin.report.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description = "관리자 신고 숨김·처리 완료, 추가 조치 없는 처리 완료 및 기각 요청"
)
public record AdminReportHandlingRequest(

        // null·빈 문자열·공백만 있는 값은
        // 서비스에서 INVALID_HANDLING_REASON으로 처리한다.
        @Schema(
                description = """
                        처리 사유. 필수입니다.
                        앞뒤 유니코드 공백(NBSP·전각 공백 포함)을 제거한 뒤
                        유니코드 코드 포인트 기준 1~500자여야 합니다.
                        내부 공백과 줄바꿈은 유지합니다.
                        UTF-16 길이나 grapheme cluster 개수가 아닙니다.
                        제한은 정규화 이후 적용되며 원문 길이 제한이 아닙니다.
                        """,
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "운영 정책 위반을 확인하여 숨김 처리합니다."
        )
        String handlingReason

) {
}