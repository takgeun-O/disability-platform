package io.github.takgeun.iyum.community.comment.infrastructure;

import java.time.Instant;

/**
 * 인기순으로 조회한 댓글의 식별 정보와 정렬 기준값.
 * 쿼리 결과에서 필요한 값만 받는 인터페이스
 */
public interface CommentPopularityRow {

    Long getCommentId();

    Instant getCreatedAt();

    Long getLikeCount();
}
