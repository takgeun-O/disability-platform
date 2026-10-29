package io.github.takgeun.iyum.admin.moderation.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class ContentModerationLogTest {

    private static final Instant NOW =
            Instant.parse("2026-10-29T12:00:00Z");

    @Test
    void 게시글_이력은_게시글_ID만_저장한다() {
        ContentModerationLog log = ContentModerationLog.forPost(
                7L,
                123L,
                ModerationAction.HIDE,
                "  운영 정책 위반  ",
                NOW
        );

        assertThat(log.getActorMemberId()).isEqualTo(7L);
        assertThat(log.getPostId()).isEqualTo(123L);
        assertThat(log.getCommentId()).isNull();
        assertThat(log.getAction()).isEqualTo(ModerationAction.HIDE);
        assertThat(log.getReason()).isEqualTo("운영 정책 위반");
        assertThat(log.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void 댓글_이력은_댓글_ID만_저장한다() {
        ContentModerationLog log = ContentModerationLog.forComment(
                7L,
                456L,
                ModerationAction.RESTORE,
                "검토 결과 복원",
                NOW
        );

        assertThat(log.getActorMemberId()).isEqualTo(7L);
        assertThat(log.getPostId()).isNull();
        assertThat(log.getCommentId()).isEqualTo(456L);
        assertThat(log.getAction()).isEqualTo(ModerationAction.RESTORE);
        assertThat(log.getReason()).isEqualTo("검토 결과 복원");
        assertThat(log.getCreatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void 관리자_ID가_유효하지_않으면_거부한다(Long actorId) {
        assertThatThrownBy(() -> ContentModerationLog.forPost(
                actorId,
                123L,
                ModerationAction.HIDE,
                "처리 사유",
                NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void 게시글_ID가_유효하지_않으면_거부한다(Long postId) {
        assertThatThrownBy(() -> ContentModerationLog.forPost(
                7L,
                postId,
                ModerationAction.HIDE,
                "처리 사유",
                NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void 댓글_ID가_유효하지_않으면_거부한다(Long commentId) {
        assertThatThrownBy(() -> ContentModerationLog.forComment(
                7L,
                commentId,
                ModerationAction.HIDE,
                "처리 사유",
                NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void 사유가_비어_있으면_거부한다(String reason) {
        assertThatThrownBy(() -> ContentModerationLog.forPost(
                7L,
                123L,
                ModerationAction.HIDE,
                reason,
                NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 사유는_앞뒤_공백을_제거한_후_500자까지_허용한다() {
        String reason = "가".repeat(500);

        ContentModerationLog log = ContentModerationLog.forPost(
                7L,
                123L,
                ModerationAction.HIDE,
                "  " + reason + "  ",
                NOW
        );

        assertThat(log.getReason()).isEqualTo(reason);
    }

    @Test
    void 사유가_501자이면_거부한다() {
        assertThatThrownBy(() -> ContentModerationLog.forPost(
                7L,
                123L,
                ModerationAction.HIDE,
                "가".repeat(501),
                NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 이모지도_코드_포인트_기준으로_길이를_검증한다() {
        String emoji = "\uD83D\uDE00";

        ContentModerationLog log = ContentModerationLog.forPost(
                7L,
                123L,
                ModerationAction.HIDE,
                emoji.repeat(500),
                NOW
        );

        assertThat(log.getReason()).isEqualTo(emoji.repeat(500));

        assertThatThrownBy(() -> ContentModerationLog.forPost(
                7L,
                123L,
                ModerationAction.HIDE,
                emoji.repeat(501),
                NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 처리_종류가_없으면_거부한다() {
        assertThatThrownBy(() -> ContentModerationLog.forPost(
                7L,
                123L,
                null,
                "처리 사유",
                NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 처리_시각이_없으면_거부한다() {
        assertThatThrownBy(() -> ContentModerationLog.forPost(
                7L,
                123L,
                ModerationAction.HIDE,
                "처리 사유",
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }
}