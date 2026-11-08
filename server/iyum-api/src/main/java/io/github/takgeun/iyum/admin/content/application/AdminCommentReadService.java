package io.github.takgeun.iyum.admin.content.application;

import io.github.takgeun.iyum.admin.content.infrastructure.AdminCommentReadRepository;
import io.github.takgeun.iyum.admin.content.infrastructure.AdminPostReadRepository;
import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCommentReadService {

    private final MemberRepository memberRepository;
    private final AdminPostReadRepository postReadRepository;
    private final AdminCommentReadRepository commentReadRepository;

    public AdminCommentListResult getComments(
            @Nullable Long actorMemberId,
            long credentialVersion,
            @Nullable Long postId,
            AdminCommentReadQuery query
    ) {
        // 콘텐츠 조회 전에 관리자 자격부터 확인
        validateAdmin(actorMemberId, credentialVersion);

        if(postId == null || postId <= 0) {
            throw new AdminContentReadException(
                    AdminContentReadException.Code.POST_NOT_FOUND
            );
        }

        // 관리자용 저장소이므로 숨김·삭제 게시글도 조회한다.
        Post post = postReadRepository.findPostById(postId)
                .orElseThrow(() ->
                        new AdminContentReadException(
                                AdminContentReadException.Code.POST_NOT_FOUND
                        )
                );

        Page<AdminCommentItemResult> comments =
                commentReadRepository.findComments(post.getId(), query)
                        .map(AdminCommentItemResult::from);

        return new AdminCommentListResult(
                post.getId(),
                AdminPostStatus.from(post),
                comments
        );
    }

    /**
     * 관리자 댓글 상세 조회
     *
     * 콘텐츠 존재 여부를 확인하기 전에 관리자 자격을 검증한다.
     * 숨김·삭제 댓글과 숨김·삭제 게시글에 속한 댓글도 조회할 수 있다.
     */
    public AdminCommentDetailResult getComment(
            @Nullable Long actorMemberId,
            long credentialVersion,
            @Nullable Long commentId
    ) {
        // 기존 목록 조회와 동일하게 회원 상태·인증 버전·관리자 권한을 검증한다.
        validateAdmin(actorMemberId, credentialVersion);

        // 기존 게시글 조회 정책과 동일하게 유효하지 않은 ID는
        // 해당 콘텐츠를 찾을 수 없는 경우로 처리한다.
        if(commentId == null || commentId <= 0) {
            throw new AdminContentReadException(
                    AdminContentReadException.Code.COMMENT_NOT_FOUND
            );
        }

        Comment comment = commentReadRepository.findCommentById(commentId)
                .orElseThrow(() ->
                        new AdminContentReadException(
                                AdminContentReadException.Code.COMMENT_NOT_FOUND
                        )
                );

        // Repository에서 작성자와 부모 게시글을 함께 로딩한다.
        // 서비스의 읽기 전용 트랜잭션 안에서 결과 객체로 변환한다.
        return AdminCommentDetailResult.from(comment);
    }

    private void validateAdmin(
            @Nullable Long actorMemberId,
            long credentialVersion
    ) {
        if (actorMemberId == null
                || actorMemberId <= 0
                || credentialVersion < 0) {
            throw new AdminContentReadException(
                    AdminContentReadException.Code.AUTHENTICATION_REQUIRED
            );
        }

        Member actor = memberRepository.findById(actorMemberId)
                .orElseThrow(() ->
                        new AdminContentReadException(
                                AdminContentReadException.Code.AUTHENTICATION_REQUIRED
                        )
                );

        if (actor.getStatus() != MemberStatus.ACTIVE
                || actor.getCredentialVersion() != credentialVersion) {
            throw new AdminContentReadException(
                    AdminContentReadException.Code.AUTHENTICATION_REQUIRED
            );
        }

        if(actor.getRole() != MemberRole.ADMIN) {
            throw new AdminContentReadException(
                    AdminContentReadException.Code.ADMIN_REQUIRED
            );
        }
    }
}
