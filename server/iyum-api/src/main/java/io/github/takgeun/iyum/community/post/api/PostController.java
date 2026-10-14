package io.github.takgeun.iyum.community.post.api;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.community.post.api.dto.PostCreateRequest;
import io.github.takgeun.iyum.community.post.api.dto.PostCreateResponse;
import io.github.takgeun.iyum.community.post.api.dto.PostDetailResponse;
import io.github.takgeun.iyum.community.post.api.dto.PostListResponse;
import io.github.takgeun.iyum.community.post.application.PostCreateException;
import io.github.takgeun.iyum.community.post.application.PostCreateResult;
import io.github.takgeun.iyum.community.post.application.PostCreateService;
import io.github.takgeun.iyum.community.post.application.PostReadService;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "커뮤니티 게시글")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/community/posts")
public class PostController {

    private final PostCreateService postCreateService;
    private final PostReadService postReadService;

    @Operation(
            summary = "게시글 작성",
            description = """
                    로그인한 회원이 게시글을 작성합니다.
                    DB의 현재 회원 상태가 ACTIVE인 경우에만 허용합니다.
                    작성자는 로그인 세션에서 결정합니다.
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
                    description = "게시글 작성 완료",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = PostCreateResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력 검증 실패 또는 잘못된 JSON·열거형 값",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인 필요 또는 현재 회원 정보 확인 불가",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "CSRF 검증 실패 또는 게시글 작성 불가 상태",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "처리하지 못한 서버 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            )
    })
    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<PostCreateResponse> create(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal, // 로그인한 회원 가져오기 (Spring Security가 현재 인증 정보의 principal을 꺼내 전달해줌
            @Valid @RequestBody PostCreateRequest request   // JSON 변환과 입력 검증
    ) {
        if (principal == null) {
            throw new PostCreateException(
                    PostCreateException.Code.AUTHOR_NOT_FOUND
            );
        }

        // 서비스 호출과 응답 변환
        PostCreateResult result = postCreateService.create(
                principal.getMemberId(),
                request.toCommand()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(PostCreateResponse.from(result));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "게시글 목록 조회",
            description = """
                    로그인 없이 조회할 수 있습니다.
                    글 유형과 주제를 생략하면 전체를 조회합니다.
                    작성 일시 내림차순, ID 내림차순으로 정렬합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            schema = @Schema(
                                    implementation = PostListResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 조회 조건",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            )
    })
    // 참고로 이 조회 메서드는 기본 200 응답이면 충분하므로 직접 DTO를 반환해도 된다.
    // Spring이 반환한 PostDetailResponse를 JSON으로 변환해서 응답 본문에 넣어줌.
    // 참고로 ResponseEntity는 상태 코드나 헤더를 직접 설정할 떄 유용함.
    // 게시글 작성 메서드는 기본값인 200 대신 201 Created를 반환하기 위해서 ResponseEntity를 사용한 것.
    public PostListResponse getPosts(
            @Parameter(description = "글 유형. 생략하면 전체 유형")
            @RequestParam(name = "type", required = false)
            @Nullable PostType type,

            @Parameter(description = "주제. 생략하면 전체 주제")
            @RequestParam(name = "topic", required = false)
            @Nullable PostTopic topic,

            @Parameter(description = "페이지 번호. 0부터 시작")
            @RequestParam(name = "page", defaultValue = "0")
            int page,

            @Parameter(description = "페이지 크기. 1~50")
            @RequestParam(name = "size", defaultValue = "20")
            int size
    ) {
        return PostListResponse.from(
                postReadService.getPosts(
                        type, topic, page, size
                )
        );
    }

    @GetMapping(
            value = "/{postId}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "게시글 상세 조회",
            description = """
                    로그인 없이 조회할 수 있습니다.
                    존재하지 않거나 삭제된 게시글은 404를 반환합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            schema = @Schema(
                                    implementation = PostDetailResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글 없음",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            )
    })
    public PostDetailResponse getPost(
            @Parameter(description = "게시글 ID", example = "123")
            @PathVariable("postId") Long postId
    ) {
        return PostDetailResponse.from(
                postReadService.getPost(postId)
        );
    }
}
