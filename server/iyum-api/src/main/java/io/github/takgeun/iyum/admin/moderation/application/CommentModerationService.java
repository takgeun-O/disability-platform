package io.github.takgeun.iyum.admin.moderation.application;

import io.github.takgeun.iyum.admin.moderation.domain.ContentModerationLog;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationInputPolicy;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationReasonType;
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
    public CommentModerationResult hide(
            Long actorMemberId,
            long credentialVersion,
            Long postId,
            Long commentId,
            ModerationReasonType reasonType,
            String reasonDetail
    ) {
        return changeState(
                actorMemberId,
                credentialVersion,
                postId,
                commentId,
                reasonType,
                reasonDetail,
                ModerationAction.HIDE,
                false   // 새 유형 입력 방식이라는 뜻. (서비스 내부 전환용이며 요청 파라미터로 노출하지 않음.)
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

    @Transactional
    public CommentModerationResult restore(
            Long actorMemberId,
            long credentialVersion,
            Long postId,
            Long commentId,
            ModerationReasonType reasonType,
            String reasonDetail
    ) {
        return changeState(
                actorMemberId,
                credentialVersion,
                postId,
                commentId,
                reasonType,
                reasonDetail,
                ModerationAction.RESTORE,
                false   // 새 유형 입력 방식이라는 뜻. (서비스 내부 전환용이며 요청 파라미터로 노출하지 않음.)
        );
    }

    /**
     * 기존 자유 입력 API를 위한 임시 호환 경로.
     * 컨트롤러 전환 완료 후 제거한다.
     */
    private CommentModerationResult changeState(
            Long actorMemberId,
            long credentialVersion,
            Long postId,
            Long commentId,
            String reason,
            ModerationAction action
    ) {
        return changeState(
                actorMemberId,
                credentialVersion,
                postId,
                commentId,
                null,
                reason,
                action,
                true
        );
    }

    private CommentModerationResult changeState(
            Long actorMemberId,
            long credentialVersion,
            Long postId,
            Long commentId,
            ModerationReasonType reasonType,
            String reasonDetail,
            ModerationAction action,
            boolean legacyInput
    ) {

        // 1. 요청 관리자 회원을 잠그고 현재 자격을 확인한다.
        Member actor = lockAndValidateAdmin(
                actorMemberId,
                credentialVersion
        );

        // 관리자 자격을 먼저 확인한다.
        // 중복 요청도 게시글·댓글 조회 전에 사유를 검증한다.
        String normalizedReason = legacyInput
                ? validateReason(reasonDetail)
                : validateReasonDetail(action, reasonType, reasonDetail);

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

        /**
         * 여기서 postId와 commentId는 어느 게시글의 어느 댓글인가를 함께 기록하는 필드가 아니라,
         * "이번 조치의 직접 대상은 무엇인가"를 구분하는 필드임.
         * 만약 postId = 10L; commentId = 25L; 처럼 댓글 숨김 기록에 저장하면
         * 이 기록은 게시글 처리 이력 검색에도 나오고, 댓글 처리 이력 검색에도 나온다.
         * 실제로는 댓글만 숨겼는데 게시글도 처리한 것처럼 분류되는 것.
         * 서비스에서 필요한 정보가 모두 이력의 "처리 대상" 필드에 들어가야 하는 건 아님.
         */
        // 4. 실제로 댓글 상태가 변경된 경우에만 이력을 저장한다.
        // 이 때 부모 게시글 ID를 알고 있더라도 두 컬럼을 동시에 채우면 안된다.
        // DB 제약 조건은 처리 대상이 게시글 또는 댓글 중 "정확히" 하나일 것을 요구함.
        if (changed) {
            ContentModerationLog log = legacyInput
                    ? ContentModerationLog.forComment(
                    actor.getId(),
                    comment.getId(),
                    action,
                    normalizedReason,
                    now
            )
                    : ContentModerationLog.forComment(
                    actor.getId(),
                    comment.getId(),
                    action,
                    reasonType,
                    normalizedReason,
                    now
            );

            logRepository.save(log);
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

    private String validateReasonDetail(
            ModerationAction action,
            ModerationReasonType reasonType,
            String reasonDetail
    ) {
        try {
            return ModerationInputPolicy.normalizeReasonDetail(
                    action,
                    reasonType,
                    reasonDetail
            );
        } catch (IllegalArgumentException exception) {
            throw new ContentModerationException(
                    ContentModerationException.Code.INVALID_REASON
            );
        }
    }
}
