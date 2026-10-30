package io.github.takgeun.iyum.admin.moderation.api;

import io.github.takgeun.iyum.admin.moderation.api.dto.CommentModerationResponse;
import io.github.takgeun.iyum.admin.moderation.api.dto.ContentModerationRequest;
import io.github.takgeun.iyum.admin.moderation.application.CommentModerationResult;
import io.github.takgeun.iyum.admin.moderation.application.CommentModerationService;
import io.github.takgeun.iyum.admin.moderation.application.ContentModerationException;
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

@Tag(name = "관리자 댓글 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/posts/{postId}/comments")
public class AdminCommentModerationController {

    private final CommentModerationService commentModerationService;

    @Operation(
            summary = "댓글 숨김",
            description = """
                    ACTIVE 관리자만 사용할 수 있습니다.
                    처리 사유와 유효한 로그인 세션·CSRF 토큰이 필요합니다.
                    
                    부모 게시글이 숨김 상태여도 처리할 수 있습니다.
                    부모 게시글 또는 댓글이 삭제된 경우에는 처리할 수 없습니다.
                    
                    이미 댓글이 숨김 상태이면 changed=false를 반환하며
                    처리 이력을 추가하지 않습니다.
                    """
    )
    @PostMapping(
            value = "/{commentId}/hide",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CommentModerationResponse> hide(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal,

            @PathVariable("postId") Long postId,
            @PathVariable("commentId") Long commentId,

            @RequestBody ContentModerationRequest request
    ) {
        requirePrincipal(principal);

        CommentModerationResult result = commentModerationService.hide(
                principal.getMemberId(),
                principal.getCredentialVersion(),
                postId,
                commentId,
                request.reason()
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CommentModerationResponse.from(result));
    }

    @Operation(
            summary = "댓글 복원",
            description = """
                    ACTIVE 관리자만 사용할 수 있습니다.
                    처리 사유와 유효한 로그인 세션·CSRF 토큰이 필요합니다.
                    
                    댓글 자체의 숨김 상태만 해제합니다.
                    부모 게시글이 숨김 상태라면 댓글을 복원해도
                    공개 화면에서는 조회할 수 없습니다.
                    
                    부모 게시글 또는 댓글이 삭제된 경우에는 처리할 수 없습니다.
                    
                    이미 댓글의 숨김이 해제된 상태이면 changed=false를 반환하며
                    처리 이력을 추가하지 않습니다.
                    """
    )
    @PostMapping(
            value = "/{commentId}/restore",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CommentModerationResponse> restore(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal,

            @PathVariable("postId") Long postId,
            @PathVariable("commentId") Long commentId,

            @RequestBody ContentModerationRequest request
    ) {
        requirePrincipal(principal);

        CommentModerationResult result = commentModerationService.restore(
                principal.getMemberId(),
                principal.getCredentialVersion(),
                postId,
                commentId,
                request.reason()
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CommentModerationResponse.from(result));
    }

    private void requirePrincipal(MemberPrincipal principal) {
        if (principal == null) {
            throw new ContentModerationException(
                    ContentModerationException.Code.AUTHENTICATION_REQUIRED
            );
        }
    }
}
