package io.github.takgeun.iyum.admin.moderation.api;

import io.github.takgeun.iyum.admin.moderation.api.dto.ContentModerationRequest;
import io.github.takgeun.iyum.admin.moderation.api.dto.PostModerationResponse;
import io.github.takgeun.iyum.admin.moderation.application.ContentModerationException;
import io.github.takgeun.iyum.admin.moderation.application.PostModerationResult;
import io.github.takgeun.iyum.admin.moderation.application.PostModerationService;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "관리자 게시글 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/posts")
public class AdminPostModerationController {

    private final PostModerationService postModerationService;

    @Operation(
            summary = "게시글 숨김",
            description = """
                    ACTIVE 관리자만 사용할 수 있습니다.
                    처리 사유와 유효한 로그인 세션·CSRF 토큰이 필요합니다.
                    이미 숨김 상태이면 changed=false를 반환하며
                    처리 이력을 추가하지 않습니다.
                    """
    )
    @PostMapping(
            value = "/{postId}/hide",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<PostModerationResponse> hide(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal,

            @PathVariable("postId") Long postId,
            @RequestBody ContentModerationRequest request
    ) {
        requirePrincipal(principal);

        PostModerationResult result = postModerationService.hide(
                principal.getMemberId(),
                principal.getCredentialVersion(),
                postId,
                request.reasonType(),
                request.reasonDetail()
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PostModerationResponse.from(result));
    }

    @Operation(
            summary = "게시글 복원",
            description = """
                    ACTIVE 관리자만 사용할 수 있습니다.
                    처리 사유와 유효한 로그인 세션·CSRF 토큰이 필요합니다.
                    이미 공개 상태이면 changed=false를 반환하며
                    처리 이력을 추가하지 않습니다.
                    작성자가 삭제한 게시글은 복원할 수 없습니다.
                    """
    )
    @PostMapping(
            value = "/{postId}/restore",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<PostModerationResponse> restore(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal,

            @PathVariable("postId") Long postId,
            @RequestBody ContentModerationRequest request
    ) {
        requirePrincipal(principal);

        PostModerationResult result = postModerationService.restore(
                principal.getMemberId(),
                principal.getCredentialVersion(),
                postId,
                request.reasonType(),
                request.reasonDetail()
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PostModerationResponse.from(result));
    }

    private void requirePrincipal(MemberPrincipal principal) {
        if(principal == null) {
            throw new ContentModerationException(
                    ContentModerationException.Code.AUTHENTICATION_REQUIRED
            );
        }
    }
}
