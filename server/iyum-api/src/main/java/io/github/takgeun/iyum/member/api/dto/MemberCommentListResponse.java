package io.github.takgeun.iyum.member.api.dto;

import io.github.takgeun.iyum.community.comment.api.dto.CommentCursorResponse;
import io.github.takgeun.iyum.member.application.MemberCommentListResult;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

import java.util.List;

@Schema(description = "최신순으로 조회한 내 댓글 커서 목록")
public record MemberCommentListResponse(
        List<MemberCommentItemResponse> items,

        @Schema(description = "다음 댓글이 더 있는지 여부")
        boolean hasNext,

        @Schema(description = "다음 조회용 커서. 마지막 목록이면 null")
        @Nullable CommentCursorResponse nextCursor
) {

    public MemberCommentListResponse {
        items = List.copyOf(items);
    }

    public static MemberCommentListResponse from(
            MemberCommentListResult result
    ) {
        // var : 오른쪽 값을 보고 컴파일러가 변수의 타입을 추론하도록 하는 문법
        var cursor = result.nextCursor();

        return new MemberCommentListResponse(
                result.items().stream()
                        .map(MemberCommentItemResponse::from)
                        .toList(),
                result.hasNext(),
                cursor == null
                        ? null
                        : CommentCursorResponse.from(cursor)
        );
    }
}
