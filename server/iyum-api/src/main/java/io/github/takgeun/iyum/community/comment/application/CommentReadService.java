package io.github.takgeun.iyum.community.comment.application;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentReadService {

    private static final int MAX_PAGE_SIZE = 50;

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;

    // 첫 조회에서는 아직 마지막 댓글이 없으므로
    // getComments(post, null, null, 3); 전달
    public CommentListResult getComments(
            Long postId,    // 댓글을 조회할 게시글
            @Nullable Instant afterCreatedAt,   // 직전에 받은 마지막 댓글의 생성 시각
            @Nullable Long afterId,     // 직전에 받은 마지막 댓글의 ID
            int size    // 이번에 받을 댓글 개수
    ) {

        // 이 떄 검증 중 예외 발생 시 이후 댓글 조회는 실행하지 않음
        validateSize(size); // 1 ~ MAX_PAGE_SIZE인지 확인
        validateCursor(afterCreatedAt, afterId);    // 둘 다 없거나, 생성 시각과 양수 ID가 모두 있는지 확인
        validatePost(postId);   // ID가 유효하고, 실제로 존재하며 삭제되지 않았는지 확인

        // 다음 댓글의 존재 여부를 판단하기 위해 1개 더 조회한다.
        // 조회 시작 위치는 커서 조건으로 정하고 PageRequest.of(...)은 그 결과의 앞부분만 제한해서 가져오는 용도
        PageRequest limit = PageRequest.of(0, size + 1);

        List<Comment> comments;

        // 첫 조회인지, 후속 조회인지 구분
        if(afterCreatedAt == null && afterId == null) {
            // 첫 조회는 처음부터 가져오고
            comments = commentRepository.findFirstVisibleComments(
                    postId,
                    limit
            );
        } else {
            // 후속 조회는 커서보다 뒤에 있는 댓글 가져오기
            comments = commentRepository.findVisibleCommentsAfter(
                    postId,
                    afterCreatedAt,
                    afterId,
                    limit
            );
        }

        // 다음 댓글 존재 여부를 판단하고 반환할 개수 정하기
        // 3개를 요청했는데 4개를 조회했다면
        // hasNext = true;
        // returnedSize = 3;
        // 3개 요청했는데 2개만 조회했다면
        // hasNext = false;
        // returnedSize = 2;
        boolean hasNext = comments.size() > size;
        int returnedSize = Math.min(comments.size(), size);

        // 확인용 한 개를 제외하고 DTO로 변환한다.
        // 조회 결과: 댓글 A, B, C, D
        // 반환 결과: 댓글 A, B, C (D는 다음 댓글이 존재한다는 사실을 확인하는 용도로만 사용)
        List<CommentResult> items = comments
                .subList(0, returnedSize)
                .stream()
                .map(CommentResult::from)   // 필요한 값을 복사하므로 클라이언트에 전달할 결과에는 엔티티 대신 DTO가 들어감.
                .toList();

        // 실제 반환한 마지막 댓글로 다음 커서를 만든다.
        // 앞의 예에서 C의 생성 시각과 ID가 다음 커서가 된다. (D를 커서로 사용하면 안됨. D가 누락된다.)
        // C를 커서로 사용하면 다음 요청은 D부터 정상적으로 가져온다.
        CommentCursor nextCursor = null;

        if(hasNext) {
            // 추가 확인용 댓글이 아니라 실제 반환한 마지막 댓글 기준
            CommentResult last = items.getLast();

            nextCursor = new CommentCursor(
                    last.createdAt(),
                    last.commentId()
            );
        }

        // 결과를 반환하고 프론트엔드가 다음 조회에 활용
        // 프론트엔드는 스크롤이 아래에 도달하면 nextCursor를 다음 요청에 넣고
        // 받은 댓글을 기존 목록 뒤에 붙인다.
        // hasNext가 false가 되면 추가 조회를 멈춘다.
        return new CommentListResult(
                items,
                hasNext,
                nextCursor
        );
    }

    private void validateSize(int size) {
        if(size < 1 || size > MAX_PAGE_SIZE) {
            throw new CommentReadException(
                    CommentReadException.Code.INVALID_PAGE_REQUEST
            );
        }
    }

    private void validateCursor(
            @Nullable Instant afterCreatedAt,
            @Nullable Long afterId
    ) {
        // 둘 다 없으면 첫 조회이므로 허용
        if(afterCreatedAt == null && afterId == null) {
            return;
        }

        // 후속 조회에서는 시각과 양수 ID가 모두 필요하다.
        if(afterCreatedAt == null || afterId == null || afterId <= 0) {
            throw new CommentReadException(
                    CommentReadException.Code.INVALID_CURSOR
            );
        }
    }

    private void validatePost(Long postId) {
        if(postId == null || postId <= 0) {
            throw new CommentReadException(
                    CommentReadException.Code.POST_NOT_FOUND
            );
        }

        postRepository.findVisiblePostById(postId)
                .orElseThrow(() ->
                        new CommentReadException(
                                CommentReadException.Code.POST_NOT_FOUND
                        )
                );
    }
}
