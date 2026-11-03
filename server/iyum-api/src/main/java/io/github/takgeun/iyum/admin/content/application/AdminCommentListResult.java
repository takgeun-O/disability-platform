package io.github.takgeun.iyum.admin.content.application;

import org.springframework.data.domain.Page;

// 부모 상태와 댓글 페이지를 묶는 결과 객체
public record AdminCommentListResult(
        Long postId,
        AdminPostStatus postStatus,
        // Page는 서비스 내부에서 페이지 정보를 전달하는 용도
        Page<AdminCommentItemResult> comments
) {
}
