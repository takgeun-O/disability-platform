package io.github.takgeun.iyum.member.application;

import io.github.takgeun.iyum.community.comment.application.CommentCursor;
import io.github.takgeun.iyum.community.comment.application.CommentLikeReadService;
import io.github.takgeun.iyum.community.comment.application.CommentLikeState;
import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MemberCommentReadService {

    private static final int MAX_PAGE_SIZE = 50;

    private final MemberRepository memberRepository;
    private final CommentRepository commentRepository;
    private final CommentLikeReadService commentLikeReadService;

    @Transactional(readOnly = true)
    public MemberCommentListResult getComments(
            @Nullable Long memberId,
            @Nullable Instant afterCreatedAt,
            @Nullable Long afterId,
            int size
    ) {
        validateMember(memberId);
        validateSize(size);
        validateCursor(afterCreatedAt, afterId);

        // 추가 1개는 다음 목록이 있는지 확인하는 용도
        PageRequest limit = PageRequest.of(0, size + 1);

        List<Comment> fetched;

        if(afterCreatedAt == null && afterId == null) {
            fetched =
                    commentRepository.findFirstVisibleCommentsByAuthorId(
                            memberId,
                            limit
                    );
        } else {
            fetched =
                    commentRepository.findVisibleCommentsByAuthorIdAfter(
                            memberId,
                            afterCreatedAt,
                            afterId,
                            limit
                    );
        }

        boolean hasNext = fetched.size() > size;
        int returnedSize = Math.min(fetched.size(), size);

        // 다음 목록 확인용으로 가져온 추가 1개는 반환하지 않음
        List<Comment> returnedComments =
                fetched.subList(0, returnedSize);

        if(returnedComments.isEmpty()) {
            return new MemberCommentListResult(
                    List.of(),
                    false,
                    null
            );
        }

        // 실제 응답에 포함할 댓글만 좋아요 집계 대상으로 사용
        List<Long> commentIds = returnedComments.stream()
                .map(Comment::getId)
                .toList();

        // 댓글 좋아요 개수는 말 그대로 개수만 필요하므로 개인별 선택 상태는 요청하지 않음
        Map<Long, CommentLikeState> likeStates =
                commentLikeReadService.getStates(commentIds, null);

        // 트랜잭션 안에서 댓글, 게시글, 좋아요 개수를 결과 객체로 복사한다.
        List<MemberCommentItemResult> items =
                returnedComments.stream()
                        .map(comment -> {
                            CommentLikeState likeState =
                                    likeStates.getOrDefault(
                                            comment.getId(),
                                            CommentLikeState.EMPTY
                                    );

                            return MemberCommentItemResult.from(
                                    comment,
                                    likeState.likeCount()
                            );
                        })
                        .toList();

        CommentCursor nextCursor = null;

        if(hasNext) {
            // 실제 반환할 마지막 댓글을 기준으로 커서를 만든다.
            MemberCommentItemResult last = items.getLast();

            nextCursor = new CommentCursor(
                    last.createdAt(),
                    last.commentId()
            );
        }

        return new MemberCommentListResult(
                items,
                hasNext,
                nextCursor
        );
    }

    private void validateMember(@Nullable Long memberId) {
        if (memberId == null) {
            throw new MemberCommentReadException(
                    MemberCommentReadException.Code.MEMBER_NOT_FOUND
            );
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberCommentReadException(
                        MemberCommentReadException.Code.MEMBER_NOT_FOUND
                ));

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new MemberCommentReadException(
                    MemberCommentReadException.Code.MEMBER_NOT_ACTIVE
            );
        }
    }

    private void validateSize(int size) {
        if(size < 1 || size > MAX_PAGE_SIZE) {
            throw new MemberCommentReadException(
                    MemberCommentReadException.Code.INVALID_PAGE_REQUEST
            );
        }
    }

    private void validateCursor(
            @Nullable Instant afterCreatedAt,
            @Nullable Long afterId
    ) {
        // 둘 다 없으면 첫 조회
        if (afterCreatedAt == null && afterId == null) {
            return;
        }

        // 후속 조회에는 생성 시각과 양수 댓글 ID가 모두 필요
        if (afterCreatedAt == null
                || afterId == null
                || afterId <= 0) {
            throw new MemberCommentReadException(
                    MemberCommentReadException.Code.INVALID_CURSOR
            );
        }
    }
}
