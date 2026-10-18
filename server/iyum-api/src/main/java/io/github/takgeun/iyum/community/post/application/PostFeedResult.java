package io.github.takgeun.iyum.community.post.application;

import org.jspecify.annotations.Nullable;

import java.util.List;

// 피드 전체 결과 객체
public record PostFeedResult(
        List<PostFeedItemResult> items,
        boolean hasNext,
        @Nullable PostCursor nextCursor
) {

    public PostFeedResult {
        // 서비스가 결과를 반환한 뒤 다른 코드에서 목록 항목을 추가하거나 삭제하지 못하도록 방지
        items = List.copyOf(items);
    }
}
