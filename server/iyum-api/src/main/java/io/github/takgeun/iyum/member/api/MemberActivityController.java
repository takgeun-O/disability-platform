package io.github.takgeun.iyum.member.api;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.community.post.api.dto.PostFeedResponse;
import io.github.takgeun.iyum.community.post.application.PostFeedResult;
import io.github.takgeun.iyum.global.error.ApiErrorResponse;
import io.github.takgeun.iyum.member.api.dto.MemberActivitySummaryResponse;
import io.github.takgeun.iyum.member.api.dto.MemberCommentListResponse;
import io.github.takgeun.iyum.member.application.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

// 게시글·댓글 수 등 활동 정보 조회 API를 따로 마련한 이유
// 성능상 이득을 보려는 게 목적이 아니라, 나중에 다른 곳에서 활동 관련 데이터 조회가 필요할 경우
// 재활용하기 위함.
@RestController
@RequiredArgsConstructor
@Tag(name = "회원")
public class MemberActivityController {

    private final MemberActivitySummaryService memberActivitySummaryService;
    private final MemberPostReadService memberPostReadService;
    private final MemberCommentReadService memberCommentReadService;

    @Operation(
            summary = "내 활동 요약 조회",
            description = """
                    현재 로그인한 회원이 작성한 게시글 수와 댓글 수를 조회합니다.
                    삭제된 게시글과 댓글은 집계에서 제외합니다.
                    삭제된 게시글에 달린 댓글도 제외합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation =
                                            MemberActivitySummaryResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인이 필요하거나 회원을 확인할 수 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "활성 상태가 아닌 회원",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            )
    })
    @GetMapping(
            value = "/api/v1/members/me/activity-summary",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<MemberActivitySummaryResponse> getSummary(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        Long memberId = principal == null
                ? null
                : principal.getMemberId();

        // principal.getMemberId()만 서비스에 전달하고 회원의 현재 활성 상태는 서비스가 DB에서 확인
        MemberActivitySummaryResult result =
                memberActivitySummaryService.getSummary(memberId);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())   // 개인별 응답이니 기존 프로필 조회처럼 no-store 적용
                .body(MemberActivitySummaryResponse.from(result));
    }

    // 내 게시글 조회 메서드 추가
    @Operation(
            summary = "내 게시글 커서 조회",
            description = """
                현재 로그인한 회원이 작성한 게시글을 최신순으로 조회합니다.
                삭제된 게시글은 제외하며 모든 카테고리와 글 유형을 포함합니다.
                첫 조회에서는 커서를 생략합니다.
                다음 조회에서는 응답의 nextCursor.createdAt과
                nextCursor.postId를 각각 afterCreatedAt과 afterId로 전달합니다.
                """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = PostFeedResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "조회 개수 또는 커서가 올바르지 않음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인이 필요하거나 회원을 확인할 수 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "활성 상태가 아닌 회원",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            )
    })
    @GetMapping(
            value = "/api/v1/members/me/posts",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<PostFeedResponse> getPosts(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal,

            @Parameter(
                    description = "이전 응답의 nextCursor.createdAt",
                    example = "2026-10-19T10:00:00.123456Z"
            )
            @RequestParam(
                    name = "afterCreatedAt",
                    required = false
            )
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant afterCreatedAt,

            @Parameter(
                    description = "이전 응답의 nextCursor.postId",
                    example = "104"
            )
            @RequestParam(
                    name = "afterId",
                    required = false
            )
            Long afterId,

            @Parameter(
                    description = "조회 개수, 기본 10개, 최소 1개, 최대 50개",
                    example = "10"
            )
            @RequestParam(
                    name = "size",
                    defaultValue = "10"
            )
            int size
    ) {
        // 회원 ID는 요청 파라미터로 받지 않고 인증 정보의 getMemberId 사용
        Long memberId = principal == null
                ? null
                : principal.getMemberId();

        PostFeedResult result = memberPostReadService.getPosts(
                memberId,
                afterCreatedAt,
                afterId,
                size
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PostFeedResponse.from(result));
    }

    @Operation(
            summary = "내 댓글 커서 조회",
            description = """
                현재 로그인한 회원이 작성한 댓글을 최신순으로 조회합니다.
                삭제된 댓글과 삭제된 게시글에 달린 댓글은 제외합니다.
                댓글마다 원본 게시글 정보를 함께 반환합니다.
                첫 조회에서는 커서를 생략합니다.
                다음 조회에서는 응답의 nextCursor.createdAt과
                nextCursor.commentId를 각각 afterCreatedAt과 afterId로 전달합니다.
                """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = MemberCommentListResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "조회 개수 또는 커서가 올바르지 않음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인이 필요하거나 회원을 확인할 수 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "활성 상태가 아닌 회원",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            )
    })
    @GetMapping(
            value = "/api/v1/members/me/comments",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<MemberCommentListResponse> getComments(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal,

            @Parameter(
                    description = "이전 응답의 nextCursor.createdAt",
                    example = "2026-10-19T10:00:00.123456Z"
            )
            @RequestParam(
                    name = "afterCreatedAt",
                    required = false
            )
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant afterCreatedAt,

            @Parameter(
                    description = "이전 응답의 nextCursor.commentId",
                    example = "104"
            )
            @RequestParam(
                    name = "afterId",
                    required = false
            )
            Long afterId,

            @Parameter(
                    description = "조회 개수. 기본 10개, 최소 1개, 최대 50개",
                    example = "10"
            )
            @RequestParam(
                    name = "size",
                    defaultValue = "10"
            )
            int size
    ) {
        Long memberId = principal == null
                ? null
                : principal.getMemberId();

        MemberCommentListResult result = memberCommentReadService.getComments(
                memberId,
                afterCreatedAt,
                afterId,
                size
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(MemberCommentListResponse.from(result));
    }
}
