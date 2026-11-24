package io.github.takgeun.iyum.community.comment.api.dto;

import io.github.takgeun.iyum.community.comment.application.CommentLikeState;
import io.github.takgeun.iyum.community.comment.application.CommentResult;
import io.github.takgeun.iyum.community.comment.domain.Comment;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentResponseTest {

    @Test
    void 삭제된_원댓글은_ID를_유지하고_원문과_작성자와_반응을_가린다() {
        Instant createdAt = Instant.parse("2026-11-23T00:00:00Z");
        Instant deletedAt = createdAt.plusSeconds(60);

        Comment comment = mock(Comment.class);
        when(comment.getId()).thenReturn(15L);
        when(comment.getCreatedAt()).thenReturn(createdAt);
        when(comment.getUpdatedAt()).thenReturn(deletedAt);
        when(comment.getDeletedAt()).thenReturn(deletedAt);

        CommentResult result = CommentResult.from(
                comment,
                new CommentLikeState(12L, true),
                2L
        );

        CommentResponse response = CommentResponse.from(result);

        assertThat(response.commentId()).isEqualTo(15L);
        assertThat(response.deleted()).isTrue();
        assertThat(response.content()).isEqualTo("삭제된 댓글입니다.");
        assertThat(response.author()).isNull();
        assertThat(response.contentUpdatedAt()).isNull();
        assertThat(response.likeCount()).isZero();
        assertThat(response.liked()).isFalse();
        assertThat(response.replyCount()).isEqualTo(2L);

        verify(comment, never()).getContent();
        verify(comment, never()).getAuthor();
    }
}