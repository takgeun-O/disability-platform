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

        if (afterCreatedAt == null && afterId == null) {
            fetched = commentRepository
                    .findFirstNotDeletedCommentsByAuthorId(
                            memberId,
                            limit
                    );
        } else {
            fetched = commentRepository
                    .findNotDeletedCommentsByAuthorIdAfter(
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

        if (returnedComments.isEmpty()) {
            return new MemberCommentListResult(
                    List.of(),
                    false,
                    null
            );
        }

        // 댓글 자체와 부모 게시글이 모두 공개인 항목만 집계한다.
        // 부모 게시글은 join fetch로 가져오므로, 부모의 숨김 상태를 확인할 때
        // 댓글마다 별도의 부모 조회가 필요하지 않음.
        List<Long> visibleCommentIds = returnedComments.stream()
                .filter(comment ->
                        comment.getHiddenAt() == null
                                && comment.getPost().getHiddenAt() == null
                )
                .map(Comment::getId)
                .toList();

        // 로그인한 회원을 기준으로 전체 좋아요 개수와 본인의 좋아요 여부를 조회한다.
        Map<Long, CommentLikeState> likeStates =
                visibleCommentIds.isEmpty()
                        ? Map.of()
                        : commentLikeReadService.getStates(
                        visibleCommentIds,
                        memberId
                );

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
                                    likeState.likeCount(),
                                    likeState.liked()
                            );
                        })
                        .toList();

        CommentCursor nextCursor = null;

        if (hasNext) {
            // 숨김 항목도 실제 반환 항목이므로 커서 기준에 포함한다.
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
        if (size < 1 || size > MAX_PAGE_SIZE) {
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
