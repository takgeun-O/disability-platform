package io.github.takgeun.iyum.admin.report.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.takgeun.iyum.admin.report.application.AdminReportDetailResult;
import io.github.takgeun.iyum.admin.report.application.AdminReportTargetType;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import jakarta.annotation.Nullable;

import java.time.Instant;

/**
 * 신고 당시 스냅샷과 현재 콘텐츠를 함께 제공한다.
 * 현재 대상이 없어도 신고 기록과 스냅샷은 반환한다.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminReportDetailResponse(
        @Schema(
                description = "신고 ID. 신고 한 건당 한 항목",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64"
        )
        Long reportId,
        @Schema(
                description = "신고자 회원 ID. 콘텐츠 작성자가 아님",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64"
        )
        Long reporterMemberId,
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
                description = "신고 상태. 처리 API는 현재 제공하지 않음",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        ContentReportStatus status,
        @Schema(
                description = "신고 접수 사유 유형",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        ContentReportReasonType reasonType,
        @Schema(
                description = "접수 시 정규화한 상세 사유. 없으면 명시적 null",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"}
        )
        @Nullable String reasonDetail,
        @Schema(
                description = "신고 당시 게시글 제목. 댓글은 명시적 null",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"}
        )
        @Nullable String titleSnapshot,
        @Schema(
                description = "신고 당시 전체 본문",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String contentSnapshot,
        @Schema(
                description = "신고 접수 시각. UTC ISO 8601",
                requiredMode = Schema.RequiredMode.REQUIRED,
                format = "date-time"
        )
        Instant createdAt,
        @Schema(
                description = "조회 시점의 현재 콘텐츠. 대상 조회 불가 시 명시적 null",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @Nullable AdminReportCurrentTargetResponse currentTarget
) {

    public static AdminReportDetailResponse from(
            AdminReportDetailResult result
    ) {
        return new AdminReportDetailResponse(
                result.reportId(),
                result.reporterMemberId(),
                result.targetType(),
                result.targetId(),
                result.status(),
                result.reasonType(),
                result.reasonDetail(),
                result.titleSnapshot(),
                result.contentSnapshot(),
                result.createdAt(),
                result.currentTarget() == null
                        ? null
                        : AdminReportCurrentTargetResponse.from(
                        result.currentTarget()
                )
        );
    }
}
