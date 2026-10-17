package io.github.takgeun.iyum.community.comment.api;

import io.github.takgeun.iyum.community.comment.api.dto.CommentListResponse;
import io.github.takgeun.iyum.community.comment.application.CommentReadService;
import io.github.takgeun.iyum.global.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@Tag(name = "커뮤니티 댓글")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/community/posts/{postId}/comments")
public class CommentController {

    private final CommentReadService commentReadService;

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
    public CommentListResponse getComments(
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
        return CommentListResponse.from(
                commentReadService.getComments(
                        postId,
                        afterCreatedAt,
                        afterId,
                        size
                )
        );
    }
}
