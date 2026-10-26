package io.github.takgeun.iyum.community.comment.api;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.community.comment.api.dto.CommentCreateRequest;
import io.github.takgeun.iyum.community.comment.api.dto.CommentCreateResponse;
import io.github.takgeun.iyum.community.comment.api.dto.CommentListResponse;
import io.github.takgeun.iyum.community.comment.api.dto.CommentUpdateRequest;
import io.github.takgeun.iyum.community.comment.application.*;
import io.github.takgeun.iyum.global.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@Tag(name = "커뮤니티 댓글")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/community/posts/{postId}/comments")
public class CommentController {

    private final CommentReadService commentReadService;
    private final CommentCreateService commentCreateService;
    private final CommentUpdateService commentUpdateService;
    private final CommentDeleteService commentDeleteService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "댓글 목록 조회",
            description = """
                    로그인 없이 조회할 수 있습니다.
                    작성 시각 오름차순, ID 오름차순으로 정렬합니다.
                    첫 조회에서는 afterCreatedAt과 afterId를 모두 생략합니다.
                    후속 조회에서는 이전 응답의 nextCursor에 담긴
                    createdAt과 commentId를 각각 전달합니다.
                    존재하지 않거나 삭제된 게시글은 404를 반환합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            schema = @Schema(
                                    implementation = CommentListResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 조회 개수·커서 또는 파라미터 형식",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않거나 삭제된 게시글",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            )
    })
    public ResponseEntity<CommentListResponse> getComments(
            @Parameter(hidden = true)
            @AuthenticationPrincipal
            @Nullable MemberPrincipal principal,

            @Parameter(description = "게시글 ID", example = "123")
            @PathVariable("postId")
            Long postId,

            @Parameter(
                    description = """
                            이전 응답의 nextCursor.createdAt.
                            서버가 반환한 문자열을 그대로 전달합니다.
                            """,
                    example = "2026-10-17T03:00:00.123456Z"
            )
            @RequestParam(name = "afterCreatedAt", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @Nullable Instant afterCreatedAt,

            @Parameter(
                    description = "이전 응답의 nextCursor.commentId",
                    example = "100"
            )
            @RequestParam(name = "afterId", required = false)
            @Nullable Long afterId,

            @Parameter(description = "조회 개수. 1~50, 기본값 20")
            @RequestParam(name = "size", defaultValue = "20")
            int size
    ) {
        Long viewerId =
                principal == null ? null : principal.getMemberId();

        CommentListResult result = commentReadService.getComments(
                postId,
                afterCreatedAt,
                afterId,
                size,
                viewerId
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CommentListResponse.from(result));
    }

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "댓글 작성",
            description = """
                    로그인한 회원이 게시글에 댓글을 작성합니다.
                    DB의 현재 회원 상태가 ACTIVE인 경우에만 허용합니다.
                    작성자는 로그인 세션에서 결정합니다.
                    존재하지 않거나 삭제된 게시글에는 작성할 수 없습니다.
                    로그인 후 발급받은 CSRF 토큰과 같은 세션 쿠키가 필요합니다.
                    """,
            parameters = @Parameter(
                    name = "X-CSRF-TOKEN",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "로그인 후 GET /api/v1/auth/csrf로 받은 토큰",
                    schema = @Schema(type = "string")
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "댓글 작성 완료",
                    content = @Content(
                            schema = @Schema(
                                    implementation = CommentCreateResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력 검증 실패 또는 요청 형식 오류",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인 필요 또는 회원 정보 확인 불가",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "CSRF 검증 실패 또는 댓글 작성 불가 상태",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않거나 삭제된 게시글",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            )
    })
    public ResponseEntity<CommentCreateResponse> create(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal,

            @Parameter(description = "게시글 ID", example = "123")
            @PathVariable("postId") Long postId,

            // 여기서 본문 검증에 실패 시 컨트롤러 메서드 본문을 실행하기 전에 오류가 발생하고
            // 기존 handlerMethodArgumentNotValid()가 400 / VALIDATION_FAILED 응답을 만든다.
            @Valid @RequestBody CommentCreateRequest request
    ) {
        if (principal == null) {
            throw new CommentCreateException(
                    CommentCreateException.Code.AUTHOR_NOT_FOUND
            );
        }

        CommentCreateResult result = commentCreateService.create(
                principal.getMemberId(),
                postId,
                request.toCommand()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(CommentCreateResponse.from(result));
    }

    @PutMapping(
            value = "/{commentId}",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "댓글 수정",
            description = """
                    로그인한 회원이 본인이 작성한 댓글을 수정합니다.
                    DB의 현재 회원 상태가 ACTIVE인 경우에만 허용합니다.
                    게시글 작성자라도 다른 회원의 댓글을 수정할 수 없습니다.
                    존재하지 않거나 삭제된 게시글·댓글은 수정할 수 없습니다.
                    동일한 본문으로 요청해도 정상 처리하며 수정 시각은 유지됩니다.
                    성공하면 응답 본문 없이 204를 반환합니다.
                    로그인 후 발급받은 CSRF 토큰과 같은 세션 쿠키가 필요합니다.
                    """,
            parameters = @Parameter(
                    name = "X-CSRF-TOKEN",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "로그인 후 GET /api/v1/auth/csrf로 받은 토큰",
                    schema = @Schema(type = "string")
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "댓글 수정 완료. 응답 본문 없음",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력 검증 실패 또는 요청 형식 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인 필요 또는 회원 정보 확인 불가",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "CSRF 검증 실패, 수정 불가 회원 상태 또는 작성자 불일치",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글·댓글이 없거나 삭제됨 또는 게시글과 댓글 불일치",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            )
    })
    public ResponseEntity<Void> update(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal,

            @Parameter(description = "게시글 ID", example = "123")
            @PathVariable("postId") Long postId,

            @Parameter(description = "댓글 ID", example = "10")
            @PathVariable("commentId") Long commentId,

            @Valid @RequestBody CommentUpdateRequest request
    ) {
        if(principal == null) {
            throw new CommentUpdateException(
                    CommentUpdateException.Code.MEMBER_NOT_FOUND
            );
        }

        commentUpdateService.update(
                principal.getMemberId(),
                postId,
                commentId,
                request.toCommand()
        );

        return ResponseEntity
                .noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }

    @DeleteMapping("/{commentId}")
    @Operation(
            summary = "댓글 삭제",
            description = """
                로그인한 회원이 본인이 작성한 댓글을 삭제합니다.
                DB의 현재 회원 상태가 ACTIVE인 경우에만 허용합니다.
                게시글 작성자라도 다른 회원의 댓글을 삭제할 수 없습니다.
                DB 행은 유지하고 삭제 시각을 기록하며, 댓글 목록에서 제외합니다.
                이미 삭제된 댓글에 다시 요청하면 404를 반환합니다.
                요청 본문은 필요하지 않습니다.
                성공하면 응답 본문 없이 204를 반환합니다.
                로그인 후 발급받은 CSRF 토큰과 같은 세션 쿠키가 필요합니다.
                """,
            parameters = @Parameter(
                    name = "X-CSRF-TOKEN",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "로그인 후 GET /api/v1/auth/csrf로 받은 토큰",
                    schema = @Schema(type = "string")
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "댓글 삭제 완료. 응답 본문 없음",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "게시글 ID 또는 댓글 ID 형식 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인 필요 또는 회원 정보 확인 불가",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "CSRF 검증 실패, 삭제 불가 회원 상태 또는 작성자 불일치",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글·댓글이 없거나 삭제됨 또는 게시글과 댓글 불일치",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            )
    })
    public ResponseEntity<Void> delete(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal,

            @Parameter(description = "게시글 ID", example = "123")
            @PathVariable("postId") Long postId,

            @Parameter(description = "댓글 ID", example = "10")
            @PathVariable("commentId") Long commentId
    ) {
        if(principal == null) {
            throw new CommentDeleteException(
                    CommentDeleteException.Code.MEMBER_NOT_FOUND
            );
        }

        commentDeleteService.delete(
                principal.getMemberId(),
                postId,
                commentId
        );

        return ResponseEntity
                .noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
