package io.github.takgeun.iyum.community.post.application;

import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;

import java.time.Instant;

public record PostSummaryResult(
        // 목록에서는 본문을 보여주지 않을 것이니 content 제외
        Long postId,
        PostType type,
        PostTopic topic,
        String title,
        PostAuthorResult author,
        Instant createdAt,
        long commentCount,   // 댓글이 없으면 0으로 표현해야 하니 null이 없는 기본형 사용
        long likeCount,
        boolean liked
) {

    public static PostSummaryResult from(
            Post post,
            long commentCount,
            long likeCount,
            boolean liked
    ) {
        return new PostSummaryResult(
                post.getId(),
                post.getType(),
                post.getTopic(),
                post.getTitle(),
                PostAuthorResult.from(post.getAuthor()),
                post.getCreatedAt(),
                commentCount,
                likeCount,
                liked
        );
    }
}
