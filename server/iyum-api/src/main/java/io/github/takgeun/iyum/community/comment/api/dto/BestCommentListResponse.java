package io.github.takgeun.iyum.community.comment.api.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.github.takgeun.iyum.community.comment.application.BestCommentResult;
import io.github.takgeun.iyum.community.comment.application.CommentResult;
import jakarta.annotation.Nullable;

import java.util.List;

public record BestCommentListResponse(
        List<Item> items
) {

    public record Item(
            // @JsonUnwrapped: root의 필드를 현재 JSON 항목에 펼쳐준다.
            // 그래서 기존 화면은 계속 item[0].commentId와
            // item[0].content를 읽고,
            // 새 화면은 items[0].bestReply도 읽을 수 있음.
            @JsonUnwrapped CommentResponse root,
            @Nullable CommentResponse bestReply
    ) {
    }

    public static BestCommentListResponse from(
            List<BestCommentResult> results
    ) {
        List<Item> items = results.stream()
                .map(result -> new Item(
                        CommentResponse.from(result.root()),
                        result.bestReply() == null
                                ? null
                                : CommentResponse.from(result.bestReply())
                ))
                .toList();

        return new BestCommentListResponse(items);
    }
}
