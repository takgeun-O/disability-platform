package io.github.takgeun.iyum.community.report.api;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.community.report.api.dto.ContentReportCreateRequest;
import io.github.takgeun.iyum.community.report.api.dto.ContentReportCreateResponse;
import io.github.takgeun.iyum.community.report.application.ContentReportCreateException;
import io.github.takgeun.iyum.community.report.application.ContentReportCreateResult;
import io.github.takgeun.iyum.community.report.application.ContentReportCreateService;
import io.github.takgeun.iyum.global.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "커뮤니티 신고")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/community/posts")
public class ContentReportController {

    private final ContentReportCreateService contentReportCreateService;

    @Operation(
            summary = "게시글 신고 접수",
            description = """
                    로그인한 ACTIVE 회원이 다른 회원의 공개 게시글을 신고합니다.
                    관리자에게도 동일한 정책이 적용됩니다.
                    로그인 세션 쿠키와 로그인 후 새로 발급받은 동일 세션의 CSRF 토큰이 필요합니다.
                    신고자 ID·credentialVersion은 세션에서, 제목·본문 스냅샷은 서버 조회에서 가져옵니다.
                    신고 취소·사유 수정 API는 제공하지 않습니다.
                    CSRF 검사가 먼저 실패하면 비로그인·오래된 세션도 401 대신 403일 수 있습니다.
                    CORS 거부 응답은 공통 JSON 형식을 보장하지 않습니다.

                    자신의 게시글은 신고할 수 없습니다.
                    존재하지 않거나 숨김·삭제된 게시글은 신고할 수 없습니다.
                    동일 회원은 동일 게시글을 한 번만 신고할 수 있으며,
                    기존 신고가 처리 완료 또는 기각되어도 다시 신고할 수 없습니다.

                    신고 접수만으로 게시글이 숨겨지거나 작성자가 제재되지는 않습니다.
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ContentReportCreateRequest.class),
                            examples = {
                                    @ExampleObject(name = "general", summary = "일반 사유: 상세 생략 가능", value = """
                                            {
                                              "reasonType": "SPAM_ADVERTISING"
                                            }
                                            """),
                                    @ExampleObject(name = "other", summary = "기타 사유: 상세 필수", value = """
                                            {
                                              "reasonType": "OTHER",
                                              "reasonDetail": "게시판 이용 목적과 맞지 않는 내용을 반복해서 게시합니다."
                                            }
                                            """)
                            }
                    )
            ),
            parameters = @Parameter(
                    name = "X-CSRF-TOKEN",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "로그인 후 GET /api/v1/auth/csrf로 다시 발급받은 토큰. 동일 로그인 세션 쿠키와 함께 전달합니다.",
                    schema = @Schema(type = "string")
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201", description = "신고 접수. 신고자·스냅샷을 반환하지 않으며 Location은 제공하지 않습니다.",
                    headers = @Header(name = "Cache-Control", schema = @Schema(type = "string", example = "no-store")),
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ContentReportCreateResponse.class),
                            examples = @ExampleObject(name = "received", value = """
                                    {
                                      "reportId": 100,
                                      "status": "RECEIVED",
                                      "createdAt": "2026-11-12T10:00:00Z"
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "400", description = "파싱·경로 변환 실패와 서비스 사유 검증 실패를 구분합니다.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "INVALID_REASON", value = """
                                            {
                                              "code": "INVALID_REASON",
                                              "message": "신고 사유를 확인해 주세요. 상세 설명은 500자 이하여야 하며, 기타 사유는 상세 설명이 필요합니다.",
                                              "fieldErrors": []
                                            }
                                            """),
                                    @ExampleObject(name = "INVALID_REQUEST_BODY", value = """
                                            {
                                              "code": "INVALID_REQUEST_BODY",
                                              "message": "요청 본문의 JSON 형식과 필드 값을 확인해 주세요.",
                                              "fieldErrors": []
                                            }
                                            """),
                                    @ExampleObject(name = "INVALID_REQUEST_PARAMETER", value = """
                                            {
                                              "code": "INVALID_REQUEST_PARAMETER",
                                              "message": "요청 파라미터의 형식과 값을 확인해 주세요.",
                                              "fieldErrors": []
                                            }
                                            """)
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401", description = "유효한 로그인 세션이 필요합니다.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "AUTHENTICATION_REQUIRED", value = """
                                            {
                                              "code": "AUTHENTICATION_REQUIRED",
                                              "message": "유효한 로그인 정보가 필요합니다.",
                                              "fieldErrors": []
                                            }
                                            """)
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403", description = "자기 콘텐츠 신고 또는 보안 검사 거부. CORS 응답 형식은 별도입니다.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "SELF_REPORT_NOT_ALLOWED", value = """
                                            {
                                              "code": "SELF_REPORT_NOT_ALLOWED",
                                              "message": "자신이 작성한 콘텐츠는 신고할 수 없습니다.",
                                              "fieldErrors": []
                                            }
                                            """),
                                    @ExampleObject(name = "CSRF_TOKEN_INVALID", value = """
                                            {
                                              "code": "CSRF_TOKEN_INVALID",
                                              "message": "CSRF 토큰을 확인해 주세요.",
                                              "fieldErrors": []
                                            }
                                            """),
                                    @ExampleObject(name = "ACCESS_DENIED", value = """
                                            {
                                              "code": "ACCESS_DENIED",
                                              "message": "접근 권한이 없습니다.",
                                              "fieldErrors": []
                                            }
                                            """)
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404", description = "공개 대상을 찾을 수 없습니다.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "POST_NOT_FOUND", value = """
                                            {
                                              "code": "POST_NOT_FOUND",
                                              "message": "신고할 수 있는 게시글을 찾을 수 없습니다.",
                                              "fieldErrors": []
                                            }
                                            """)
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "409", description = "기존 신고 상태와 관계없이 재신고할 수 없습니다.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "REPORT_ALREADY_EXISTS", value = """
                                            {
                                              "code": "REPORT_ALREADY_EXISTS",
                                              "message": "이미 신고한 콘텐츠입니다.",
                                              "fieldErrors": []
                                            }
                                            """)
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "415", description = "지원하지 않는 Content-Type 등 MVC 오류: HTTP_ERROR.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "HTTP_ERROR", value = """
                                            {
                                              "code": "HTTP_ERROR",
                                              "message": "요청을 처리할 수 없습니다.",
                                              "fieldErrors": []
                                            }
                                            """)
                            }
                    )
            )
    })
    @PostMapping(
            value = "/{postId}/reports",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ContentReportCreateResponse> reportPost(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal,

            @Parameter(description = "신고할 게시글 ID", example = "104")
            @PathVariable("postId") Long postId,

            @RequestBody ContentReportCreateRequest request
    ) {
        requireAuthenticatedPrincipal(principal);

        ContentReportCreateResult result = contentReportCreateService.reportPost(
                principal.getMemberId(),
                principal.getCredentialVersion(),
                postId,
                request.reasonType(),
                request.reasonDetail()
        );

        return createdResponse(result);
    }

    @Operation(
            summary = "댓글 신고 접수",
            description = """
                    로그인한 ACTIVE 회원이 다른 회원의 공개 댓글을 신고합니다.
                    관리자에게도 동일한 정책이 적용됩니다.
                    로그인 세션 쿠키와 로그인 후 새로 발급받은 동일 세션의 CSRF 토큰이 필요합니다.
                    신고자 ID·credentialVersion은 세션에서, 제목·본문 스냅샷은 서버 조회에서 가져옵니다.
                    신고 취소·사유 수정 API는 제공하지 않습니다.
                    CSRF 검사가 먼저 실패하면 비로그인·오래된 세션도 401 대신 403일 수 있습니다.
                    CORS 거부 응답은 공통 JSON 형식을 보장하지 않습니다.

                    자신의 댓글은 신고할 수 없습니다.
                    자신이 작성한 게시글에 달린 다른 회원의 댓글은 신고할 수 있습니다.

                    부모 게시글과 댓글이 모두 공개 상태여야 하며,
                    댓글은 요청한 게시글에 속해야 합니다.
                    존재하지 않거나 숨김·삭제된 대상은 신고할 수 없습니다.

                    동일 회원은 동일 댓글을 한 번만 신고할 수 있으며,
                    기존 신고가 처리 완료 또는 기각되어도 다시 신고할 수 없습니다.

                    신고 접수만으로 댓글이 숨겨지거나 작성자가 제재되지는 않습니다.
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ContentReportCreateRequest.class),
                            examples = {
                                    @ExampleObject(name = "general", summary = "일반 사유: 상세 생략 가능", value = """
                                            {
                                              "reasonType": "SPAM_ADVERTISING"
                                            }
                                            """),
                                    @ExampleObject(name = "other", summary = "기타 사유: 상세 필수", value = """
                                            {
                                              "reasonType": "OTHER",
                                              "reasonDetail": "게시판 이용 목적과 맞지 않는 내용을 반복해서 게시합니다."
                                            }
                                            """)
                            }
                    )
            ),
            parameters = @Parameter(
                    name = "X-CSRF-TOKEN",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "로그인 후 GET /api/v1/auth/csrf로 다시 발급받은 토큰. 동일 로그인 세션 쿠키와 함께 전달합니다.",
                    schema = @Schema(type = "string")
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201", description = "신고 접수. 신고자·스냅샷을 반환하지 않으며 Location은 제공하지 않습니다.",
                    headers = @Header(name = "Cache-Control", schema = @Schema(type = "string", example = "no-store")),
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ContentReportCreateResponse.class),
                            examples = @ExampleObject(name = "received", value = """
                                    {
                                      "reportId": 100,
                                      "status": "RECEIVED",
                                      "createdAt": "2026-11-12T10:00:00Z"
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "400", description = "파싱·경로 변환 실패와 서비스 사유 검증 실패를 구분합니다.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "INVALID_REASON", value = """
                                            {
                                              "code": "INVALID_REASON",
                                              "message": "신고 사유를 확인해 주세요. 상세 설명은 500자 이하여야 하며, 기타 사유는 상세 설명이 필요합니다.",
                                              "fieldErrors": []
                                            }
                                            """),
                                    @ExampleObject(name = "INVALID_REQUEST_BODY", value = """
                                            {
                                              "code": "INVALID_REQUEST_BODY",
                                              "message": "요청 본문의 JSON 형식과 필드 값을 확인해 주세요.",
                                              "fieldErrors": []
                                            }
                                            """),
                                    @ExampleObject(name = "INVALID_REQUEST_PARAMETER", value = """
                                            {
                                              "code": "INVALID_REQUEST_PARAMETER",
                                              "message": "요청 파라미터의 형식과 값을 확인해 주세요.",
                                              "fieldErrors": []
                                            }
                                            """)
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401", description = "유효한 로그인 세션이 필요합니다.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "AUTHENTICATION_REQUIRED", value = """
                                            {
                                              "code": "AUTHENTICATION_REQUIRED",
                                              "message": "유효한 로그인 정보가 필요합니다.",
                                              "fieldErrors": []
                                            }
                                            """)
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403", description = "자기 콘텐츠 신고 또는 보안 검사 거부. CORS 응답 형식은 별도입니다.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "SELF_REPORT_NOT_ALLOWED", value = """
                                            {
                                              "code": "SELF_REPORT_NOT_ALLOWED",
                                              "message": "자신이 작성한 콘텐츠는 신고할 수 없습니다.",
                                              "fieldErrors": []
                                            }
                                            """),
                                    @ExampleObject(name = "CSRF_TOKEN_INVALID", value = """
                                            {
                                              "code": "CSRF_TOKEN_INVALID",
                                              "message": "CSRF 토큰을 확인해 주세요.",
                                              "fieldErrors": []
                                            }
                                            """),
                                    @ExampleObject(name = "ACCESS_DENIED", value = """
                                            {
                                              "code": "ACCESS_DENIED",
                                              "message": "접근 권한이 없습니다.",
                                              "fieldErrors": []
                                            }
                                            """)
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404", description = "공개 대상을 찾을 수 없습니다. 부모 게시글 검증이 먼저 실패하면 POST_NOT_FOUND입니다.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "POST_NOT_FOUND", value = """
                                            {
                                              "code": "POST_NOT_FOUND",
                                              "message": "신고할 수 있는 게시글을 찾을 수 없습니다.",
                                              "fieldErrors": []
                                            }
                                            """),
                                    @ExampleObject(name = "COMMENT_NOT_FOUND", value = """
                                            {
                                              "code": "COMMENT_NOT_FOUND",
                                              "message": "신고할 수 있는 댓글을 찾을 수 없습니다.",
                                              "fieldErrors": []
                                            }
                                            """)
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "409", description = "기존 신고 상태와 관계없이 재신고할 수 없습니다.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "REPORT_ALREADY_EXISTS", value = """
                                            {
                                              "code": "REPORT_ALREADY_EXISTS",
                                              "message": "이미 신고한 콘텐츠입니다.",
                                              "fieldErrors": []
                                            }
                                            """)
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "415", description = "지원하지 않는 Content-Type 등 MVC 오류: HTTP_ERROR.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "HTTP_ERROR", value = """
                                            {
                                              "code": "HTTP_ERROR",
                                              "message": "요청을 처리할 수 없습니다.",
                                              "fieldErrors": []
                                            }
                                            """)
                            }
                    )
            )
    })
    @PostMapping(
            value = "/{postId}/comments/{commentId}/reports",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ContentReportCreateResponse> reportComment(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal,

            @Parameter(description = "부모 게시글 ID", example = "104")
            @PathVariable("postId") Long postId,

            @Parameter(description = "신고할 댓글 ID", example = "144")
            @PathVariable("commentId") Long commentId,

            @RequestBody ContentReportCreateRequest request
    ) {
        requireAuthenticatedPrincipal(principal);

        ContentReportCreateResult result = contentReportCreateService.reportComment(
                principal.getMemberId(),
                principal.getCredentialVersion(),
                postId,
                commentId,
                request.reasonType(),
                request.reasonDetail()
        );

        return createdResponse(result);
    }

    private ResponseEntity<ContentReportCreateResponse> createdResponse(
            ContentReportCreateResult result
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(ContentReportCreateResponse.from(result));
    }

    private void requireAuthenticatedPrincipal(
            MemberPrincipal principal
    ) {
        if (principal == null) {
            throw new ContentReportCreateException(
                    ContentReportCreateException.Code.AUTHENTICATION_REQUIRED
            );
        }
    }
}
