package io.github.takgeun.iyum.community.post.api;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.community.post.api.dto.*;
import io.github.takgeun.iyum.community.post.application.*;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@Tag(name = "커뮤니티 게시글")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/community/posts")
public class PostController {

    private final PostCreateService postCreateService;
    private final PostReadService postReadService;
    private final PostUpdateService postUpdateService;
    private final PostDeleteService postDeleteService;

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
            value = "/feed",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "게시글 피드 커서 조회",
            description = """
                    로그인 없이 조회할 수 있습니다.
                    생성 시각 내림차순, ID 내림차순으로 정렬합니다.
                    
                    첫 조회에서는 afterCreatedAt과 afterId를 모두 생략합니다.
                    후속 조회에서는 이전 응답의 nextCursor 값을 함께 전달합니다.
                    생성 시각 문자열은 정밀도를 유지하도록 그대로 전달합니다.
                    
                    글 유형과 주제는 후속 조회에서도 동일하게 전달합니다.
                    필터를 변경하면 커서를 생략하고 처음부터 조회합니다.
                    
                    마지막 목록은 hasNext=false, nextCursor=null을 반환합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공. 조회할 게시글이 없으면 빈 목록",
                    content = @Content(
                            schema = @Schema(
                                    implementation = PostFeedResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 조회 개수·커서·필터 또는 파라미터 형식",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            )
    })
    public PostFeedResponse getFeed(
            @Parameter(description = "글 유형. 생략하면 전체 유형")
            @RequestParam(name = "type", required = false)
            @Nullable PostType type,

            @Parameter(description = "주제. 생략하면 전체 주제")
            @RequestParam(name = "topic", required = false)
            @Nullable PostTopic topic,

            @Parameter(
                    description = """
                            이전 응답의 nextCursor.createdAt.
                            반환받은 문자열을 그대로 전달합니다.
                            """,
                    example = "2026-10-18T02:00:00.123456Z"
            )
            @RequestParam(name = "afterCreatedAt", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) // 요청의 날짜 문자열을 Instant로 변환할 때 사용
            @Nullable Instant afterCreatedAt,

            @Parameter(
                    description = "이전 응답의 nextCursor.postId",
                    example = "104"
            )
            @RequestParam(name = "afterId", required = false)
            @Nullable Long afterId,

            @Parameter(description = "조회 개수. 1~50, 기본값 20")
            @RequestParam(name = "size", defaultValue = "20")
            int size
    ) {
        return PostFeedResponse.from(
                postReadService.getFeed(
                        type,
                        topic,
                        afterCreatedAt,
                        afterId,
                        size
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

    // PUT 방식 : 대상의 상태를 전달한 내용으로 교체 (PATCH는 대상에 일부 변경을 적용하는 것)
    // PATCH로 하면 정상 작동하게는 만들 수 있으나, 만약 요청에 title만 들어온다면 나머지 필드는 null로 들어올 수 있음.
    // @NotNull이나 @NotBlank 검증 등 필수값 검사에서 거절되는 현상이 발생함. (이걸 처리하는 게 번거로움)
    @PutMapping(
            value = "/{postId}",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "게시글 수정",
            description = """
                    로그인한 작성자만 게시글을 수정할 수 있습니다.
                    DB의 현재 회원 상태가 ACTIVE인 경우에만 허용합니다.
                    유형·주제·제목·본문을 모두 전달해야 합니다.
                    존재하지 않거나 삭제된 게시글은 404를 반환합니다.
                    성공하면 응답 본문 없이 204를 반환합니다.
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
                    description = "게시글 수정 완료",
                    content = @Content
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
                    description = "CSRF 오류, 활동 불가 상태 또는 작성자 불일치",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
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
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "처리하지 못한 서버 오류",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            )
    })
    public ResponseEntity<Void> update( // Void : 응답 본문이 없다는 의미 (204 응답 반환할 것)
                                        @Parameter(hidden = true)
                                        @AuthenticationPrincipal MemberPrincipal principal,

                                        @Parameter(description = "수정할 게시글 ID", example = "123")
                                        @PathVariable("postId") Long postId,

                                        // @Valid로 PostUpdateRequest 검증 요청 -> DTO에 붙은 @ValidPostUpdateRequest를 통해 검증기 실행
                                        @Valid @RequestBody PostUpdateRequest request
    ) {

        if (principal == null) {
            throw new PostUpdateException(
                    PostUpdateException.Code.MEMBER_NOT_FOUND
            );
        }

        postUpdateService.update(
                principal.getMemberId(),
                postId,
                request.toCommand()
        );

        return ResponseEntity
                .noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }

    @Operation(
            summary = "게시글 삭제",
            description = """
                    로그인한 작성자가 자신의 게시글을 논리 삭제합니다.
                    DB의 현재 회원 상태가 ACTIVE인 경우에만 허용합니다.
                    존재하지 않거나 이미 삭제된 게시글은 404를 반환합니다.
                    로그인 후 발급받은 CSRF 토큰과 같은 세션 쿠키가 필요합니다.
                    요청 본문은 사용하지 않습니다.
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
                    description = "게시글 삭제 완료",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "게시글 ID 형식 오류",
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
                    description = "CSRF 검증 실패, 삭제 불가 상태 또는 작성자 불일치",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않거나 이미 삭제된 게시글",
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
    // 삭제 메서드는 수정 API와 달리 입력할 제목이나 본문이 없으므로 삭제 요청 DTO나 입력 검증기는 만들지 않아도 된다.
    // 또한 요청 본문이 없는 API이기 때문에 consumes = APPLICATION_JSON_VALUE를 지정하지 않음.
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> delete( // Void : 응답 본문이 없다는 의미 (204 응답 반환할 것)
                                        @Parameter(hidden = true)
                                        @AuthenticationPrincipal MemberPrincipal principal,

                                        @Parameter(description = "게시글 ID", example = "123")
                                        @PathVariable("postId") Long postId
    ) {

        if (principal == null) {
            throw new PostDeleteException(
                    PostDeleteException.Code.MEMBER_NOT_FOUND
            );
        }

        postDeleteService.delete(
                principal.getMemberId(),
                postId
        );

        return ResponseEntity
                .noContent()
                // Cache-Control: no-store
                // 이전에 받은 응답을 보관했다가 재사용하여 서버 요청과 대기 시간을 줄이는 기능
                // 개인정보를 응답에 담거나, 매번 현재 상태를 확인해야 하는 응답의 경우 재사용하는 것은 적절하지 않으니 no-store 사용
                // 다만 백엔드에서의 이 설정이 삭제 후 옛날 글이 화면에 남는 문제를 해결해 주는 설정은 아님.
                // 삭제 후 옛날 글이 화면에 남는 문제는 프론트엔드에서 게시글 목록을 다시 조회하는 식으로 해서 해결해야 함.
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
