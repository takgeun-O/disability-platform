package io.github.takgeun.iyum.admin.content.application;

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

/**
 * 엔티티 변환은 서비스 트랜잭션 안에서 완료한다.
 * 컨트롤러에는 Post나 Member 엔티티가 전달되지 않음.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPostReadService {

    private final MemberRepository memberRepository;
    private final AdminPostReadRepository postReadRepository;

    public Page<AdminPostItemResult> getPosts(
            @Nullable Long actorMemberId,
            long credentialVersion,
            AdminPostReadQuery query
    ) {
        validateAdmin(actorMemberId, credentialVersion);

        // 게시글마다 결과 객체를 만들면서
        // 페이지 번호·크기·전체 개수도 유지
        return postReadRepository.findPosts(query)
                .map(AdminPostItemResult::from);
    }

    public AdminPostDetailResult getPost(
            @Nullable Long actorMemberId,
            long credentialVersion,
            @Nullable Long postId
    )  {
        // 게시글 조회보다 먼저 요청자의 관리자 자격을 확인한다.
        validateAdmin(actorMemberId, credentialVersion);

        if(postId == null || postId <= 0) {
            throw new AdminContentReadException(
                    AdminContentReadException.Code.POST_NOT_FOUND
            );
        }

        Post post = postReadRepository.findPostById(postId)
                .orElseThrow(() ->
                        new AdminContentReadException(
                                AdminContentReadException.Code.POST_NOT_FOUND
                        )
                );

        return AdminPostDetailResult.from(post);
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

        // 비활성 회원 또는 오래된 인증 정보는 로그인부터 다시 요구한다.
        if (actor.getStatus() != MemberStatus.ACTIVE
                || actor.getCredentialVersion() != credentialVersion) {
            throw new AdminContentReadException(
                    AdminContentReadException.Code.AUTHENTICATION_REQUIRED
            );
        }

        // 유효한 인증 정보이지만 관리자가 아닌 경우를 구분한다.
        if(actor.getRole() != MemberRole.ADMIN) {
            throw new AdminContentReadException(
                    AdminContentReadException.Code.ADMIN_REQUIRED
            );
        }
    }
}
