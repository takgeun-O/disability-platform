package io.github.takgeun.iyum.community.report.api.dto;

import io.github.takgeun.iyum.community.report.application.ContentReportCreateResult;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 성공 시 201 Created와 다음 형태 반환
 * {
 *   "reportId": 1,
 *   "status": "RECEIVED",
 *   "createdAt": "2026-11-12T10:00:00Z"
 * }
 */
@Schema(description = "신고 접수 결과")
public record ContentReportCreateResponse(

        @Schema(
                description = "접수된 신고 ID",
                type = "integer",
                types = {"integer"},
                format = "int64",
                example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Long reportId,

        @Schema(
                description = "신규 신고의 상태. 접수 성공 시 RECEIVED",
                allowableValues = {"RECEIVED"},
                example = "RECEIVED",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        ContentReportStatus status,

        @Schema(
                description = "신고 접수 시각. UTC ISO 8601",
                type = "string",
                format = "date-time",
                example = "2026-11-12T10:00:00Z",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Instant createdAt

) {

    public static ContentReportCreateResponse from(
            ContentReportCreateResult result
    ) {
        return new ContentReportCreateResponse(
                result.reportId(),
                result.status(),
                result.createdAt()
        );
    }
}
