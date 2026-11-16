package io.github.takgeun.iyum.admin.report.application;

import io.github.takgeun.iyum.admin.moderation.application.ContentModerationException;
import io.github.takgeun.iyum.admin.moderation.domain.ContentModerationLog;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationInputPolicy;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationReasonType;
import io.github.takgeun.iyum.admin.moderation.infrastructure.ContentModerationLogRepository;
import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.community.report.domain.*;
import io.github.takgeun.iyum.community.report.infrastructure.ContentReportRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminReportHideAndResolveService {

    private final MemberRepository memberRepository;
    private final ContentReportRepository reportRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ContentModerationLogRepository logRepository;
    private final Clock clock;

    /**
     * 신고 대상을 숨기고 해당 신고 한 건을 처리 완료한다.
     * <p>
     * 콘텐츠 변경, 운영 기록 저장, 신고 종결은
     * 하나의 트랜잭션에서 실행한다.
     */
    public AdminReportHandlingResult hideAndResolve(
            @Nullable Long actorMemberId,
            long credentialVersion,
            @Nullable Long reportId,
            @Nullable String handlingReason
    ) {
        // 1. 신고 조회 전에 요청자의 현재 관리자 자격을 확인한다.
        validateAdminForUpdate(
                actorMemberId,
                credentialVersion
        );

        // 2. 신고를 잠그고 이미 종결됐는지 확인한다.
        ContentReport report = findReportForUpdate(reportId);

        if (report.getStatus() != ContentReportStatus.RECEIVED) {
            throw new AdminReportHandlingException(
                    AdminReportHandlingException.Code.REPORT_ALREADY_HANDLED
            );
        }

        // 콘텐츠를 변경하기 전에 두 기록에 사용할 사유를 검증한다.
        String normalizedReason =
                normalizeHandlingReason(handlingReason);

        // DB와 엔티티에서 보장하는 대상 조건을 방어적으로 확인한다.
        if ((report.getPostId() == null)
                == (report.getCommentId() == null)) {
            throw new IllegalStateException(
                    "신고 대상은 게시글 또는 댓글 중 하나여야 합니다."
            );
        }

        // 3. 실제 신고 대상만 잠금 조회하고 숨긴다.
        HideResult hideResult;  // 처리 여부, 처리 시각

        if(report.getPostId() != null) {
            hideResult = hidePost(
                    actorMemberId,
                    report.getPostId(),
                    normalizedReason
            );
        } else {
            hideResult = hideComment(
                    actorMemberId,
                    report.getCommentId(),
                    normalizedReason
            );
        }

        ContentReportHandlingOutcome outcome = hideResult.changed()
                ? ContentReportHandlingOutcome.CONTENT_HIDDEN
                : ContentReportHandlingOutcome.CONTENT_ALREADY_HIDDEN;

        // 4. 실제 실행 결과로 신고를 종결한다.
        try {
            report.resolve(
                    actorMemberId,
                    normalizedReason,
                    hideResult.handledAt,
                    outcome
            );
        } catch (ContentReportAlreadyHandledException exception) {
            throw new AdminReportHandlingException(
                    AdminReportHandlingException.Code.REPORT_ALREADY_HANDLED
            );
        }

        // report와 콘텐츠는 영속 상태이므로 변경 감지로 저장된다.
        // 커밋 실패를 포함해 예외가 발생하면 함께 롤백
        return AdminReportHandlingResult.from(report);
    }

    private void validateAdminForUpdate(
            @Nullable Long actorMemberId,
            long credentialVersion
    ) {
        if (actorMemberId == null
                || actorMemberId <= 0
                || credentialVersion < 0) {
            throw new AdminReportHandlingException(
                    AdminReportHandlingException.Code.AUTHENTICATION_REQUIRED
            );
        }

        // 행위자 잠금 조회
        Member actor = memberRepository
                .findByIdForUpdate(actorMemberId)
                .orElseThrow(() ->
                        new AdminReportHandlingException(
                                AdminReportHandlingException.Code.AUTHENTICATION_REQUIRED
                        )
                );

        if (actor.getStatus() != MemberStatus.ACTIVE
                || actor.getCredentialVersion() != credentialVersion) {
            throw new AdminReportHandlingException(
                    AdminReportHandlingException.Code.AUTHENTICATION_REQUIRED
            );
        }

        if (actor.getRole() != MemberRole.ADMIN) {
            throw new AdminReportHandlingException(
                    AdminReportHandlingException.Code.ADMIN_REQUIRED
            );
        }
    }

    private String normalizeHandlingReason(
            @Nullable String reason
    ) {
        try {
            String normalized =
                    ContentReportHandlingPolicy
                            .normalizeHandlingReason(reason);

            // 신고 처리 정보와 운영 기록 양쪽의 입력 정책을
            // 콘텐츠 변경 전에 모두 검증
            return ModerationInputPolicy.normalizeReasonDetail(
                    ModerationAction.HIDE,
                    // 운영 기록의 사유 유형은 OTHER 사용
                    // 현재 신고 처리 기능에서는 관리자가 사유 유형을 선택하지 않고,
                    // 처리 사유 문장만 입력하기 떄문
                    // 신고자가 신고한 이유와 관리자가 실제로 숨긴 이유를 구분해서 생각해야 한다.
                    ModerationReasonType.OTHER,
                    normalized
            );
        } catch (IllegalArgumentException exception) {
            throw new AdminReportHandlingException(
                    AdminReportHandlingException.Code.INVALID_HANDLING_REASON
            );
        }
    }

    private ContentReport findReportForUpdate(
            @Nullable Long reportId
    ) {
        if (reportId == null || reportId <= 0) {
            throw new AdminReportHandlingException(
                    AdminReportHandlingException.Code.REPORT_NOT_FOUND
            );
        }

        return reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() ->
                        new AdminReportHandlingException(
                                AdminReportHandlingException.Code.REPORT_NOT_FOUND
                        )
                );
    }

    private HideResult hidePost(
            Long actorMemberId,
            Long postId,
            String reason
    ) {
        Post post = lockPostForModeration(postId);

        // 콘텐츠 잠금 대기가 끝난 뒤 처리 시각을 얻는다.
        Instant handledAt = clock.instant();

        boolean changed = post.hide(handledAt);

        if(changed) {
            logRepository.save(
                    ContentModerationLog.forPost(
                            actorMemberId,
                            post.getId(),
                            ModerationAction.HIDE,
                            ModerationReasonType.OTHER,
                            reason,
                            handledAt
                    )
            );
        }

        return new HideResult(changed, handledAt);
    }

    private HideResult hideComment(
            Long actorMemberId,
            Long commentId,
            String reason
    ) {
        // 댓글 엔티티를 먼저 읽지 않고 부모 ID만 조회한다.
        Long postId = commentRepository
                .findPostIdByCommentIdForModeration(commentId)
                .orElseThrow(() ->
                        new ContentModerationException(
                                ContentModerationException.Code.COMMENT_NOT_FOUND
                        )
                );

        // 기존 댓글 조치와 동일하게 부모 게시글부터 잠근다.
        Post post = lockPostForModeration(postId);

        // 부모 게시글이 숨겨져 있어도 댓글 개별 숨김은 허용한다.
        // 부모 게시글의 상태는 변경하지 않는다.
        Comment comment = commentRepository
                .findCommentByIdForModerationForUpdate(
                        post.getId(),
                        commentId
                )
                .orElseThrow(() ->
                        new ContentModerationException(
                                ContentModerationException.Code.COMMENT_NOT_FOUND
                        )
                );

        if(comment.getDeletedAt() != null) {
            throw new ContentModerationException(
                    ContentModerationException.Code.COMMENT_DELETED
            );
        }

        Instant handledAt = clock.instant();

        boolean changed = comment.hide(handledAt);

        if(changed) {
            logRepository.save(
                    ContentModerationLog.forComment(
                            actorMemberId,
                            comment.getId(),
                            ModerationAction.HIDE,
                            ModerationReasonType.OTHER,
                            reason,
                            handledAt
                    )
            );
        }

        return new HideResult(changed, handledAt);
    }

    private Post lockPostForModeration(Long postId) {
        Post post = postRepository
                .findPostByIdForModerationForUpdate(postId)
                .orElseThrow(() ->
                        new ContentModerationException(
                                ContentModerationException.Code.POST_NOT_FOUND
                        )
                );

        // 해당 게시글이 삭제됐을 경우
        if(post.getDeletedAt() != null) {
            throw new ContentModerationException(
                    ContentModerationException.Code.POST_DELETED
            );
        }

        return post;
    }

    private record HideResult(
            boolean changed,
            Instant handledAt
    ){

    }
}
