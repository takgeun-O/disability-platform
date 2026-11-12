package io.github.takgeun.iyum.community.report.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "content_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "reporter_member_id",
            nullable = false,
            updatable = false
    )
    private Long reporterMemberId;

    /**
     * 신고의 직접 대상
     * 게시글 신고이면 postId만 설정
     */
    @Column(name = "post_id", updatable = false)
    private Long postId;

    /**
     * 댓글 신고면 commentId만 설정
     * 부모 게시글 ID를 postId에 함께 저장하지 않는다.
     * (만약 함께 저장하려고 하면 댓글만 신고한건데 게시글 신고까지 포함되는 문제가 발생할 것.)
     */
    @Column(name = "comment_id", updatable = false)
    private Long commentId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "reason_type",
            nullable = false,
            length = 40,
            updatable = false
    )
    private ContentReportReasonType reasonType;

    @Column(
            name = "reason_detail",
            length = ContentReportInputPolicy.REASON_DETAIL_MAX_LENGTH,
            updatable = false
    )
    private String reasonDetail;

    /**
     * 접수 당시 게시글 제목
     * 댓글 신고는 null이다.
     */
    @Column(
            name = "title_snapshot",
            columnDefinition = "text",
            updatable = false
    )
    private String titleSnapshot;

    /**
     * 접수 당시 게시글 또는 댓글 본문
     * 현재 원문이 수정되어도 이 값은 변경하지 않는다.
     */
    @Column(
            name = "content_snapshot",
            nullable = false,
            columnDefinition = "text",
            updatable = false
    )
    private String contentSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 20
    )
    private ContentReportStatus status;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    // 아래 3개 필드는 updatable 해야 한다. (신고 접수 후 처리할 때 값이 저장되어야 하기 때문)
    @Column(name = "handled_by_member_id")
    private Long handledByMemberId;

    @Column(name = "handled_at")
    private Instant handledAt;

    @Column(
            name = "handling_reason",
            length = ContentReportHandlingPolicy.HANDLING_REASON_MAX_LENGTH
    )
    private String handlingReason;

    private ContentReport(
            Long reporterMemberId,
            Long postId,
            Long commentId,
            ContentReportReasonType reasonType,
            String reasonDetail,
            String titleSnapshot,
            String contentSnapshot,
            Instant createdAt
    ) {
        validatePositiveId(reporterMemberId, "신고자 회원 ID");

        // 둘 다 null이거나 둘 다 존재하면 잘못된 대상이다.
        if ((postId == null) == (commentId == null)) {
            throw new IllegalArgumentException(
                    "신고 대상은 게시글 또는 댓글 중 하나여야 합니다."
            );
        }

        if (postId != null) {
            validatePositiveId(postId, "게시글 ID");
            validateSnapshotText(titleSnapshot, "게시글 제목");
        } else {
            validatePositiveId(commentId, "댓글 ID");

            if (titleSnapshot != null) {
                throw new IllegalArgumentException(
                        "댓글 신고에는 제목 스냅샷을 저장할 수 없습니다."
                );
            }
        }

        validateSnapshotText(contentSnapshot, "본문");

        if (createdAt == null) {
            throw new IllegalArgumentException(
                    "신고 접수 시각은 필수입니다."
            );
        }

        String normalizedDetail = ContentReportInputPolicy.normalizeReasonDetail(
                reasonType,
                reasonDetail
        );

        this.reporterMemberId = reporterMemberId;
        this.postId = postId;
        this.commentId = commentId;
        this.reasonType = reasonType;
        this.reasonDetail = normalizedDetail;

        // 원문의 공백·줄바꿈을 그대로 보존
        // 스냅샷은 서버에 저장된 원문을 복사하는 용도이기 때문에
        // 정규화를 한번 더 하지 않는다.
        this.titleSnapshot = titleSnapshot;
        this.contentSnapshot = contentSnapshot;

        this.status = ContentReportStatus.RECEIVED;
        this.createdAt = createdAt;
    }

    public static ContentReport forPost(
            Long reporterMemberId,
            Long postId,
            ContentReportReasonType reasonType,
            String reasonDetail,
            String titleSnapshot,
            String contentSnapshot,
            Instant createdAt
    ) {
        validatePositiveId(postId, "게시글 ID");

        return new ContentReport(
                reporterMemberId,
                postId,
                null,
                reasonType,
                reasonDetail,
                titleSnapshot,
                contentSnapshot,
                createdAt
        );
    }

    public static ContentReport forComment(
            Long reporterMemberId,
            Long commentId,
            ContentReportReasonType reasonType,
            String reasonDetail,
            String contentSnapshot,
            Instant createdAt
    ) {
        validatePositiveId(commentId, "댓글 ID");

        return new ContentReport(
                reporterMemberId,
                null,
                commentId,
                reasonType,
                reasonDetail,
                null,
                contentSnapshot,
                createdAt
        );
    }

    public void resolve(
            Long handlerMemberId,
            String reason,
            Instant handledAt
    ) {
        handle(
                ContentReportStatus.RESOLVED,
                handlerMemberId,
                reason,
                handledAt
        );
    }

    public void dismiss(
            Long handlerMemberId,
            String reason,
            Instant handledAt
    ) {
        handle(
                ContentReportStatus.DISMISSED,
                handlerMemberId,
                reason,
                handledAt
        );
    }

    private static void validatePositiveId(Long id, String name) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    name + "는 양수여야 합니다."
            );
        }
    }

    private static void validateSnapshotText(
            String value,
            String name
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    name + " 스냅샷은 필수입니다."
            );
        }
    }

    private void handle(
            ContentReportStatus nextStatus,
            Long handlerMemberId,
            String reason,
            Instant handledAt
    ) {
        if(this.status != ContentReportStatus.RECEIVED) {
            throw new ContentReportAlreadyHandledException();
        }

        validatePositiveId(handlerMemberId, "처리 관리자 회원 ID");

        if(handledAt == null) {
            throw new IllegalArgumentException(
                    "신고 처리 시각은 필수입니다."
            );
        }

        String normalizedReason =
                ContentReportHandlingPolicy.normalizeHandlingReason(reason);

        // 모든 검증이 끝난 뒤 상태와 처리 정보를 함께 변경
        this.handledByMemberId = handlerMemberId;
        this.handledAt = handledAt;
        this.handlingReason = normalizedReason;
        this.status = nextStatus;
    }
}
