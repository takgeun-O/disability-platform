package io.github.takgeun.iyum.community.post.application;

import io.github.takgeun.iyum.community.post.domain.Post;
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
public class PostDeleteService {

    private final MemberRepository memberRepository;
    private final PostRepository postRepository;

    @Transactional
    public void delete(Long memberId, Long postId) {
        if(memberId == null || memberId <= 0) {
            throw new PostDeleteException(
                    PostDeleteException.Code.MEMBER_NOT_FOUND
            );
        }

        // 회원 -> 게시글 순서로 잠금을 획득하는 흐름을 수정 서비스와 같게 유지
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() ->
                        new PostDeleteException(
                                PostDeleteException.Code.MEMBER_NOT_FOUND
                        ));

        if(member.getStatus() != MemberStatus.ACTIVE) {
            throw new PostDeleteException(
                    PostDeleteException.Code.MEMBER_NOT_ACTIVE
            );
        }

        if(postId == null || postId <= 0) {
            throw new PostDeleteException(
                    PostDeleteException.Code.POST_NOT_FOUND
            );
        }

        // 회원 -> 게시글 순서로 잠금을 획득하는 흐름을 수정 서비스와 같게 유지
        Post post = postRepository.findVisiblePostByIdForUpdate(postId)
                .orElseThrow(() ->
                        new PostDeleteException(
                                PostDeleteException.Code.POST_NOT_FOUND
                        ));

        if(!post.getAuthor().getId().equals(member.getId())) {
            throw new PostDeleteException(
                    PostDeleteException.Code.NOT_POST_AUTHOR
            );
        }

        // 트랜잭션 안에서 조회한 엔티티의 상태를 변경하므로
        // 별도로 save()를 호출하지 않아도 변경 감지로 변경된다.
        // 참고로 deleteById()는 행 자체를 지우므로 이번 삭제 논리에서는 사용하지 않음.
        post.delete(Instant.now());
    }
}
