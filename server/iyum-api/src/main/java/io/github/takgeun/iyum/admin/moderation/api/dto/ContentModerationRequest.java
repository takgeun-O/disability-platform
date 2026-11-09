package io.github.takgeun.iyum.admin.moderation.api.dto;

import io.github.takgeun.iyum.admin.moderation.domain.ModerationReasonType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 여기서 별도의 @NotNull, @Size를 추가하지 않는다.
 * 필수값 여부는 서비스 -> 도메인 정책에서 검사할 것.
 *
 * @Size의 문자열 길이 기준과 정책상 정한 Unicode 코드 포인트 기준이 다르므로
 * 중복 검증하지 않는다.
 */
public record ContentModerationRequest(

        @Schema(
                description = """
                        처리 사유 유형. 필수입니다.

                        숨김:
                        SPAM_ADVERTISING, ABUSE_HATE,
                        PERSONAL_INFORMATION, DUPLICATE_FLOODING,
                        OFF_TOPIC, OTHER

                        복원:
                        MODERATION_ERROR, NO_VIOLATION, OTHER

                        LEGACY는 기존 운영 기록을 구분하기 위한 값이며,
                        새로운 조치 요청에는 사용할 수 없습니다.
                        """,
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "SPAM_ADVERTISING",
                allowableValues = {
                        "SPAM_ADVERTISING",
                        "ABUSE_HATE",
                        "PERSONAL_INFORMATION",
                        "DUPLICATE_FLOODING",
                        "OFF_TOPIC",
                        "MODERATION_ERROR",
                        "NO_VIOLATION",
                        "OTHER"
                }
        )
        ModerationReasonType reasonType,

        @Schema(
                description = """
                        처리 사유 상세 내용.

                        OTHER를 선택하면 필수입니다.
                        다른 유형에서는 생략할 수 있으며,
                        공백뿐인 값은 상세 내용 없음으로 처리합니다.

                        입력한 경우 앞뒤 공백을 제거한 뒤
                        Unicode 코드 포인트 기준 500자 이하여야 합니다.
                        """,
                example = "동일한 광고를 반복해서 게시했습니다."
        )
        String reasonDetail
) {
}