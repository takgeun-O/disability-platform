package io.github.takgeun.iyum.admin.moderation.application;

import java.time.Instant;

public record PostModerationResult(
        Long postId,
        boolean hidden,
        Instant hiddenAt,   // 숨김 시각. 공개 상태면 null
        boolean changed     // 이번 요청에서 실제 상태가 변경됐는지 여부
) {
}
