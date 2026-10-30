package io.github.takgeun.iyum.admin.moderation.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ContentModerationRequest(
        @Schema(
                description = """
                        처리 사유.
                        앞뒤 공백을 제거한 뒤 Unicode 코드 포인트 기준
                        1자 이상 500자 이하여야 합니다.
                        """,
                example = "개인정보가 포함되어 있어 숨김 처리합니다."
        )
        String reason
) {
}
