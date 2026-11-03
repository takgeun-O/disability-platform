package io.github.takgeun.iyum.admin.content.api;

import io.github.takgeun.iyum.admin.content.api.dto.AdminPostDetailResponse;
import io.github.takgeun.iyum.admin.content.api.dto.AdminPostListResponse;
import io.github.takgeun.iyum.admin.content.application.*;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.global.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(
        value = "/api/v1/admin/posts",
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Tag(name = "관리자 게시글 조회")
@ApiResponses({
        @ApiResponse(
                responseCode = "400",
                description = "조회 조건 또는 요청 파라미터가 올바르지 않음",
                content = @Content(
                        schema = @Schema(
                                implementation = ApiErrorResponse.class
                        )
                )
        ),
        @ApiResponse(
                responseCode = "401",
                description = "유효한 관리자 로그인이 필요함",
                content = @Content(
                        schema = @Schema(
                                implementation = ApiErrorResponse.class
                        )
                )
        ),
        @ApiResponse(
                responseCode = "403",
                description = "관리자 권한이 없음",
                content = @Content(
                        schema = @Schema(
                                implementation = ApiErrorResponse.class
                        )
                )
        )
})
public class AdminPostReadController {

    private final AdminPostReadService adminPostReadService;

    @Operation(
            summary = "관리자 게시글 목록 조회",
            description = """
                    상태와 제목 검색어로 게시글을 조회합니다.
                    상태를 생략하면 공개·숨김·삭제 게시글을 모두 포함합니다.
                    작성 시각 내림차순, 같은 시각이면 ID 내림차순입니다.
                    페이지 번호는 0부터 시작합니다.
                    """
    )
    @GetMapping
    public ResponseEntity<AdminPostListResponse> getPosts(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal,

            @Parameter(description = "조회할 게시글 상태")
            @RequestParam(name = "status", defaultValue = "ALL")
            AdminPostStatus status,

            @Parameter(description = "제목 검색어, 앞뒤 공백 제거 후 최대 100자")
            @RequestParam(name = "keyword", required = false)
            String keyword,

            @Parameter(description = "페이지 번호, 0부터 시작")
            @RequestParam(name = "page", defaultValue = "0")
            int page,

            @Parameter(description = "페이지 크기, 기본 20, 최소 1, 최대 50")
            @RequestParam(name = "size", defaultValue = "20")
            int size
    ) {
        // 인증 객체가 없는 경우를 처리하는 방어 코드
        // 실제 관리자 여부는 기존 Security 필터와 조회 서비스에서 검증
        // GET 조회이므로 CSRF 토큰을 요구하지 않고 기존 로그인 세션 쿠키 사용
        requirePrincipal(principal);

        AdminPostReadQuery query = new AdminPostReadQuery(
                status,
                keyword,
                page,
                size
        );

        // 요청자가 전달한 회원 ID가 아닌 로그인 인증 정보를 사용
        Page<AdminPostItemResult> result =
                adminPostReadService.getPosts(
                        principal.getMemberId(),
                        principal.getCredentialVersion(),
                        query
                );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AdminPostListResponse.from(result));
    }

    @Operation(
            summary = "관리자 게시글 상세 조회",
            description = """
                    공개·숨김·삭제 상태와 관계없이 게시글 원문을 조회합니다.
                    삭제된 게시글을 조회할 수 있어도 숨김·복원은 허용되지 않습니다.
                    """
    )
    @ApiResponse(
            responseCode = "404",
            description = "게시글을 찾을 수 없음",
            content = @Content(
                    schema = @Schema(
                            implementation = ApiErrorResponse.class
                    )
            )
    )
    @GetMapping("/{postId}")
    public ResponseEntity<AdminPostDetailResponse> getPost(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal,

            @Parameter(description = "게시글 ID")
            @PathVariable("postId") Long postId
    ) {
        // 인증 객체가 없는 경우를 처리하는 방어 코드
        // 실제 관리자 여부는 기존 Security 필터와 조회 서비스에서 검증
        // GET 조회이므로 CSRF 토큰을 요구하지 않고 기존 로그인 세션 쿠키 사용
        requirePrincipal(principal);

        AdminPostDetailResult result = adminPostReadService.getPost(
                principal.getMemberId(),
                principal.getCredentialVersion(),
                postId
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AdminPostDetailResponse.from(result));
    }

    private void requirePrincipal(MemberPrincipal principal) {
        if (principal == null) {
            throw new AdminContentReadException(
                    AdminContentReadException.Code.AUTHENTICATION_REQUIRED
            );
        }
    }
}
