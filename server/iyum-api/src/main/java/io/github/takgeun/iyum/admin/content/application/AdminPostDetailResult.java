package io.github.takgeun.iyum.admin.content.application;

import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import jakarta.annotation.Nullable;

import java.time.Instant;

// 상세 결과 객체
public record AdminPostDetailResult(
        Long postId,
        PostType type,
        PostTopic topic,
        String title,
        String content,
        AdminPostAuthorResult author,
        AdminPostStatus status,
        Instant createdAt,
        Instant updatedAt,
        @Nullable Instant hiddenAt,
        @Nullable Instant deletedAt
) {

    public static AdminPostDetailResult from(Post post) {
        return new AdminPostDetailResult(
                post.getId(),
                post.getType(),
                post.getTopic(),
                post.getTitle(),
                post.getContent(),
                AdminPostAuthorResult.from(post.getAuthor()),
                AdminPostStatus.from(post),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.getHiddenAt(),
                post.getDeletedAt()
        );
    }
}