package io.github.takgeun.iyum.admin.moderation.application;

import io.github.takgeun.iyum.admin.moderation.domain.ContentModerationLog;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationInputPolicy;
import io.github.takgeun.iyum.admin.moderation.infrastructure.ContentModerationLogRepository;
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

/**
 * 1. 요청한 회원을 잠금 조회하고 현재 관리자 자격 확인
 * 2. 사유 검증
 * 3. 게시글 잠금 조회 및 삭제 여부 확인
 * 4. 숨김·복원 처리
 * 5. 실제 상태가 변경됐을 때만 이력 저장
 */
@Service
@RequiredArgsConstructor
public class PostModerationService {

    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final ContentModerationLogRepository logRepository;

    // 여기서 회원ID와 인증 버전은 컨트롤러의 MemberPrincipal로부터 전달 받음
    // 요청 본문이나 쿼리 파라미터로 받지 않도록 주의
    @Transactional
    public PostModerationResult hide(
            Long actorMemberId,
            long credentialVersion,
            Long postId,
            String reason
    ) {
        return changeState(
                actorMemberId,
                credentialVersion,
                postId,
                reason,
                ModerationAction.HIDE
        );
    }

    @Transactional
    public PostModerationResult restore(
            Long actorMemberId,
            long credentialVersion,
            Long postId,
            String reason
    ) {
        return changeState(
                actorMemberId,
                credentialVersion,
                postId,
                reason,
                ModerationAction.RESTORE
        );
    }

    private PostModerationResult changeState(
            Long actorMemberId,
            long credentialVersion,
            Long postId,
            String reason,
            ModerationAction action
    ) {

        // 1. 요청 관리자 회원을 먼저 잠근다.
        Member actor = lockAndValidateAdmin(
                actorMemberId,
                credentialVersion
        );

        // 중복 요청이어도 사유 검증은 동일하게 수행한다.
        String normalizedReason = validateReason(reason);

        if (postId == null || postId <= 0) {
            throw new ContentModerationException(
                    ContentModerationException.Code.POST_NOT_FOUND
            );
        }

        // 2. 관리자 회원 -> 게시글 순서로 잠금을 획득한다.
        // 관리자 회원은 바로 위에서 잠갔으니 여기서는 게시글 잠금.
        Post post = postRepository
                .findPostByIdForModerationForUpdate(postId)
                .orElseThrow(() ->
                        new ContentModerationException(
                                ContentModerationException.Code.POST_NOT_FOUND
                        )
                );

        // 관리자용 조회는 삭제된 게시글도 가져오므로 여기서 거절한다.
        if(post.getDeletedAt() != null) {
            throw new ContentModerationException(
                    ContentModerationException.Code.POST_DELETED
            );
        }

        // 숨김 시각과 이력의 처리 시각에 같은 값을 사용한다.
        Instant now = Instant.now();

        boolean changed = switch (action) {
            case HIDE -> post.hide(now);
            case RESTORE -> post.restore();
        };

        // 3. 실제 상태 변경이 있을 때만 이력을 저장한다.
        if(changed) {
            logRepository.save(
                    ContentModerationLog.forPost(
                            actor.getId(),
                            post.getId(),
                            action,
                            normalizedReason,
                            now
                    )
            );
        }

        // post는 관리 상태이므로 변경 감지로 저장된다.
        return new PostModerationResult(
                post.getId(),
                post.getHiddenAt() != null,
                post.getHiddenAt(),
                changed
        );
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

    private String validateReason(String reason) {
        try {
            return ModerationInputPolicy.normalizeReason(reason);
        } catch (IllegalArgumentException exception) {
            throw new ContentModerationException(
                    ContentModerationException.Code.INVALID_REASON
            );
        }
    }
}
