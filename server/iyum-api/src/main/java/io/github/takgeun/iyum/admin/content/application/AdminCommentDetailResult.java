package io.github.takgeun.iyum.admin.content.application;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.post.domain.Post;
import jakarta.annotation.Nullable;

import java.time.Instant;

/**
 * 관리자 댓글 단건 조회 결과
 *
 * 댓글 자체의 상태와 부모 게시글 상태를 구분해서 전달한다.
 * 본문은 현재 저장된 내용이며, 운영 조치 당시의 본문은 아니다.
 */
public record AdminCommentDetailResult(
        Long commentId,
        // postId와 postStatus를 제공해야
        // 프론트에서 "댓글은 정상이나 부모 게시글이 숨겨져 공개되지 않는 상태" 와 같은
        // 안내를 정확하게 제공할 수 있음.
        Long postId,
        AdminPostStatus postStatus,
        String content,
        AdminCommentAuthorResult author,
        AdminCommentStatus status,
        Instant createdAt,
        Instant updatedAt,
        @Nullable Instant contentUpdatedAt,
        @Nullable Instant hiddenAt,
        @Nullable Instant deletedAt
) {

    public static AdminCommentDetailResult from(Comment comment) {
        Post post = comment.getPost();

        return new AdminCommentDetailResult(
                comment.getId(),
                post.getId(),
                AdminPostStatus.from(post),
                comment.getContent(),
                AdminCommentAuthorResult.from(comment.getAuthor()),
                AdminCommentStatus.from(comment),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                comment.getContentUpdatedAt(),
                comment.getHiddenAt(),
                comment.getDeletedAt()
        );
    }
}
