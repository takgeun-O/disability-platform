package io.github.takgeun.iyum.community.post.application;

import io.github.takgeun.iyum.community.post.infrastructure.PostLikeRepository;
import io.github.takgeun.iyum.community.post.infrastructure.PostLikeSummary;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 목록·상세·마이페이지에서 공통으로 사용할 조회 서비스
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostLikeReadService {

    private final PostLikeRepository postLikeRepository;

    // 게시글 ID 목록을 받아 집계 쿼리를 한번 실행하고,
    // 게시글 ID로 찾을 수 있는 Map 반환
    public Map<Long, PostLikeState> getStates(
            List<Long> postIds,
            @Nullable Long viewerId
    ) {
        if (postIds.isEmpty()) {
            return Map.of();
        }

        List<PostLikeSummary> summaries = postLikeRepository.findSummariesByPostIds(
                postIds,
                viewerId
        );

        Map<Long, PostLikeState> states = new HashMap<>();

        for (PostLikeSummary summary : summaries) {
            states.put(
                    summary.getPostId(),
                    new PostLikeState(
                            summary.getLikeCount(),
                            summary.getViewerLikeCount() > 0
                    )
            );
        }

        return states;
    }
}
