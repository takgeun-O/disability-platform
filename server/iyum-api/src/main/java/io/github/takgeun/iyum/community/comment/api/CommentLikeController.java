package io.github.takgeun.iyum.community.comment.api;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.community.comment.api.dto.CommentLikeResponse;
import io.github.takgeun.iyum.community.comment.application.CommentLikeException;
import io.github.takgeun.iyum.community.comment.application.CommentLikeResult;
import io.github.takgeun.iyum.community.comment.application.CommentLikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "커뮤니티 댓글 좋아요")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/community/posts/{postId}/comments")
public class CommentLikeController {

    private final CommentLikeService commentLikeService;

    @Operation(
            summary = "댓글 좋아요 등록",
            description = """
                    로그인한 ACTIVE 회원이 댓글에 좋아요를 등록합니다.
                    이미 등록했다면 기존 좋아요를 유지합니다.
                    본인 댓글에도 등록할 수 있습니다.
                    존재하지 않거나 삭제된 게시글·댓글은 404를 반환합니다.
                    댓글이 해당 게시글에 속하지 않는 경우에도 404를 반환합니다.
                    요청 본문은 사용하지 않습니다.
                    로그인 세션 쿠키와 CSRF 토큰이 필요합니다.
                    """,
            parameters = @Parameter(
                    name = "X-CSRF-TOKEN",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "로그인 후 GET /api/v1/auth/csrf로 받은 토큰",
                    schema = @Schema(type = "string")
            )
    )
    @PutMapping(
            value = "/{commentId}/like",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CommentLikeResponse> like(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal,

            @Parameter(description = "게시글 ID", example = "123")
            @PathVariable("postId") Long postId,

            @Parameter(description = "댓글 ID", example = "456")
            @PathVariable("commentId") Long commentId
    ) {
        Long memberId = requireMemberId(principal);

        CommentLikeResult result = commentLikeService.like(
                memberId,
                postId,
                commentId
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CommentLikeResponse.from(result));
    }

    @Operation(
            summary = "댓글 좋아요 취소",
            description = """
                    로그인한 ACTIVE 회원이 자신의 댓글 좋아요를 취소합니다.
                    등록한 좋아요가 없어도 취소 상태를 반환합니다.
                    존재하지 않거나 삭제된 게시글·댓글은 404를 반환합니다.
                    댓글이 해당 게시글에 속하지 않는 경우에도 404를 반환합니다.
                    요청 본문은 사용하지 않습니다.
                    로그인 세션 쿠키와 CSRF 토큰이 필요합니다.
                    """,
            parameters = @Parameter(
                    name = "X-CSRF-TOKEN",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "로그인 후 GET /api/v1/auth/csrf로 받은 토큰",
                    schema = @Schema(type = "string")
            )
    )
    @DeleteMapping(
            value = "/{commentId}/like",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CommentLikeResponse> unlike(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal,

            @Parameter(description = "게시글 ID", example = "123")
            @PathVariable("postId") Long postId,

            @Parameter(description = "댓글 ID", example = "456")
            @PathVariable("commentId") Long commentId
    ) {
        Long memberId = requireMemberId(principal);

        CommentLikeResult result = commentLikeService.unlike(
                memberId,
                postId,
                commentId
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CommentLikeResponse.from(result));
    }

    private Long requireMemberId(MemberPrincipal principal) {
        if(principal == null) {
            throw new CommentLikeException(
                    CommentLikeException.Code.MEMBER_NOT_FOUND
            );
        }

        return principal.getMemberId();
    }
}
