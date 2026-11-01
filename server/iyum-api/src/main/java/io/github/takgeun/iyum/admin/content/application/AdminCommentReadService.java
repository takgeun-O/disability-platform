package io.github.takgeun.iyum.admin.content.application;

import io.github.takgeun.iyum.admin.content.infrastructure.AdminCommentReadRepository;
import io.github.takgeun.iyum.admin.content.infrastructure.AdminPostReadRepository;
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
