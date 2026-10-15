package io.github.takgeun.iyum.community.post.application;

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
public class PostUpdateService {

    private final MemberRepository memberRepository;
    private final PostRepository postRepository;

    @Transactional
    public void update(
            Long memberId,
            Long postId,
            PostUpdateCommand command
    ) {
        if(memberId == null || memberId <= 0) {
            throw new PostUpdateException(
                    PostUpdateException.Code.MEMBER_NOT_FOUND
            );
        }

        // 1. 로그인 회원의 현재 정보를 DB에서 조회한다.
        // 기존 작성 서비스와 동일하게 회원 행을 먼저 잠근다.
        Member member = memberRepository
                .findByIdForUpdate(memberId)
                .orElseThrow(() ->
                        new PostUpdateException(
                                PostUpdateException.Code.MEMBER_NOT_FOUND
                        )
                );

        // 2. 현재 상태가 ACTIVE인지 확인한다.
        if(member.getStatus() != MemberStatus.ACTIVE) {
            throw new PostUpdateException(
                    PostUpdateException.Code.MEMBER_NOT_ACTIVE
            );
        }

        if(postId == null || postId <= 0) {
            throw new PostUpdateException(
                    PostUpdateException.Code.POST_NOT_FOUND
            );
        }

        // 3. 삭제되지 않은 게시글을 조회하고 잠근다.
        Post post = postRepository
                .findVisiblePostByIdForUpdate(postId)
                .orElseThrow(() ->
                        new PostUpdateException(
                                PostUpdateException.Code.POST_NOT_FOUND
                        )
                );

        // 4. 로그인 회원과 실제 게시글 작성자가 같은지 확인한다.
        if(!post.getAuthor().getId().equals(member.getId())) {
            throw new PostUpdateException(
                    PostUpdateException.Code.NOT_POST_AUTHOR
            );
        }

        // 5. 권한 확인을 통과한 경우에만 내용을 변경한다.
        post.update(
                command.type(),
                command.topic(),
                command.title(),
                command.content()
        );

        // postRepository.save(post) 는 불필요
        // 트랜잭션 안에서 조회한 Post를 수정 -> JPA가 관리하는 엔티티이므로
        // 변경 감지에 의해 변경 내용이 DB에 반영된다.
    }
}
