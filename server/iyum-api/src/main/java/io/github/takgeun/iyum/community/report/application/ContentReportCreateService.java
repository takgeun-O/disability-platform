package io.github.takgeun.iyum.community.report.application;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.community.report.domain.ContentReport;
import io.github.takgeun.iyum.community.report.domain.ContentReportInputPolicy;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.community.report.infrastructure.ContentReportRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class ContentReportCreateService {

    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ContentReportRepository reportRepository;
    private final Clock clock;

    /**
     * reporterMemberId와 credentialVersion은
     * 로그인한 MemberPrincipal에서 전달받는다.
     * <p>
     * 요청 본문에서 신고자 ID나 인증 버전을 받지 않는다.
     */
    @Transactional
    public ContentReportCreateResult reportPost(
            Long reporterMemberId,
            long credentialVersion,
            Long postId,
            ContentReportReasonType reasonType,
            String reasonDetail
    ) {
        // 1. 신고자 잠금 및 현재 인증 상태 확인
        // 신고할 자격을 확인한 뒤 신고 저장이 끝날 때까지,
        // 다른 요청이 신고자의 상태나 인증 버전을 바꾸지 못하게 해야 한다.
        Member reporter = lockAndValidateReporter(
                reporterMemberId,
                credentialVersion
        );

        // 대상 조회 전에 신고 입력값을 검증한다.
        String normalizedDetail = normalizeReasonDetail(
                reasonType,
                reasonDetail
        );

        // 2. 공개 게시글 잠금
        // 신고 대상의 상태와 내용을 보호한다.
        // 그렇지 않으면 신고 요청이 진행 중에 본문이 수정되고 커밋됐는데
        // 앞서 읽었던 문제의 내용으로 신고가 저장되기 때문
        // 참고로 잠금은 lockVisiblePost() 메서드가 끝나는 순간에 풀리지 않는다.
        // reportPost()의 트랜잭션이 커밋되거나 롤백될 때까지 유지
        Post post = lockVisiblePost(postId);

        // 3. 자기 게시글 신고 차단
        validateNotOwnContent(
                reporter.getId(),
                post.getAuthor().getId()
        );

        // 4. 기존 신고 확인
        // 처리 완료·기각 여부와 관계없이 중복 신고를 제한한다.
        if(reportRepository.findByReporterMemberIdAndPostId(
                reporter.getId(),
                post.getId()
        ).isPresent()) {
            throw new ContentReportCreateException(
                    ContentReportCreateException.Code.REPORT_ALREADY_EXISTS
            );
        }

        // 5. 서버가 잠금 조회한 원문으로 스냅샷 생성
        ContentReport report = ContentReport.forPost(
                reporter.getId(),
                post.getId(),
                reasonType,
                normalizedDetail,
                post.getTitle(),
                post.getContent(),
                clock.instant()
        );

        return saveReport(report);
    }

    @Transactional
    public ContentReportCreateResult reportComment(
            Long reporterMemberId,
            long credentialVersion,
            Long postId,
            Long commentId,
            ContentReportReasonType reasonType,
            String reasonDetail
    ) {
        // 1. 신고자 잠금 및 현재 인증 상태 확인
        Member reporter = lockAndValidateReporter(
                reporterMemberId,
                credentialVersion
        );

        String normalizedDetail = normalizeReasonDetail(
                reasonType,
                reasonDetail
        );

        // 2. 부모 게시글 잠금
        // 부모 게시글이 숨김·삭제된 경우 댓글 신고도 허용하지 않는다.
        Post post = lockVisiblePost(postId);

        // 3. 해당 게시글에 속한 공개 댓글 잠금
        Comment comment = lockVisibleComment(
                post.getId(),
                commentId
        );

        // 자기 게시글에 다른 회원이 쓴 댓글은 신고할 수 있다.
        // 여기서는 댓글 작성자와 신고자를 비교한다.
        validateNotOwnContent(
                reporter.getId(),
                comment.getAuthor().getId()
        );

        // 4. 기존 댓글 신고 확인
        if(reportRepository.findByReporterMemberIdAndCommentId(
                reporter.getId(),
                comment.getId()
        ).isPresent()) {
            throw new ContentReportCreateException(
                    ContentReportCreateException.Code.REPORT_ALREADY_EXISTS
            );
        }

        // 5. 서버가 읽은 댓글 본문으로 스냅샷 생성
        // ContentReport의 postId는 설정하지 않는다.
        ContentReport report = ContentReport.forComment(
                reporter.getId(),
                comment.getId(),
                reasonType,
                normalizedDetail,
                comment.getContent(),
                clock.instant()
        );

        return saveReport(report);
    }

    private Member lockAndValidateReporter(
            Long reporterMemberId,
            long credentialVersion
    ) {
        // 잘못된 인증 입력은 DB 조회 전에 차단한다.
        if (reporterMemberId == null
                || reporterMemberId <= 0
                || credentialVersion < 0) {
            throw authenticationRequired();
        }

        // 신고자 잠금 조회하는 이유
        // 신고할 자격을 확인한 뒤 신고 저장이 끝날 때까지,
        // 다른 요청이 신고자의 상태나 인증 버전을 바꾸지 못하게 하기 위해서
        Member reporter = memberRepository
                .findByIdForUpdate(reporterMemberId)
                .orElseThrow(
                        ContentReportCreateService::authenticationRequired
                );

        // 세션 필터 검사 이후 회원 상태나 인증 버전이
        // 변경됐을 가능성을 고려해 트랜잭션 안에서 다시 확인한다.
        if (reporter.getStatus() != MemberStatus.ACTIVE
                || reporter.getCredentialVersion() != credentialVersion) {
            throw authenticationRequired();
        }

        return reporter;
    }

    private Post lockVisiblePost(Long postId) {
        if(postId == null || postId <= 0) {
            throw new ContentReportCreateException(
                    ContentReportCreateException.Code.POST_NOT_FOUND
            );
        }

        return postRepository.findVisiblePostByIdForUpdate(postId)
                .orElseThrow(() ->
                        new ContentReportCreateException(
                                ContentReportCreateException.Code.POST_NOT_FOUND
                        )
                );
    }

    private Comment lockVisibleComment(
            Long postId,
            Long commentId
    ) {
        if(commentId == null || commentId <= 0) {
            throw new ContentReportCreateException(
                    ContentReportCreateException.Code.COMMENT_NOT_FOUND
            );
        }

        // 기존 저장소 메서드가 다음 조건을 함꼐 확인한다.
        // - 댓글 ID
        // - 부모 게시글 ID 일치
        // - 게시글과 댓글 모두 숨김·삭제 상태가 아님
        return commentRepository.findVisibleCommentByIdForUpdate(
                postId,
                commentId
        )
                .orElseThrow(() ->
                        new ContentReportCreateException(
                                ContentReportCreateException.Code.COMMENT_NOT_FOUND
                        )
                );
    }

    private void validateNotOwnContent(
            Long reporterMemberId,
            Long authorMemberId
    ) {
        if(reporterMemberId.equals(authorMemberId)) {
            throw new ContentReportCreateException(
                    ContentReportCreateException.Code.SELF_REPORT_NOT_ALLOWED
            );
        }
    }

    private String normalizeReasonDetail(
            ContentReportReasonType reasonType,
            String reasonDetail
    ) {
        try {
            return ContentReportInputPolicy.normalizeReasonDetail(
                    reasonType,
                    reasonDetail
            );
        } catch (IllegalArgumentException exception) {
            throw new ContentReportCreateException(
                    ContentReportCreateException.Code.INVALID_REASON
            );
        }
    }

    private ContentReportCreateResult saveReport(ContentReport report) {
        ContentReport saved = reportRepository.save(report);

        return new ContentReportCreateResult(
                saved.getId(),
                saved.getStatus(),
                saved.getCreatedAt()
        );
    }

    private static ContentReportCreateException authenticationRequired() {
        return new ContentReportCreateException(
                ContentReportCreateException.Code.AUTHENTICATION_REQUIRED
        );
    }
}
