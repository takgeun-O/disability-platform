package io.github.takgeun.iyum.admin.report.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.takgeun.iyum.admin.report.application.AdminReportCurrentTargetResult;
import jakarta.annotation.Nullable;

import java.time.Instant;

/**
 * 조회 시점의 현재 콘텐츠.
 * 신고 접수 당시 스냅샷과 구분한다.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminReportCurrentTargetResponse(
        @Schema(
                description = "게시글 ID 또는 댓글 ID",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer"},
                format = "int64"
        )
        Long targetId,
        @Schema(
                description = "현재 댓글의 부모 게시글 ID. 게시글은 명시적 null",
                requiredMode = Schema.RequiredMode.REQUIRED,
                type = "integer",
                types = {"integer", "null"},
                format = "int64"
        )
        @Nullable Long parentPostId,
        @Schema(
                description = "현재 대상 작성자. 신고자와 다름",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        AdminReportTargetAuthorResponse author,
        @Schema(
                description = "현재 게시글 제목. 댓글은 명시적 null",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"}
        )
        @Nullable String title,
        @Schema(
                description = "조회 시점의 전체 본문. 신고 당시 스냅샷이 아님",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String content,
        @Schema(
                description = "대상 자체의 숨김 시각. 댓글 부모의 상태가 아님. 없으면 null",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"},
                format = "date-time"
        )
        @Nullable Instant hiddenAt,
        @Schema(
                description = "대상 자체의 소프트 삭제 시각. 없으면 null",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"},
                format = "date-time"
        )
        @Nullable Instant deletedAt
) {

    public static AdminReportCurrentTargetResponse from(
            AdminReportCurrentTargetResult result
    ) {
        return new AdminReportCurrentTargetResponse(
                result.targetId(),
                result.parentPostId(),
                AdminReportTargetAuthorResponse.from(result.author()),
                result.title(),
                result.content(),
                result.hiddenAt(),
                result.deletedAt()
        );
    }
}
