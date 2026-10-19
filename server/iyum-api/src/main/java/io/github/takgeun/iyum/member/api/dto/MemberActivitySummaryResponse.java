package io.github.takgeun.iyum.member.api.dto;

import io.github.takgeun.iyum.member.application.MemberActivitySummaryResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "현재 로그인한 회원의 활동 요약")
public record MemberActivitySummaryResponse(

        @Schema(
                description = "삭제되지 않은 내 게시글 수",
                example = "2",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long postCount,

        @Schema(
                description = """
                        내가 작성한 공개 상태의 댓글 수.
                        삭제된 댓글과 삭제된 게시글에 달린 댓글은 제외합니다.
                        """,
                example = "3",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long commentCount
) {
    public static MemberActivitySummaryResponse from(
            MemberActivitySummaryResult result
    ) {
        return new MemberActivitySummaryResponse(
                result.postCount(),
                result.commentCount()
        );
    }
}
