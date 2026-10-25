package io.github.takgeun.iyum.community.post.api;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.community.post.api.dto.PostLikeResponse;
import io.github.takgeun.iyum.community.post.application.PostLikeException;
import io.github.takgeun.iyum.community.post.application.PostLikeResult;
import io.github.takgeun.iyum.community.post.application.PostLikeService;
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

@Tag(name = "커뮤니티 게시글 좋아요")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/community/posts")
public class PostLikeController {

    private final PostLikeService postLikeService;

    @Operation(
            summary = "게시글 좋아요 등록",
            description = """
                    로그인한 ACTIVE 회원이 게시글에 좋아요를 등록합니다.
                    이미 등록했다면 기존 좋아요를 유지합니다.
                    본인 게시글에도 등록할 수 있습니다.
                    요청 본문은 사용하지 않습니다.
                    로그인 세션 쿠키와 CSRF 토큰이 필요합니다.
                    """,
            parameters = @Parameter(
                    name = "X-CSRF-TOKEN",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "GET /api/v1/auth/csrf로 받은 토큰",
                    schema = @Schema(type = "string")
            )
    )
    @PutMapping(
            value = "/{postId}/like",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<PostLikeResponse> like(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal,

            @Parameter(description = "게시글 ID", example = "123")
            @PathVariable("postId") Long postId
    ) {
        Long memberId = requireMemberId(principal);

        PostLikeResult result = postLikeService.like(memberId, postId);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())   // 개인별 좋아요 상태가 포함된 응답 저장 X
                .body(PostLikeResponse.from(result));
    }

    @Operation(
            summary = "게시글 좋아요 취소",
            description = """
                    로그인한 ACTIVE 회원이 자신의 좋아요를 취소합니다.
                    등록한 좋아요가 없어도 취소 상태를 반환합니다.
                    존재하지 않거나 삭제된 게시글은 404를 반환합니다.
                    요청 본문은 사용하지 않습니다.
                    로그인 세션 쿠키와 CSRF 토큰이 필요합니다.
                    """,
            parameters = @Parameter(
                    name = "X-CSRF-TOKEN",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "GET /api/v1/auth/csrf로 받은 토큰",
                    schema = @Schema(type = "string")
            )
    )
    @DeleteMapping(
            value = "/{postId}/like",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<PostLikeResponse> unlike(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal,

            @Parameter(description = "게시글 ID", example = "123")
            @PathVariable("postId") Long postId
    ) {
        Long memberId = requireMemberId(principal);

        PostLikeResult result = postLikeService.unlike(memberId, postId);

        // 취소도 결과 JSON을 반환하므로 204 대신 200 반환
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PostLikeResponse.from(result));
    }

    private Long requireMemberId(MemberPrincipal principal) {
        if(principal == null) {
            throw new PostLikeException(
                    PostLikeException.Code.MEMBER_NOT_FOUND
            );
        }

        return principal.getMemberId();
    }
}
