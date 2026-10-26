package io.github.takgeun.iyum.community.comment.application;

import io.github.takgeun.iyum.community.comment.infrastructure.CommentLikeRepository;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentLikeSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentLikeReadServiceTest {

    @Mock
    CommentLikeRepository commentLikeRepository;

    @InjectMocks
    CommentLikeReadService commentLikeReadService;

    @Test
    void 댓글_ID가_비어_있으면_집계_쿼리를_실행하지_않는다() {
        Map<Long, CommentLikeState> result =
                commentLikeReadService.getStates(List.of(), 7L);

        assertThat(result).isEmpty();

        verifyNoInteractions(commentLikeRepository);
    }

    @Test
    void 댓글_ID들을_한번에_조회하고_댓글별_상태로_변환한다() {
        List<Long> commentIds = List.of(100L, 101L, 102L);

        CommentLikeSummary first = summary(100L, 3L, 1L);
        CommentLikeSummary second = summary(101L, 2L, 0L);

        when(commentLikeRepository.findSummariesByCommentIds(
                commentIds, 7L
        )).thenReturn(List.of(first, second));

        Map<Long, CommentLikeState> result =
                commentLikeReadService.getStates(commentIds, 7L);

        assertThat(result).hasSize(2);

        assertThat(result.get(100L))
                .isEqualTo(new CommentLikeState(3L, true));

        assertThat(result.get(101L))
                .isEqualTo(new CommentLikeState(2L, false));

        // 집계 행이 없는 댓글은 호출 측에서 EMPTY로 처리한다.
        assertThat(
                result.getOrDefault(102L, CommentLikeState.EMPTY)
        ).isEqualTo(new CommentLikeState(0L, false));

        verify(commentLikeRepository)
                .findSummariesByCommentIds(commentIds, 7L);

        verifyNoMoreInteractions(commentLikeRepository);
    }

    @Test
    void 비로그인_조회는_회원_ID를_null로_전달한다() {
        List<Long> commentIds = List.of(100L);

        // 내부의 when 설정을 먼저 모두 완료한다.
        CommentLikeSummary summary = summary(100L, 3L, 0L);

        when(commentLikeRepository.findSummariesByCommentIds(
                commentIds, null
        )).thenReturn(List.of(summary));

        Map<Long, CommentLikeState> result =
                commentLikeReadService.getStates(commentIds, null);

        assertThat(result.get(100L))
                .isEqualTo(new CommentLikeState(3L, false));

        verify(commentLikeRepository)
                .findSummariesByCommentIds(commentIds, null);

        verifyNoMoreInteractions(commentLikeRepository);
    }

    private CommentLikeSummary summary(
            Long commentId,
            long likeCount,
            long viewerLikeCount
    ) {
        CommentLikeSummary summary = mock(CommentLikeSummary.class);

        when(summary.getCommentId()).thenReturn(commentId);
        when(summary.getLikeCount()).thenReturn(likeCount);
        when(summary.getViewerLikeCount()).thenReturn(viewerLikeCount);

        return summary;
    }
}