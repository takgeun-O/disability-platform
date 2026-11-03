package io.github.takgeun.iyum.member.application;

import io.github.takgeun.iyum.community.post.application.PostAuthorResult;
import io.github.takgeun.iyum.community.post.application.PostContentPreview;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import jakarta.annotation.Nullable;

import java.time.Instant;

public record MemberPostItemResult(
        Long postId,

        @Nullable PostType type,
        @Nullable PostTopic topic,
        @Nullable String title,
        @Nullable String contentPreview,
        @Nullable PostAuthorResult author,

        Instant createdAt,

        @Nullable Long commentCount,
        @Nullable Long likeCount,
        @Nullable Boolean liked,

        MemberContentVisibility visibility
) {

    public static MemberPostItemResult from(
            Post post,
            long commentCount,
            long likeCount,
            boolean liked
    ) {
        if(post.getDeletedAt() != null) {
            throw new IllegalArgumentException(
                    "삭제된 게시글은 내 게시글 목록에 포함할 수 없습니다."
            );
        }

        // 숨김 상태의 분기를 먼저 처리한다.
        // 실수로 집계값을 전달하더라도 숨김 항목의 응답에는 포함되지 않음.
        if(post.getHiddenAt() != null) {
            return new MemberPostItemResult(
                    post.getId(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    post.getCreatedAt(),
                    null,
                    null,
                    null,
                    MemberContentVisibility.HIDDEN
            );
        }

        return new MemberPostItemResult(
                post.getId(),
                post.getType(),
                post.getTopic(),
                post.getTitle(),
                PostContentPreview.from(post.getContent()),
                PostAuthorResult.from(post.getAuthor()),
                post.getCreatedAt(),
                commentCount,
                likeCount,
                liked,
                MemberContentVisibility.VISIBLE
        );
    }
}
