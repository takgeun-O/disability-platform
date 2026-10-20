package io.github.takgeun.iyum.member.application;

import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.comment.infrastructure.PostCommentCount;
import io.github.takgeun.iyum.community.post.application.PostCursor;
import io.github.takgeun.iyum.community.post.application.PostFeedItemResult;
import io.github.takgeun.iyum.community.post.application.PostFeedResult;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MemberPostReadService {

    private static final int MAX_PAGE_SIZE = 50;

    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    @Transactional(readOnly = true)
    public PostFeedResult getPosts(
            @Nullable Long memberId,
            @Nullable Instant afterCreatedAt,
            @Nullable Long afterId,
            int size
    ) {
        /**
         * 첫 요청: 커서 없이 2개 요청한다고 가정
         */
        validateMember(memberId);
        validateSize(size);
        validateCursor(afterCreatedAt, afterId);

        /**
         * 요청한 개수보다 1개 더 조회
         * PageRequest.of(0, 2 + 1);
         * 조회 조건을 만족하는 결과의 처음부터 최대 3개 가져와라
         */
        // 추가 1개는 다음 목록이 있는지 확인하는 용도임
        PageRequest limit = PageRequest.of(0, size + 1);

        List<Post> fetched;

        if (afterCreatedAt == null && afterId == null) {
            /**
             * 커서가 없으므로 첫 조회 메서드 호출
             * fetched = [105, 104, 103]; -> 이 때 103번은 다음 목록이 있는지 확인하기 위한 추가 1개
             */
            fetched = postRepository.findFirstVisiblePostsByAuthorId(
                    memberId,
                    limit
            );
        } else {
            /**
             * 여기는 프론트쪽에서 "더보기" 버튼 클릭 시부터
             * 커서가 존재하게 되므로 이 루트를 타게 된다.
             */
            fetched = postRepository.findVisiblePostsByAuthorIdAfter(
                    memberId,
                    afterCreatedAt,
                    afterId,
                    limit
            );
        }

        /**
         * 다음 목록 여부 판단하고, 실제 반환할 2개만 선택
         * hasNext = 3 > 2; -> true
         * returnedSize = Math.min(3, 2) -> 2
         */
        boolean hasNext = fetched.size() > size;
        int returnedSize = Math.min(fetched.size(), size);

        /**
         * subList(0, 2) -> 인덱스 0, 1의 항목 선택 (끝 인덱스 2는 포함하지 않음)
         * returnedPosts = [105, 104];
         */
        List<Post> returnedPosts =
                fetched.subList(0, returnedSize);

        /**
         * 조회 결과가 아예 없으면 여기서 빈 목록을 반환하고 종료
         */
        if (returnedPosts.isEmpty()) {
            return new PostFeedResult(
                    List.of(),
                    false,
                    null
            );
        }

        /**
         * 반환할 게시글의 댓글 수를 한번에 조회할 것.
         * [105, 104]
         */
        List<Long> postIds = returnedPosts.stream()
                .map(Post::getId)
                .toList();

        /**
         * [105, 104]
         * 위 postId 목록으로 댓글 수 집계
         */
        List<PostCommentCount> counts =
                commentRepository.countVisibleCommentsByPostIds(
                        postIds
                );

        /**
         * 위 결과를 찾기 편하도록 Map에 넣기
         */
        Map<Long, Long> commentCounts = new HashMap<>();

        for (PostCommentCount count : counts) {
            commentCounts.put(
                    count.getPostId(),
                    count.getCommentCount()
            );
        }

        /**
         * 엔티티를 결과 객체로 변환
         * 트랜잭션 안에서 필요한 엔티티 값을 읽고 복사하므로
         * 이후 컨트롤러는 결과 객체의 값을 사용하면 된다.
         */
        List<PostFeedItemResult> items = returnedPosts.stream()
                .map(post -> PostFeedItemResult.from(
                        post,
                        commentCounts.getOrDefault(post.getId(), 0L)
                ))
                .toList();

        PostCursor nextCursor = null;

        if (hasNext) {
            /**
             * 실제 반환한 마지막 게시글로 다음 커서 생성
             * 반환한 목록이 [105, 104]이므로 마지막은 104번
             * nextCursor = new PostCursor(last.createdAt(), 104);
             * createdAt 시각에 생성된 104번 게시글까지 전달했으니
             * 그 다음에는 그 뒤부터 조회해라. (추가 확인용인 103번으로 커서를 만들면 안됨.)
             */
            PostFeedItemResult last = items.getLast();

            nextCursor = new PostCursor(
                    last.createdAt(),
                    last.postId()
            );
        }

        return new PostFeedResult(
                items,
                hasNext,
                nextCursor
        );

        /**
         * 이후에는 프론트에서 "더보기"를 누르면 커서를 다시 전달할 것.
         * getPosts(
         *  7L,
         *  이전응답.nextCursor().createdAt(),
         *  이전응답.nextCursor().postId(),
         *  2
         * );
         */
    }

    private void validateMember(@Nullable Long memberId) {
        if (memberId == null) {
            throw new MemberPostReadException(
                    MemberPostReadException.Code.MEMBER_NOT_FOUND
            );
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberPostReadException(
                        MemberPostReadException.Code.MEMBER_NOT_FOUND
                ));

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new MemberPostReadException(
                    MemberPostReadException.Code.MEMBER_NOT_ACTIVE
            );
        }
    }

    private void validateSize(int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new MemberPostReadException(
                    MemberPostReadException.Code.INVALID_PAGE_REQUEST
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

        // 후속 조회에는 생성 시각과 양수 게시글 ID가 모두 필요
        if (afterCreatedAt == null
                || afterId == null
                || afterId <= 0) {
            throw new MemberPostReadException(
                    MemberPostReadException.Code.INVALID_CURSOR
            );
        }
    }
}
