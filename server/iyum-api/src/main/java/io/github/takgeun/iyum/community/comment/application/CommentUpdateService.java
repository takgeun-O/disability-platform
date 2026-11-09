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

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class CommentUpdateService {

    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final Clock clock;

    @Transactional
    public void update(
            Long memberId,
            Long postId,
            Long commentId,
            CommentUpdateCommand command
    ) {
        if (memberId == null || memberId <= 0) {
            throw new CommentUpdateException(
                    CommentUpdateException.Code.MEMBER_NOT_FOUND
            );
        }

        // 로그인 세션 대신 DB의 현재 회원 상태를 확인한다.
        // 잠금은 회원 -> 게시글 -> 댓글 순서로 획득
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() ->
                        new CommentUpdateException(
                                CommentUpdateException.Code.MEMBER_NOT_FOUND
                        )
                );

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new CommentUpdateException(
                    CommentUpdateException.Code.MEMBER_NOT_ACTIVE
            );
        }

        if (postId == null || postId <= 0) {
            throw new CommentUpdateException(
                    CommentUpdateException.Code.POST_NOT_FOUND
            );
        }

        // 게시글 삭제와 댓글 수정이 겹칠 때 처리 순서를 조정한다.
        postRepository.findVisiblePostByIdForUpdate(postId)
                .orElseThrow(() ->
                        new CommentUpdateException(
                                CommentUpdateException.Code.POST_NOT_FOUND
                        )
                );

        if(commentId == null || commentId <= 0) {
            throw new CommentUpdateException(
                    CommentUpdateException.Code.COMMENT_NOT_FOUND
            );
        }

        // 해당 게시글에 속한 삭제되지 않은 댓글만 조회한다.
        Comment comment = commentRepository
                .findVisibleCommentByIdForUpdate(postId, commentId)
                .orElseThrow(() ->
                        new CommentUpdateException(
                                CommentUpdateException.Code.COMMENT_NOT_FOUND
                        )
                );

        // 댓글 작성자 권한 체크
        if(!comment.getAuthor().getId().equals(member.getId())) {
            throw new CommentUpdateException(
                    CommentUpdateException.Code.NOT_COMMENT_AUTHOR
            );
        }

        // 회원·게시글·댓글 잠금과 작성자 확인을 마친 뒤 시각을 읽는다.
        // 실제 본문 변경 여부와 contentUpdatedAt 갱신은 도메인에서 결정
        comment.update(
                command.content(),
                clock.instant()
        );

        // 기존 댓글의 ID, 작성자, 생성 시각은 유지
        // 댓글 본문이 실제로 변경되면 updatedAt은 기존 Auditing 설정에 따라 수정 시각 갱신
        // 즉, 같은 본문을 다시 보내는 경우에는 UPDATE가 발생하지 않을 수 있음.
    }
}
