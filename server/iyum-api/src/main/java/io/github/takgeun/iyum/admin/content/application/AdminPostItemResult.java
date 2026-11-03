package io.github.takgeun.iyum.admin.content.application;

import io.github.takgeun.iyum.community.post.application.PostContentPreview;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import jakarta.annotation.Nullable;

import java.time.Instant;

// 목록 항목 결과 객체 생성
public record AdminPostItemResult(
        Long postId,
        PostType type,
        PostTopic topic,
        String title,
        String contentPreview,
        AdminPostAuthorResult author,
        AdminPostStatus status,
        Instant createdAt,
        Instant updatedAt,
        @Nullable Instant hiddenAt,
        @Nullable Instant deletedAt
) {

    public static AdminPostItemResult from(Post post) {
        return new AdminPostItemResult(
                post.getId(),
                post.getType(),
                post.getTopic(),
                post.getTitle(),
                PostContentPreview.from(post.getContent()),
                AdminPostAuthorResult.from(post.getAuthor()),
                AdminPostStatus.from(post),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.getHiddenAt(),
                post.getDeletedAt()
        );
    }
}
