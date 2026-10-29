package io.github.takgeun.iyum.member.application;

import io.github.takgeun.iyum.community.post.application.PostCursor;
import jakarta.annotation.Nullable;

import java.util.List;

public record MemberPostListResult(
        List<MemberPostItemResult> items,
        boolean hasNext,
        @Nullable PostCursor nextCursor
) {

    public MemberPostListResult {
        items = List.copyOf(items);
    }
}
