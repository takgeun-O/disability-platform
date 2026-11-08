package io.github.takgeun.iyum.admin.content.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.takgeun.iyum.admin.content.application.AdminCommentDetailResult;
import io.github.takgeun.iyum.admin.content.application.AdminCommentStatus;
import io.github.takgeun.iyum.admin.content.application.AdminPostStatus;
import jakarta.annotation.Nullable;

import java.time.Instant;

/**
 * 관리자 댓글 단건 조회 응답
 *
 * 현재 저장된 댓글 정보를 반환한다.
 * 운영 조치 당시의 본문을 보존한 응답은 아니다.
 *
 * 값이 없는 시각 필드도 생략하지 않고 명시적 null로 반환한다.
 */
// @JsonInclude(ALWAYS): contentUpdatedAt, hiddenAt, deletedAt이 없을 때도
// 필드를 생략하지 않고 null로 반환하기 위한 설정
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminCommentDetailResponse(
        Long commentId,
        Long postId,
        AdminPostStatus postStatus,
        String content,
        AdminCommentAuthorResponse author,
        AdminCommentStatus status,
        Instant createdAt,
        Instant updatedAt,
        @Nullable Instant contentUpdatedAt,
        @Nullable Instant hiddenAt,
        @Nullable Instant deletedAt
) {

    public static AdminCommentDetailResponse from(
            AdminCommentDetailResult result
    ) {
        return new AdminCommentDetailResponse(
                result.commentId(),
                result.postId(),
                result.postStatus(),
                result.content(),
                AdminCommentAuthorResponse.from(result.author()),
                result.status(),
                result.createdAt(),
                result.updatedAt(),
                result.contentUpdatedAt(),
                result.hiddenAt(),
                result.deletedAt()
        );
    }
}
