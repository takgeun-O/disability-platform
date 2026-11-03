package io.github.takgeun.iyum.admin.content.api.dto;

import io.github.takgeun.iyum.admin.content.application.AdminCommentItemResult;
import io.github.takgeun.iyum.admin.content.application.AdminCommentListResult;
import io.github.takgeun.iyum.admin.content.application.AdminPostStatus;
import org.springframework.data.domain.Page;

import java.util.List;

public record AdminCommentListResponse(
        Long postId,
        AdminPostStatus postStatus, // 댓글이 속한 게시글의 상태
        List<AdminCommentItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {

    public static AdminCommentListResponse from(
            AdminCommentListResult result
    ) {
        Page<AdminCommentItemResult> comments = result.comments();

        List<AdminCommentItemResponse> items = comments.getContent()
                .stream()
                .map(AdminCommentItemResponse::from)
                .toList();

        return new AdminCommentListResponse(
                result.postId(),
                result.postStatus(),
                items,
                comments.getNumber(),
                comments.getSize(),
                comments.getTotalElements(),
                comments.getTotalPages(),
                comments.hasNext(),
                comments.hasPrevious()
        );
    }
}
