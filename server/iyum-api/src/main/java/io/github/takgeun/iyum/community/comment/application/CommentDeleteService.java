package io.github.takgeun.iyum.community.comment.application;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CommentDeleteService {

    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    @Transactional
    public void delete(
            Long memberId,
            Long postId,
            Long commentId
    ) {
        if(memberId == null || memberId <= 0) {
            throw new CommentDeleteException(
                    CommentDeleteException.Code.MEMBER_NOT_FOUND
            );
        }

        // 수정 서비스와 동일하게 회원 -> 게시글 -> 댓글 순서로 잠근다.
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() ->
                        new CommentDeleteException(
                                CommentDeleteException.Code.MEMBER_NOT_FOUND
                        )
                );

        if(member.getStatus() != MemberStatus.ACTIVE) {
            throw new CommentDeleteException(
                    CommentDeleteException.Code.MEMBER_NOT_ACTIVE
            );
        }

        if(postId == null || postId <= 0) {
            throw new CommentDeleteException(
                    CommentDeleteException.Code.POST_NOT_FOUND
            );
        }

        // 게시글 삭제와 댓글 삭제가 겹칠 때 처리 순서 조정
        postRepository.findNotDeletedPostByIdForUpdate(postId)
                .orElseThrow(() ->
                        new CommentDeleteException(
                                CommentDeleteException.Code.POST_NOT_FOUND
                        )
                );

        if(commentId == null || commentId <= 0) {
            throw new CommentDeleteException(
                    CommentDeleteException.Code.COMMENT_NOT_FOUND
            );
        }

        // 해당 게시글에 속하고 삭제되지 않은 댓글만 조회한다.
        Comment comment = commentRepository
                .findNotDeletedCommentByIdForUpdate(postId, commentId)
                .orElseThrow(() ->
                        new CommentDeleteException(
                                CommentDeleteException.Code.COMMENT_NOT_FOUND
                        )
                );

        // 댓글 작성자 검증은 반드시 유지. (댓글 삭제는 반드시 댓글 작성자 본인이어야 함)
        if(!comment.getAuthor().getId().equals(member.getId())) {
            throw new CommentDeleteException(
                    CommentDeleteException.Code.NOT_COMMENT_AUTHOR
            );
        }

        // 행을 제거하지 않고 삭제 시각을 기록한다.
        comment.delete(Instant.now());

        // 트랜잭션 안에서 조회한 엔티티를 변경하므로 삭제 시각도 JPA 변경 감지로 저장된다.
    }
}
