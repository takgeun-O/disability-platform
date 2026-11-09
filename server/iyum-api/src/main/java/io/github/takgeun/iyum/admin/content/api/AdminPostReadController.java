package io.github.takgeun.iyum.admin.content.api;

import io.github.takgeun.iyum.admin.content.api.dto.AdminPostDetailResponse;
import io.github.takgeun.iyum.admin.content.api.dto.AdminPostListResponse;
import io.github.takgeun.iyum.admin.content.application.*;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.global.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.Explode;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.enums.ParameterStyle;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

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
                상태, 제목 검색어, 한국 날짜 기준 작성 기간으로 게시글을 조회합니다.
                상태 파라미터를 생략하면 정상·숨김·삭제를 모두 포함합니다.
                기존 단일 선택은 status, 다중 선택은 statuses를 사용합니다.
                다중 선택 예: statuses=VISIBLE&statuses=HIDDEN
                status와 statuses를 동시에 전달할 수 없습니다.
                statuses에는 VISIBLE, HIDDEN, DELETED만 허용합니다.
                선택한 상태끼리는 OR, 나머지 검색 조건과는 AND로 결합합니다.
                시작일과 종료일은 각각 생략할 수 있으며 종료일 전체를 포함합니다.
                날짜 범위는 0001-01-01부터 9999-12-31까지입니다.
                시작일은 종료일보다 늦을 수 없습니다.
                정렬을 생략하거나 빈 문자열로 전달하면 LATEST를 적용합니다.
                LATEST는 작성 시각 내림차순, 같은 시각이면 ID 내림차순입니다.
                OLDEST는 작성 시각 오름차순, 같은 시각이면 ID 오름차순입니다.
                전체 검색 결과를 정렬한 뒤 페이지를 적용합니다.
                페이지 번호는 0부터 시작합니다.
                """
    )
    @Parameters({
            @Parameter(
                    name = "status",
                    in = ParameterIn.QUERY,
                    description = """
                        기존 단일 상태 필터.
                        생략하거나 빈 문자열이면 전체 조회.
                        statuses와 동시에 전달할 수 없습니다.
                        """,
                    schema = @Schema(
                            type = "string",
                            allowableValues = {
                                    "ALL", "VISIBLE", "HIDDEN", "DELETED"
                            }
                    )
            ),
            @Parameter(
                    name = "statuses",
                    in = ParameterIn.QUERY,
                    description = """
                        다중 상태 필터.
                        예: statuses=VISIBLE&statuses=HIDDEN
                        빈 값과 ALL은 허용하지 않습니다.
                        status와 동시에 전달할 수 없습니다.
                        """,
                    style = ParameterStyle.FORM,
                    explode = Explode.TRUE,
                    array = @ArraySchema(
                            schema = @Schema(
                                    type = "string",
                                    allowableValues = {
                                            "VISIBLE", "HIDDEN", "DELETED"
                                    }
                            )
                    )
            )
    })
    @GetMapping
    public ResponseEntity<AdminPostListResponse> getPosts(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal,

            @Parameter(hidden = true)
            @RequestParam MultiValueMap<String, String> parameters,

            @Parameter(description = "제목 검색어, 앞뒤 공백 제거 후 최대 100자")
            @RequestParam(name = "keyword", required = false)
            String keyword,

            @Parameter(
                    description = "작성 시작일, 한국 날짜 기준",
                    example = "2026-11-01"
            )
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @Parameter(
                    description = "작성 종료일, 한국 날짜 기준 해당 날짜 전체 포함",
                    example = "2026-11-07"
            )
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            @Parameter(description = "페이지 번호, 0부터 시작")
            @RequestParam(name = "page", defaultValue = "0")
            int page,

            @Parameter(description = "페이지 크기, 기본 20, 최소 1, 최대 50")
            @RequestParam(name = "size", defaultValue = "20")
            int size,

            @Parameter(
                    description = "작성 시각 정렬, 생략하거나 빈 문자열이면 LATEST",
                    schema = @Schema(
                            allowableValues = {"LATEST", "OLDEST"},
                            defaultValue = "LATEST"
                    )
            )
            @RequestParam(name = "sort", defaultValue = "LATEST")
            AdminPostSort sort
    ) {
        // 인증 객체가 없는 경우를 처리하는 방어 코드
        // 실제 관리자 여부는 기존 Security 필터와 조회 서비스에서 검증
        // GET 조회이므로 CSRF 토큰을 요구하지 않고 기존 로그인 세션 쿠키 사용
        requirePrincipal(principal);

        // 상태 파라미터의 기존 호환 정책은 해석 클래스에서 처리한다.
        // 날짜 범위 검증과 한국 날짜의 Instant 변환은 조회 조건 객체가 담당한다.
        AdminPostReadQuery query = AdminPostReadQuery.ofStatuses(
                AdminContentStatusRequestParser.postStatuses(parameters),   // 해석 클래스에서 분기에 따라 처리
                keyword,
                page,
                size,
                startDate,
                endDate,
                sort
        );

        // 관리자 식별 정보는 요청 파라미터가 아닌 로그인 세션에서 가져온다.
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
