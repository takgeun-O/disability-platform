package io.github.takgeun.iyum.admin.report.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.takgeun.iyum.admin.report.application.AdminReportItemResult;
import io.github.takgeun.iyum.admin.report.application.AdminReportTargetType;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import jakarta.annotation.Nullable;

import java.time.Instant;

/**
 * 제목과 본문 미리보기는 신고 당시 스냅샷이다.
 * currentAuthor는 현재 대상 콘텐츠의 작성자다.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminReportItemResponse(
        @Schema(
                description = "신고 ID. 신고 한 건당 한 항목",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64"
        )
        Long reportId,
        @Schema(
                description = "신고 대상 종류",
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"POST", "COMMENT"}
        )
        AdminReportTargetType targetType,
        @Schema(
                description = "게시글 ID 또는 댓글 ID",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64"
        )
        Long targetId,
        @Schema(
                description = "신고 상태: 접수, 처리 완료, 기각",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        ContentReportStatus status,
        @Schema(
                description = "신고 접수 사유 유형",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        ContentReportReasonType reasonType,
        @Schema(
                description = "신고 당시 게시글 제목. 댓글은 명시적 null",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"}
        )
        @Nullable String titleSnapshot,
        @Schema(
                description = "신고 당시 본문: 유니코드 연속 공백을 하나로 정리하고 120 코드 포인트 초과 시 … 추가",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String contentSnapshotPreview,
        @Schema(
                description = "신고 접수 시각. UTC ISO 8601",
                requiredMode = Schema.RequiredMode.REQUIRED,
                format = "date-time"
        )
        Instant createdAt,
        @Schema(
                description = "현재 콘텐츠 작성자. 대상 조회 불가 시 명시적 null",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @Nullable AdminReportTargetAuthorResponse currentAuthor
) {

    public static AdminReportItemResponse from(
            AdminReportItemResult result
    ) {
        return new AdminReportItemResponse(
                result.reportId(),
                result.targetType(),
                result.targetId(),
                result.status(),
                result.reasonType(),
                result.titleSnapshot(),
                result.contentSnapshotPreview(),
                result.createdAt(),
                result.currentAuthor() == null
                        ? null
                        : AdminReportTargetAuthorResponse.from(
                        result.currentAuthor()
                )
        );
    }
}
