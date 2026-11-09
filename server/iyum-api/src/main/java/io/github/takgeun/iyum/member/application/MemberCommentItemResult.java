package io.github.takgeun.iyum.member.application;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.post.domain.Post;
import jakarta.annotation.Nullable;

import java.time.Instant;

// 내 댓글 한 개를 담을 결과 객체
public record MemberCommentItemResult(
        Long commentId,
        @Nullable String content,
        Instant createdAt,
        @Nullable Instant updatedAt,
        @Nullable Instant contentUpdatedAt,
        MemberCommentPostResult post,
        @Nullable Long likeCount,
        @Nullable Boolean liked,
        MemberContentVisibility visibility
) {

    public static MemberCommentItemResult from(
            Comment comment,
            long likeCount,
            boolean liked
    ) {
        Post post = comment.getPost();

        if (comment.getDeletedAt() != null
                || post.getDeletedAt() != null) {
            throw new IllegalArgumentException(
                    "삭제된 댓글 또는 삭제된 게시글의 댓글은 반환할 수 없습니다."
            );
        }

        MemberContentVisibility visibility;

        // 부모 게시글 숨김을 먼저 파악한다.
        if(post.getHiddenAt() != null) {
            visibility = MemberContentVisibility.PARENT_HIDDEN;
        } else if(comment.getHiddenAt() != null) {
            visibility = MemberContentVisibility.HIDDEN;
        } else {
            visibility = MemberContentVisibility.VISIBLE;
        }

        if(visibility != MemberContentVisibility.VISIBLE) {
            return new MemberCommentItemResult(
                    comment.getId(),
                    null,
                    comment.getCreatedAt(),
                    null,
                    null,
                    MemberCommentPostResult.idOnly(post.getId()),
                    null,
                    null,
                    visibility
            );
        }

        // 공개 항목 반환
        return new MemberCommentItemResult(
                comment.getId(),
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                comment.getContentUpdatedAt(),
                MemberCommentPostResult.from(post),
                likeCount,
                liked,
                MemberContentVisibility.VISIBLE
        );
    }
}
