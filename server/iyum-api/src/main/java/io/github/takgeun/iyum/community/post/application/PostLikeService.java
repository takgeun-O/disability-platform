package io.github.takgeun.iyum.community.post.application;

import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostLike;
import io.github.takgeun.iyum.community.post.infrastructure.PostLikeRepository;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PostLikeService {

    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;

    @Transactional
    public PostLikeResult like(Long memberId, Long postId) {
        return changeLikeState(memberId, postId, true);
    }

    @Transactional
    public PostLikeResult unlike(Long memberId, Long postId) {
        return changeLikeState(memberId, postId, false);
    }

    // 내부 like, unlike 트랜잭션에 참여
    private PostLikeResult changeLikeState(
            Long memberId,
            Long postId,
            boolean liked
    ) {
        /**
         * 같은 회원의 등록 요청이 두 번 들어오는 경우
         *  1. 첫 번째 요청이 회원과 게시글을 잠근다.
         *  2. 좋아요가 없으면 생성하고 커밋
         *  3. 기다리면 두 번째 요청이 잠금을 획득한다.
         *  4. 이미 생성된 좋아요를 확인하고 그대로 반환
         *  DB 유니크제약은 다른 경로에서 중복 저장을 시도해도 막는 마지막 방어선
         *
         * 게시글 삭제와 좋아요 등록이 겹치는 경우
         *  - 좋아요가 먼저 게시글 잠금을 획득하면 -> 좋아요 처리가 끝난 뒤 게시글 삭제 진행
         *  - 게시글 삭제가 먼저 완료되면 -> 좋아요 요청은 공개 게시글을 찾지 못하므로 POST_NOT_FOUND 발생
         *  게시글 잠금을 사용하므로 서로 다른 회원이 같은 게시글에 누르는 좋아요도 순차 처리
         *  향후 실제 부하가 커지면 잠금 범위 재검토 예정
         */
        if(memberId == null || memberId <= 0) {
            throw new PostLikeException(
                    PostLikeException.Code.MEMBER_NOT_FOUND
            );
        }

        // 기존 게시글·댓글 서비스와 동일하게
        // 회원 -> 게시글 순서로 잠금 획득
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() ->
                        new PostLikeException(
                                PostLikeException.Code.MEMBER_NOT_FOUND
                        )
                );

        // 세션의 정보만 믿지 않고 DB의 현재 회원 상태를 확인한다.
        if(member.getStatus() != MemberStatus.ACTIVE) {
            throw new PostLikeException(
                    PostLikeException.Code.MEMBER_NOT_ACTIVE
            );
        }

        if(postId == null || postId <= 0) {
            throw new PostLikeException(
                    PostLikeException.Code.POST_NOT_FOUND
            );
        }

        // 삭제되지 않은 게시글만 조회
        // 좋아요 처리 중 게시글 삭제가 겹치는 상황 조정
        Post post = postRepository.findVisiblePostByIdForUpdate(postId)
                .orElseThrow(() ->
                        new PostLikeException(
                                PostLikeException.Code.POST_NOT_FOUND
                        )
                );

        Optional<PostLike> existingLike =
                postLikeRepository.findByPost_IdAndMember_Id(
                        post.getId(),
                        member.getId()
                );

        if(liked) {
            // 좋아요 등록 요청 + 기존 좋아요 비어있을 때 -> 등록 처리
            // 좋아요 등록 요청 + 기존에도 좋아요 등록되어 있을 때 -> 그대로 유지
            if(existingLike.isEmpty()) {
                postLikeRepository.save(
                        PostLike.create(post, member)
                );

                // 이번 변경을 DB에 반영한 뒤 개수 조회 순서를 명확히 하기 위함
                // 다만 flush()가 커밋은 아니므로 실패 시 트랜잭션과 함꼐 롤백
                postLikeRepository.flush();
            }
        } else {
            // 좋아요 취소 요청 + 기존 좋아요 박혀있을 떄 -> 좋아요 취소 처리
            // 좋아요 취소 요청 + 기존 좋아요 없는 경우 -> 그대로 냅두기
            if(existingLike.isPresent()) {
                postLikeRepository.delete(existingLike.get());

                postLikeRepository.flush();
            }
        }

        // 이번 등록·취소가 DB에 반영된 다음 개수를 조회한다.
        long likeCount =
                postLikeRepository.countByPost_Id(post.getId());

        return new PostLikeResult(
                postId,
                liked,
                likeCount
        );
    }
}
