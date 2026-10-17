package io.github.takgeun.iyum.community.comment.application;

import jakarta.annotation.Nullable;

import java.util.List;

public record CommentListResult(
        List<CommentResult> items,  // 이번에 반환할 댓글
        boolean hasNext,    // 다음 댓글이 더 있는지
        @Nullable CommentCursor nextCursor  // 다음 요청에 사용할 커서. 마지막 구간이면 null
) {

    public CommentListResult {
        // List.copyOf(): 전달받은 목록을 변경 불가능한 목록으로 복사
        // 서비스가 반환한 뒤 외부에서 댓글 항목을 추가·삭제하지 못하도록 한 것.
        items = List.copyOf(items);
    }
}
