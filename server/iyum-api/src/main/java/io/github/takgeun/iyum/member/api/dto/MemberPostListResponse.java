package io.github.takgeun.iyum.member.api.dto;

import io.github.takgeun.iyum.community.post.api.dto.PostCursorResponse;
import io.github.takgeun.iyum.member.application.MemberPostListResult;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

import java.util.List;

@Schema(description = "내 게시글 커서 조회 응답")
public record MemberPostListResponse(
        List<MemberPostItemResponse> items,
        boolean hasNext,
        @Nullable PostCursorResponse nextCursor
) {

    public MemberPostListResponse {
        items = List.copyOf(items);
    }

    public static MemberPostListResponse from(
            MemberPostListResult result
    ) {
        return new MemberPostListResponse(
                result.items().stream()
                        .map(MemberPostItemResponse::from)
                        .toList(),
                result.hasNext(),
                result.nextCursor() == null
                        ? null
                        : PostCursorResponse.from(result.nextCursor())
        );
    }
}
