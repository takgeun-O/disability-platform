package io.github.takgeun.iyum.member.application;

import io.github.takgeun.iyum.community.comment.application.CommentCursor;
import jakarta.annotation.Nullable;

import java.util.List;

// 목록 전체를 담을 결과 객체
public record MemberCommentListResult(
        List<MemberCommentItemResult> items,
        boolean hasNext,
        @Nullable CommentCursor nextCursor
) {
    public MemberCommentListResult {
        items = List.copyOf(items);
    }
}
