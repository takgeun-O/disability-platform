package io.github.takgeun.iyum.community.comment.application;

import io.github.takgeun.iyum.community.comment.infrastructure.CommentLikeRepository;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentLikeSummary;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentLikeReadService {

    private final CommentLikeRepository commentLikeRepository;

    public Map<Long, CommentLikeState> getStates(
            List<Long> commentIds,
            @Nullable Long viewerId
    ) {
        if(commentIds.isEmpty()) {
            return Map.of();
        }

        List<CommentLikeSummary> summaries = commentLikeRepository.findSummariesByCommentIds(
                commentIds,
                viewerId
        );

        Map<Long, CommentLikeState> states = new HashMap<>();

        for (CommentLikeSummary summary : summaries) {
            states.put(
                    summary.getCommentId(),
                    new CommentLikeState(
                            summary.getLikeCount(),
                            summary.getViewerLikeCount() > 0
                    )
            );
        }

        return states;
    }
}
