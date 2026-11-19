package io.github.takgeun.iyum.community.post.api.dto;

import io.github.takgeun.iyum.community.post.application.PostFeedItemResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "주간 베스트 게시글 조회 응답")
public record PostWeeklyBestResponse(
        List<PostFeedItemResponse> items
) {

    public static PostWeeklyBestResponse from(
            List<PostFeedItemResult> results
    ) {
        // 서비스에서 넘어온 results를 받아서 stream 하면
        // items[0]이 1위임.
        // 대상 글이 없으면 같은 형식으로 {"items":[]} 반환
        List<PostFeedItemResponse> items = results.stream()
                .map(PostFeedItemResponse::from)
                .toList();

        return new PostWeeklyBestResponse(items);
    }
}
