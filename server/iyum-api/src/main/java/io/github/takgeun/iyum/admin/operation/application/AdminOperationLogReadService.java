package io.github.takgeun.iyum.admin.operation.application;

import io.github.takgeun.iyum.admin.moderation.domain.ContentModerationLog;
import io.github.takgeun.iyum.admin.operation.infrastructure.AdminOperationLogReadRepository;
import io.github.takgeun.iyum.admin.operation.infrastructure.AdminOperationLogTargetReadRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminOperationLogReadService {

    private final MemberRepository memberRepository;
    private final AdminOperationLogReadRepository operationLogReadRepository;
    private final AdminOperationLogTargetReadRepository targetReadRepository;

    /**
     * 주의)
     * actorMemberId는 지금 조회를 요청하는 회원이며,
     * 결과 객체의 actorMemberId는 과거에 해당 숨김·복원을 처리한 관리자임.
     * 즉, 서로 다른 회원인 경우도 있음을 유의
     */
    public Page<AdminOperationLogItemResult> getLogs(
            @Nullable Long actorMemberId,
            long credentialVersion,
            AdminOperationLogReadQuery query
    ) {
        // 운영 기록 조회보다 먼저 요청자의 자격을 확인한다.
        validateAdmin(actorMemberId, credentialVersion);

        // 운영 기록 페이지를 먼저 확정한 뒤, 해당 대상만 조회한다.
        Page<ContentModerationLog> logs =
                operationLogReadRepository.findLogs(query);

        // 현재 페이지에 포함된 게시글 ID만 중복 없이 수집한다.
        // 즉, 동일 대상의 기록이 여러 개 있어도 대상 정보는 한 번만 조회한다.
        Set<Long> postIds = logs.getContent()
                .stream()
                .map(ContentModerationLog::getPostId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 현재 페이지에 포함된 댓글 ID만 중복 없이 수집한다.
        // 즉, 동일 대상의 기록이 여러 개 있어도 대상 정보는 한 번만 조회한다.
        Set<Long> commentIds = logs.getContent()
                .stream()
                .map(ContentModerationLog::getCommentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 빈 ID 집합은 targetReadRepository에서 SQL 실행 없이 처리
        Map<Long, AdminOperationLogCurrentTargetResult> postSummaries =
                targetReadRepository.findPostSummaries(postIds);

        // 빈 ID 집합은 targetReadRepository에서 SQL 실행 없이 처리
        Map<Long, AdminOperationLogCurrentTargetResult> commentSummaries =
                targetReadRepository.findCommentSummaries(commentIds);

        // 엔티티 변환은 서비스 트랜잭션 안에서 완료한다.
        // Page.map은 페이지 번호·크기·전체 개수·정렬 정보를 유지한다. + 요약 추가
        // postId가 존재하면 게시글 요약, 존재하지 않으면 댓글 요약 정보 넘기기
        return logs.map(log -> {
            AdminOperationLogCurrentTargetResult currentTarget =
                    log.getPostId() != null
                            ? postSummaries.get(log.getPostId())
                            : commentSummaries.get(log.getCommentId());

            return AdminOperationLogItemResult.from(
                    log,
                    currentTarget
            );
        });
    }

    private void validateAdmin(
            @Nullable Long actorMemberId,
            long credentialVersion
    ) {
        if (actorMemberId == null
                || actorMemberId <= 0
                || credentialVersion < 0) {
            throw new AdminOperationLogReadException(
                    AdminOperationLogReadException.Code.AUTHENTICATION_REQUIRED
            );
        }

        Member actor = memberRepository.findById(actorMemberId)
                .orElseThrow(() ->
                        new AdminOperationLogReadException(
                                AdminOperationLogReadException.Code.AUTHENTICATION_REQUIRED
                        )
                );

        // 세션에 관리자 정보가 남아 있어도
        // 현재 DB 상태와 인증 버전이 유효해야 한다.
        if (actor.getStatus() != MemberStatus.ACTIVE
                || actor.getCredentialVersion() != credentialVersion) {
            throw new AdminOperationLogReadException(
                    AdminOperationLogReadException.Code.AUTHENTICATION_REQUIRED
            );
        }

        // 인증 실패와 관리자 권한 부족을 구분한다.
        if (actor.getRole() != MemberRole.ADMIN) {
            throw new AdminOperationLogReadException(
                    AdminOperationLogReadException.Code.ADMIN_REQUIRED
            );
        }
    }
}
