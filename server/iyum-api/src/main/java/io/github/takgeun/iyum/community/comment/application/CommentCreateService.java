package io.github.takgeun.iyum.community.comment.application;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentCreateService {

    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    @Transactional
    public CommentCreateResult create(
            Long authorId,
            Long postId,
            CommentCreateCommand command
    ) {
        if(authorId == null || authorId <= 0) {
            throw new CommentCreateException(
                    CommentCreateException.Code.AUTHOR_NOT_FOUND
            );
        }

        // 로그인 세션의 정보만 믿지 않고 DB의 현재 회원을 확인한다.
        // 로그인 후 회원 상태가 바뀌었더라도 댓글 작성 시점에 조회한 DB 상태가 ACTIVE여야 함.
        // 기존 서비스와 동일하게 회원 -> 게시글 순서로 잠금을 획득한다.
        Member author = memberRepository.findByIdForUpdate(authorId)
                .orElseThrow(() ->
                        new CommentCreateException(
                                CommentCreateException.Code.AUTHOR_NOT_FOUND
                        )
                );

        if(author.getStatus() != MemberStatus.ACTIVE) {
            throw new CommentCreateException(
                    CommentCreateException.Code.AUTHOR_NOT_ACTIVE
            );
        }

        if (postId == null || postId <= 0) {
            throw new CommentCreateException(
                    CommentCreateException.Code.POST_NOT_FOUND
            );
        }

        // 작성 도중 대상 게시글이 삭제되는 상황을 조정한다.
        // 게시글 조회에도 기존 잠금 메서드를 사용한다.
        // 댓글 작성과 게시글 삭제가 겹칠 때 같은 게시글 행에 대한 작업을 조정하기 위함
        //  - 댓글 작성이 먼저 게시글 잠금을 얻으면, 댓글 작성 트랜잭션이 끝난 뒤 게시글 삭제 진행
        //  - 게시글 삭제가 먼저 완료되면, 댓글 작성에서는 삭제되지 않은 게시글을 찾지 못해 POST_NOT_FOUND 처리
        Post post = postRepository.findVisiblePostByIdForUpdate(postId)
                .orElseThrow(() ->
                        new CommentCreateException(
                                CommentCreateException.Code.POST_NOT_FOUND
                        )
                );

        // 기존 입력 정책으로 본문을 검증한다. (내부에서 CommentInputPolicy.validateContent() 실행)
        // 이 떄 잘못된 본문은 현재 도메인에서 IllegalArgumentException으로 거절
        // 이후 작성 API에서 요청 DTO 검증 추가해서 사용자 입력 오류가 정상적인 400 응답으로 처리되도록 함
        Comment comment = Comment.create(
                post,
                author,
                command.content()
        );

        Comment savedComment = commentRepository.save(comment);

        return new CommentCreateResult(savedComment.getId());
    }
}
