package io.github.takgeun.iyum.community.comment.application;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.domain.CommentLike;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentLikeRepository;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 1. 등록과 취소는 각각 멱등하게 처리한다.
 * 2. 자기 댓글에도 좋아요를 허용한다.
 *  -> 댓글 수정·삭제 서비스에 있는 작성자 일치 여부를 여기서는 넣지 않음.
 * 3. 잠금 순서를 기존 서비스와 맞춘다.
 *  게시글 삭제나 댓글 삭제와 좋아요 요청이 겹쳤을 때 먼저 잠금을 획득한 작업을 기준으로 순서를 정한다.
 *  현재 방식의 경우 같은 게시글의 서로 다른 댓글에 대한 좋아요도
 *  게시글 잠금 때문에 순차 처리된다. 기존의 수정·삭제 흐름과 일관성을 유지하고자 하는 방식이고
 *  실제 부하가 커지면 향후 잠금 범위를 재검토할 예정
 * 4. flush()는 커밋이 아님을 인지하기. (DB에 반영하기 위한 목적임)
 */
@Service
@RequiredArgsConstructor
public class CommentLikeService {

    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;

    @Transactional
    public CommentLikeResult like(
            Long memberId,
            Long postId,
            Long commentId
    ) {
        return changeLikeState(memberId, postId, commentId, true);
    }

    @Transactional
    public CommentLikeResult unlike(
            Long memberId,
            Long postId,
            Long commentId
    ) {
        return changeLikeState(memberId, postId, commentId, false);
    }

    private CommentLikeResult changeLikeState(
            Long memberId,
            Long postId,
            Long commentId,
            boolean liked
    ) {
        if(memberId == null || memberId <= 0) {
            throw new CommentLikeException(
                    CommentLikeException.Code.MEMBER_NOT_FOUND
            );
        }

        // 1. 회원 잠금: 현재 상태 확인 및 같은 회원의 변경 요청 조정
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() ->
                        new CommentLikeException(
                                CommentLikeException.Code.MEMBER_NOT_FOUND
                        )
                );

        if(member.getStatus() != MemberStatus.ACTIVE) {
            throw new CommentLikeException(
                    CommentLikeException.Code.MEMBER_NOT_ACTIVE
            );
        }

        if(postId == null || postId <= 0) {
            throw new CommentLikeException(
                    CommentLikeException.Code.POST_NOT_FOUND
            );
        }

        // 2. 게시글 잠금: 삭제되지 않은 게시글인지 확인
        postRepository.findVisiblePostByIdForUpdate(postId)
                .orElseThrow(() ->
                        new CommentLikeException(
                                CommentLikeException.Code.POST_NOT_FOUND
                        )
                );

        if(commentId == null || commentId <= 0) {
            throw new CommentLikeException(
                    CommentLikeException.Code.COMMENT_NOT_FOUND
            );
        }

        // 3. 댓글 잠금: 게시글 소속 및 삭제 여부 확인
        Comment comment = commentRepository
                .findVisibleCommentByIdForUpdate(postId, commentId)
                .orElseThrow(() ->
                        new CommentLikeException(
                                CommentLikeException.Code.COMMENT_NOT_FOUND
                        )
                );

        // 현재 좋아요 여부 확인
        Optional<CommentLike> existingLike =
                commentLikeRepository.findByComment_IdAndMember_Id(
                        comment.getId(),
                        member.getId()
                );

        if(liked) {
            // 좋아요를 등록해 달라는 요청이라면
            if(existingLike.isEmpty()) {
                // 아직 등록하지 않았을 때만 생성하기
                commentLikeRepository.save(
                        CommentLike.create(comment, member)
                );

                commentLikeRepository.flush();
            }
        } else {
            // 좋아요를 취소해 달라는 요청이라면
            if(existingLike.isPresent()) {
                // 등록된 기록이 있을 떄만 삭제
                commentLikeRepository.delete(existingLike.get());

                commentRepository.flush();
            }
        }

        long likeCount =
                commentLikeRepository.countByComment_Id(comment.getId());

        return new CommentLikeResult(
                postId,
                comment.getId(),
                liked,
                likeCount
        );
    }
}
