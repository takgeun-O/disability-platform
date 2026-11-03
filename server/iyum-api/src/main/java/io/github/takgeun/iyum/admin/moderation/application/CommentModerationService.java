package io.github.takgeun.iyum.admin.moderation.application;

import io.github.takgeun.iyum.admin.moderation.domain.ContentModerationLog;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationInputPolicy;
import io.github.takgeun.iyum.admin.moderation.infrastructure.ContentModerationLogRepository;
import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CommentModerationService {

    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ContentModerationLogRepository logRepository;

    @Transactional
    public CommentModerationResult hide(
            Long actorMemberId,
            long credentialVersion,
            Long postId,
            Long commentId,
            String reason
    ) {
        return changeState(
                actorMemberId,
                credentialVersion,
                postId,
                commentId,
                reason,
                ModerationAction.HIDE
        );
    }

    @Transactional
    public CommentModerationResult restore(
            Long actorMemberId,
            long credentialVersion,
            Long postId,
            Long commentId,
            String reason
    ) {
        return changeState(
                actorMemberId,
                credentialVersion,
                postId,
                commentId,
                reason,
                ModerationAction.RESTORE
        );
    }

    private CommentModerationResult changeState(
            Long actorMemberId,
            long credentialVersion,
            Long postId,
            Long commentId,
            String reason,
            ModerationAction action
    ) {

        // 1. 요청 관리자 회원을 잠그고 현재 자격을 확인한다.
        Member actor = lockAndValidateAdmin(
                actorMemberId,
                credentialVersion
        );

        // 중복 요청도 사유 검증은 동일하게 수행한다.
        String normalizedReason = validateReason(reason);

        if (postId == null || postId <= 0) {
            throw new ContentModerationException(
                    ContentModerationException.Code.POST_NOT_FOUND
            );
        }

        // 2. 부모 게시글을 먼저 잠근다.
        Post post = postRepository
                .findPostByIdForModerationForUpdate(postId)
                .orElseThrow(() ->
                        new ContentModerationException(
                                ContentModerationException.Code.POST_NOT_FOUND
                        )
                );

        if (post.getDeletedAt() != null) {
            throw new ContentModerationException(
                    ContentModerationException.Code.POST_DELETED
            );
        }

        // 부모 게시글의 숨김 상태는 변경하지 않는다.
        // 부모가 숨겨져 있어도 댓글의 개별 숨김·복원은 허용한다.
        if (commentId == null || commentId <= 0) {
            throw new ContentModerationException(
                    ContentModerationException.Code.COMMENT_NOT_FOUND
            );
        }

        // 3. 해당 게시글에 속한 댓글을 잠근다.
        Comment comment = commentRepository.findCommentByIdForModerationForUpdate(
                        post.getId(),
                        commentId
                )
                .orElseThrow(() ->
                        new ContentModerationException(
                                ContentModerationException.Code.COMMENT_NOT_FOUND
                        )
                );

        if (comment.getDeletedAt() != null) {
            throw new ContentModerationException(
                    ContentModerationException.Code.COMMENT_DELETED
            );
        }

        Instant now = Instant.now();

        boolean changed = switch (action) {
            case HIDE -> comment.hide(now);
            case RESTORE -> comment.restore();
        };

        // 4. 실제로 댓글 상태가 변경된 경우에만 이력을 저장한다.
        // 이 때 부모 게시글 ID를 알고 있더라도 두 컬럼을 동시에 채우면 안된다.
        // DB 제약 조건은 처리 대상이 게시글 또는 댓글 중 "정확히" 하나일 것을 요구함.
        if (changed) {
            logRepository.save(
                    ContentModerationLog.forComment(
                            actor.getId(),
                            comment.getId(),
                            action,
                            normalizedReason,
                            now
                    )
            );
        }

        return new CommentModerationResult(
                post.getId(),
                comment.getId(),
                comment.getHiddenAt() != null,
                comment.getHiddenAt(),
                changed
        );
    }


    private String validateReason(String reason) {
        try {
            return ModerationInputPolicy.normalizeReason(reason);
        } catch (IllegalArgumentException exception) {
            throw new ContentModerationException(
                    ContentModerationException.Code.INVALID_REASON
            );
        }
    }

    private Member lockAndValidateAdmin(
            Long actorMemberId,
            long credentialVersion
    ) {
        if (actorMemberId == null
                || actorMemberId <= 0
                || credentialVersion < 0) {
            throw new ContentModerationException(
                    ContentModerationException.Code.AUTHENTICATION_REQUIRED
            );
        }

        Member actor = memberRepository
                .findByIdForUpdate(actorMemberId)
                .orElseThrow(() ->
                        new ContentModerationException(
                                ContentModerationException.Code.AUTHENTICATION_REQUIRED
                        )
                );

        if (actor.getStatus() != MemberStatus.ACTIVE
                || actor.getCredentialVersion() != credentialVersion) {
            throw new ContentModerationException(
                    ContentModerationException.Code.AUTHENTICATION_REQUIRED
            );
        }

        if (actor.getRole() != MemberRole.ADMIN) {
            throw new ContentModerationException(
                    ContentModerationException.Code.ADMIN_REQUIRED
            );
        }

        return actor;
    }
}
