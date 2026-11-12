package io.github.takgeun.iyum.admin.report.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.takgeun.iyum.admin.report.application.AdminReportDetailResult;
import io.github.takgeun.iyum.admin.report.application.AdminReportTargetType;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminReportDetailResponse(

        @Schema(
                description = "신고 ID",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64"
        )
        Long reportId,

        @Schema(
                description = "신고자 회원 ID. 콘텐츠 작성자 및 처리 관리자와 구분",
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
                description = "신고 상태",
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {
                        "RECEIVED",
                        "RESOLVED",
                        "DISMISSED"
                }
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
        @Nullable AdminReportCurrentTargetResponse currentTarget,

        @Schema(
                description = "처리 관리자 회원 ID. 접수 상태에서는 명시적 null",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"integer", "null"},
                format = "int64"
        )
        @Nullable Long handledByMemberId,

        @Schema(
                description = "저장된 처리 시각. UTC ISO 8601. 접수 상태에서는 명시적 null",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"},
                format = "date-time"
        )
        @Nullable Instant handledAt,

        @Schema(
                description = "저장된 처리 사유. 접수 상태에서는 명시적 null",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"}
        )
        @Nullable String handlingReason

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
                        ),
                        result.handledByMemberId(),
                        result.handledAt(),
                        result.handlingReason()
                );
        }
}