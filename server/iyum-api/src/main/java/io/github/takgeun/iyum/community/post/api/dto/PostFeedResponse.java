package io.github.takgeun.iyum.community.post.api.dto;

import io.github.takgeun.iyum.community.post.application.PostFeedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

import java.util.List;

// Service의 결과 객체를 외부에 공개할 응답 DTO로 변환
@Schema(description = "커서 기반 게시글 피드 응답")
public record PostFeedResponse(
        List<PostFeedItemResponse> items,

        @Schema(description = "다음 게시글이 더 있는지 여부")
        boolean hasNext,

        @Schema(description = "다음 조회용 커서. 마지막 목록이면 null")
        @Nullable PostCursorResponse nextCursor
) {

    public PostFeedResponse {
        items = List.copyOf(items);
    }

    public static PostFeedResponse from(PostFeedResult result) {
        var cursor = result.nextCursor();

        return new PostFeedResponse(
                result.items().stream()
                        .map(PostFeedItemResponse::from)
                        .toList(),
                result.hasNext(),
                cursor == null
                        ? null
                        : PostCursorResponse.from(cursor)
        );
    }
}
