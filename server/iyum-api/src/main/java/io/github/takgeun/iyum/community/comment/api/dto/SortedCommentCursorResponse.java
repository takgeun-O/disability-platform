package io.github.takgeun.iyum.community.comment.api.dto;

import io.github.takgeun.iyum.community.comment.application.CommentCursor;
import io.github.takgeun.iyum.community.comment.application.CommentSort;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

import java.time.Instant;

@Schema(description = "게시글별 댓글 목록의 다음 조회용 정렬 커서")
public record SortedCommentCursorResponse(

        @Schema(description = "마지막 반환 댓글의 작성 시각")
        Instant createdAt,

        @Schema(description = "마지막 반환 댓글의 ID")
        Long commentId,

        @Schema(description = "이 커서를 생성한 정렬 종류")
        CommentSort sort,

        @Schema(description = "인기순 조회에 사용한 좋아요 수. 등록순·최신순은 null")
        @Nullable Long likeCount
) {

    public static SortedCommentCursorResponse from(
            CommentCursor cursor
    ) {
        return new SortedCommentCursorResponse(
                cursor.createdAt(),
                cursor.commentId(),
                cursor.sort(),
                cursor.likeCount()
        );
    }
}
