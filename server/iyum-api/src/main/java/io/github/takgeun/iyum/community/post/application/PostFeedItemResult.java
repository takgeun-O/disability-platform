package io.github.takgeun.iyum.community.post.application;

import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;

import java.time.Instant;

// 기존 PostSummaryResult는 페이지 조회에서 계속 사용
public record PostFeedItemResult(
        Long postId,
        PostType type,
        PostTopic topic,
        String title,
        String contentPreview,
        PostAuthorResult author,
        Instant createdAt,
        long commentCount,
        long likeCount,
        boolean liked
) {

    public static PostFeedItemResult from(
            Post post,
            long commentCount,
            long likeCount,
            boolean liked
    ) {
        return new PostFeedItemResult(
                post.getId(),
                post.getType(),
                post.getTopic(),
                post.getTitle(),
                PostContentPreview.from(post.getContent()),
                PostAuthorResult.from(post.getAuthor()),
                post.getCreatedAt(),
                commentCount,
                likeCount,
                liked
        );
    }
}
