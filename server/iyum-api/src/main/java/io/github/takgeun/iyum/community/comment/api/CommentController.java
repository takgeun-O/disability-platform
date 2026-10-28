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
import jakarta.validation.constraints.Null;
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
                    
                    sort를 생략하면 OLDEST로 조회합니다.
                    OLDEST: 작성 시각 오름차순, ID 오름차순.
                    LATEST: 작성 시각 내림차순, ID 내림차순.
                    POPULAR: 좋아요 수 내림차순, 작성 시각 오름차순, ID 오름차순.
                    
                    첫 조회에서는 모든 after 파라미터를 생략합니다.
                    후속 조회에서는 nextCursor의 createdAt, commentId, sort를
                    각각 afterCreatedAt, afterId, afterSort로 전달합니다.
                    인기순 후속 조회는 likeCount를 afterLikeCount로 함께 전달합니다.
                    
                    기존 등록순 요청은 afterSort를 생략할 수 있습니다.
                    정렬을 변경할 때는 커서를 초기화해야 합니다.
                    인기순은 페이지 요청 사이에 좋아요 변경으로 순위가 달라질 수 있습니다.
                    
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
                    description = "잘못된 조회 개수·정렬·커서 또는 파라미터 형식",
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
                    description = "정렬 종류. 기본값 OLDEST",
                    example = "OLDEST"
            )
            @RequestParam(name = "sort", defaultValue = "OLDEST")
            CommentSort sort,

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

            @Parameter(
                    description = """
                            이전 응답의 nextCursor.sort.
                            등록순의 기존 요청에 한해 생략할 수 있습니다.
                            """,
                    example = "POPULAR"
            )
            @RequestParam(name = "afterSort", required = false)
            @Nullable CommentSort afterSort,

            @Parameter(
                    description = """
                            이전 응답의 nextCursor.likeCount.
                            인기순 후속 조회에서만 전달됩니다.
                            """,
                    example = "5"
            )
            @RequestParam(name = "afterLikeCount", required = false)
            @Nullable Long afterLikeCount,

            @Parameter(description = "조회 개수. 1~50, 기본값 20")
            @RequestParam(name = "size", defaultValue = "20")
            int size
    ) {
        CommentCursor cursor = null;

        // 커서 관련 값 중 하나라도 있으면 후속 조회로 검증한다.
        boolean hasCursorParameter =
                afterCreatedAt != null
                        || afterId != null
                        || afterSort != null
                        || afterLikeCount != null;

        if (hasCursorParameter) {
            // sort와 afterSort가 서로 다른 정보를 의미한다는 것이 핵심.
            // sort : 이번에 어떤 정렬로 조회할 것인가?
            // afterSort : 전달한 커서는 어떤 정렬에서 받은 것인가?
            // 정렬을 바꿨다면 기존 커서를 버리고 첫 페이지부터 조회해야 한다.
            // 같은 댓글 위치라도 정렬 방향에 따라 '그 다음'이 달라지기 떄문.

            // 예를 들어서 댓글이 작성된 순서대로 1 -> 2 -> 3 -> 4 -> 5가 있다고 가정했을 때 (size는 2로 가정)
            // 첫 응답은 [1, 2] 가 되고, 커서는 2번 댓글의 시각과 ID 정보가 들어가게 된다.
            // 기존의 프론트는 등록순만 지원했으므로 다음 페이지 요청에 afterSort 없이 시각과 ID만 보내던 구조였음.
//            sort = OLDEST
//            afterId = 2
//            afterCreatedAt = 2번 댓글 작성 시각
//            afterSort = 생략
            // 이 때 최신순으로 정렬을 바꾸면서 실수로 기존 커서를 그대로 보내면
//            sort = LATEST                       ← 정렬만 변경
//            afterId = 2                         ← 기존 커서 유지
//            afterCreatedAt = 2번 댓글 작성 시각
//            afterSort = 생략
            // 이 때 사용자가 최신순으로 선택했다면 댓글 순서는 5 -> 4 -> 3 -> 2 -> 1이 되고
            // 정상적이라면 [5, 4]를 보여줘야 함에도 불구하고,
            // afterId인 2번을 기준으로 최신순의 다음을 조회하면 [1]을 가져오게 된다.

            // 이러한 실수를 서버가 정상 요청처럼 받아들이지 못하게 아래와 같이 처리한다.
            // 정렬 정보가 없는 커서는 기존 등록순 형식으로 취급한다.
            // 이 때 CommentReadQuery에서 두 정렬의 일치 여부를 검사하면 거절할 수 있다.

            // 정리하자면 정상적인 최신순 이용 흐름은 다음과 같다.
            // 1. 최신순으로 변경: sort=LATEST만 보내고 커서 관련 값은 모두 제거
            // 2. 첫 응답 수신: [5, 4] 와 최신순 커서를 받음
            // 3. 더 보기: 받은 커서의 시각·ID와 afterSort=LATEST를 함께 전달
            CommentSort cursorSort = afterSort == null
                    ? CommentSort.OLDEST    // 여기서 sort로 작성하면 요청 정렬과 커서 정렬의 불일치를 발견할 수 없게 됨.
                    : afterSort;

            cursor = new CommentCursor(
                    afterCreatedAt,
                    afterId,
                    cursorSort,
                    afterLikeCount
            );
        }

        CommentReadQuery query = new CommentReadQuery(
                sort,
                cursor,
                size
        );

        Long viewerId = principal == null
                ? null
                : principal.getMemberId();

        CommentListResult result = commentReadService.getComments(
                postId,
                query,
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
        if (principal == null) {
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
        if (principal == null) {
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
