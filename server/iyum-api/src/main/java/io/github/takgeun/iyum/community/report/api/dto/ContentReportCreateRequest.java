package io.github.takgeun.iyum.community.report.api.dto;

import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {
 *   "reasonType": "SPAM_ADVERTISING",
 *   "reasonDetail": "동일한 광고를 반복해서 게시하고 있습니다."
 * }
 */
@Schema(description = "게시글·댓글 신고 접수 요청")
public record ContentReportCreateRequest(

        @Schema(
                description = """
                        신고 사유 유형. 필수입니다.
                        OTHER를 선택하면 reasonDetail도 입력해야 합니다.
                        """,
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "SPAM_ADVERTISING",
                allowableValues = {
                        "SPAM_ADVERTISING",
                        "ABUSE_HATE",
                        "PERSONAL_INFORMATION",
                        "DUPLICATE_FLOODING",
                        "OFF_TOPIC",
                        "OTHER"
                }
        )
        ContentReportReasonType reasonType,

        @Schema(
                description = """
                        신고 상세 설명.
                        OTHER에서는 필수이며, 나머지 유형에서는 생략할 수 있습니다.

                        String.strip()으로 앞뒤 Character.isWhitespace 공백을 제거한 뒤 Unicode 코드 포인트 기준
                        500자 이하여야 합니다.
                        내부 공백과 줄바꿈은 보존합니다. NBSP(U+00A0)는 제거하지 않습니다.
                        UTF-16 길이나 grapheme cluster 개수가 아닙니다.

                        OTHER가 아닌 경우 공백뿐인 값은 상세 설명 없음으로 처리합니다.
                        """,
                types = {"string", "null"},
                example = "동일한 광고를 반복해서 게시하고 있습니다."
        )
        String reasonDetail

) {
}
