package io.github.takgeun.iyum.admin.member.api;

import io.github.takgeun.iyum.admin.member.api.dto.AdminMemberDetailResponse;
import io.github.takgeun.iyum.admin.member.api.dto.AdminMemberListResponse;
import io.github.takgeun.iyum.admin.member.application.*;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.global.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.Parameter;
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
        value = "/api/v1/admin/members",
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Tag(name = "관리자 회원 조회", description = "읽기 전용 목록·상세. 회원 상태·역할 변경 기능은 제공하지 않음")
@ApiResponses({
        @ApiResponse(
                responseCode = "401",
                description = "세션 없음·만료 또는 현재 회원/인증 버전/역할 불일치",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ApiErrorResponse.class),
                        examples = {
                                @ExampleObject(
                                        name = "session",
                                        value = """
                                                {
                                                  "code": "AUTHENTICATION_REQUIRED",
                                                  "message": "로그인이 필요합니다.",
                                                  "fieldErrors": []
                                                }
                                                """
                                ),
                                @ExampleObject(
                                        name = "service",
                                        value = """
                                                {
                                                  "code": "AUTHENTICATION_REQUIRED",
                                                  "message": "유효한 로그인 정보가 필요합니다.",
                                                  "fieldErrors": []
                                                }
                                                """
                                )
                        }
                )
        ),
        @ApiResponse(
                responseCode = "403",
                description = "일반 회원의 접근 또는 서비스 관리자 자격 검증 실패. 프론트는 두 코드 모두 처리",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ApiErrorResponse.class),
                        examples = {
                                @ExampleObject(
                                        name = "security",
                                        value = """
                                                {
                                                  "code": "ACCESS_DENIED",
                                                  "message": "접근 권한이 없습니다.",
                                                  "fieldErrors": []
                                                }
                                                """
                                ),
                                @ExampleObject(
                                        name = "service",
                                        value = """
                                                {
                                                  "code": "ADMIN_REQUIRED",
                                                  "message": "관리자 권한이 필요합니다.",
                                                  "fieldErrors": []
                                                }
                                                """
                                )
                        }
                )
        )
})
public class AdminMemberReadController {

    private final AdminMemberReadService adminMemberReadService;

    @Operation(
            summary = "관리자 회원 목록 조회",
            description = """
                    회원 ID, 닉네임 또는 이메일로 회원을 검색합니다.
                    회원 ID와 이메일은 정확 일치, 닉네임은 부분 일치 검색입니다.
                    닉네임 검색은 대소문자를 구분하지 않습니다.
                    검색어는 Java strip()으로 앞뒤 공백을 제거하며 비어 있으면 검색 유형도 무시합니다.
                    닉네임의 %, _, !는 와일드카드가 아닌 일반 문자입니다.
                    ID는 ASCII 숫자 1~19자리, 양의 Long이며 선행 0을 제거합니다.
                    닉네임은 최대 100 유니코드 코드 포인트, 이메일은 소문자 정규화 후 최대 254 UTF-16 코드 단위입니다.
                    이메일 일부도 형식 오류로 거부하지 않지만 부분 검색은 하지 않습니다.
                    검색어가 있으면 검색 유형이 필요합니다.
                    상태와 권한을 생략하거나 빈 문자열이면 전체를 조회합니다. ALL 값은 지원하지 않습니다.
                    검색·상태·권한 조건은 AND로 결합합니다.
                    LATEST는 createdAt DESC, memberId DESC; OLDEST는 두 필드 모두 ASC로 정렬한 뒤 페이지를 적용합니다.
                    모든 회원 상태를 조회하며 WITHDRAWN/DELETED 표시 이름은 탈퇴한 회원입니다. DELETED 이메일은 명시적 null, WITHDRAWN은 저장된 이메일입니다.
                    관리자 로그인 세션이 필요하며 GET 요청에는 CSRF 토큰이 필요하지 않습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공. 결과 없음·범위 밖 페이지도 200. 서버 페이지 정보 유지",
                    headers = @Header(name = "Cache-Control", schema = @Schema(type = "string", example = "no-store")),
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminMemberListResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "members",
                                            value = """
                                                    {
                                                      "items": [
                                                        {
                                                          "memberId": 42,
                                                          "nickname": "이움회원",
                                                          "email": "member@example.com",
                                                          "status": "ACTIVE",
                                                          "role": "USER",
                                                          "createdAt": "2026-11-10T01:00:00Z"
                                                        }
                                                      ],
                                                      "page": 0,
                                                      "size": 20,
                                                      "totalElements": 1,
                                                      "totalPages": 1,
                                                      "hasNext": false,
                                                      "hasPrevious": false
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "empty",
                                            value = """
                                                    {
                                                      "items": [],
                                                      "page": 0,
                                                      "size": 20,
                                                      "totalElements": 0,
                                                      "totalPages": 0,
                                                      "hasNext": false,
                                                      "hasPrevious": false
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "파라미터 변환·검색 유형 누락·페이지 범위·검색어 검증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "parameter",
                                            value = """
                                                    {
                                                      "code": "INVALID_REQUEST_PARAMETER",
                                                      "message": "요청 파라미터의 형식과 값을 확인해 주세요.",
                                                      "fieldErrors": []
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "searchTypeRequired",
                                            value = """
                                                    {
                                                      "code": "INVALID_REQUEST_PARAMETER",
                                                      "message": "회원 조회 조건을 올바르게 입력해 주세요.",
                                                      "fieldErrors": []
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "page",
                                            value = """
                                                    {
                                                      "code": "INVALID_PAGE_REQUEST",
                                                      "message": "페이지는 0 이상이고 조회 개수는 1 이상 50 이하여야 하며, 조회 시작 위치는 지원 범위 이내여야 합니다.",
                                                      "fieldErrors": []
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "keyword",
                                            value = """
                                                    {
                                                      "code": "INVALID_SEARCH_KEYWORD",
                                                      "message": "검색 유형에 맞는 유효한 검색어를 입력해 주세요.",
                                                      "fieldErrors": []
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    })
    @GetMapping
    public ResponseEntity<AdminMemberListResponse> getMembers(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal,

            @Parameter(description = "검색 유형. 생략/빈 문자열은 없음. keyword가 비어 있으면 무시. 잘못된 enum은 400 INVALID_REQUEST_PARAMETER")
            @RequestParam(name = "searchType", required = false)
            AdminMemberSearchType searchType,

            @Parameter(description = "Java strip() 후 비어 있으면 검색하지 않음. MEMBER_ID: 양의 Long, ASCII 숫자 최대 19자리, 선행 0 제거. NICKNAME: 최대 100 코드 포인트, 대소문자 무시 부분 검색, %, _, !는 일반 문자. EMAIL: 소문자 정규화 후 254 UTF-16 코드 단위 이하, 형식 검증 없는 전체 정확 일치")
            @RequestParam(name = "keyword", required = false)
            String keyword,

            @Parameter(description = "PENDING, ACTIVE, RESTRICTED, SUSPENDED, WITHDRAWN, DELETED. 생략/빈 문자열은 전체. ALL/소문자는 400")
            @RequestParam(name = "status", required = false)
            MemberStatus status,

            @Parameter(description = "USER 또는 ADMIN. 생략/빈 문자열은 전체. ALL/소문자는 400")
            @RequestParam(name = "role", required = false)
            MemberRole role,

            @Parameter(description = "LATEST: createdAt DESC → memberId DESC. OLDEST: 둘 다 ASC. 생략/빈 문자열은 LATEST")
            @RequestParam(name = "sort", defaultValue = "LATEST")
            AdminMemberSort sort,

            @Parameter(
                    description = "0 이상, page × size ≤ 2147483647. 생략/빈 문자열은 0",
                    schema = @Schema(
                            type = "integer",
                            format = "int32",
                            minimum = "0",
                            defaultValue = "0"
                    )
            )
            @RequestParam(name = "page", defaultValue = "0")
            int page,

            @Parameter(
                    description = "1~50. 생략/빈 문자열은 20",
                    schema = @Schema(
                            type = "integer",
                            format = "int32",
                            minimum = "1",
                            maximum = "50",
                            defaultValue = "20"
                    )
            )
            @RequestParam(name = "size", defaultValue = "20")
            int size
    ) {
        requirePrincipal(principal);

        AdminMemberReadQuery query = new AdminMemberReadQuery(
                page,
                size,
                searchType,
                keyword,
                status,
                role,
                sort
        );

        Page<AdminMemberItemResult> result = adminMemberReadService.getMembers(
                principal.getMemberId(),
                principal.getCredentialVersion(),
                query
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AdminMemberListResponse.from(result));
    }

    @Operation(
            summary = "관리자 회원 상세 조회",
            description = """
                    가입 대기·정지·탈퇴 등 대상 회원의 상태와 관계없이 조회합니다.
                    DELETED의 이메일은 명시적 null, WITHDRAWN은 현재 저장된 이메일을 반환합니다.
                    WITHDRAWN과 DELETED의 표시 이름은 탈퇴한 회원입니다. withdrawnAt이 없으면 명시적 null입니다.
                    회원 이메일을 포함하나 passwordHash, password, credentialVersion 등 인증 비밀값은 반환하지 않습니다.
                    updatedAt은 회원 정보의 최종 변경 시각이며 최근 로그인 시각이 아닙니다.
                    관리자 로그인 세션이 필요하며 GET 요청에는 CSRF 토큰이 필요하지 않습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "모든 상태의 대상 회원 조회 성공. nullable 필드는 명시적 null",
                    headers = @Header(name = "Cache-Control", schema = @Schema(type = "string", example = "no-store")),
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminMemberDetailResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "member",
                                            value = """
                                                    {
                                                      "memberId": 42,
                                                      "nickname": "이움회원",
                                                      "email": "member@example.com",
                                                      "status": "ACTIVE",
                                                      "role": "USER",
                                                      "createdAt": "2026-11-10T01:00:00Z",
                                                      "updatedAt": "2026-11-10T01:00:00Z",
                                                      "withdrawnAt": null
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "deleted",
                                            value = """
                                                    {
                                                      "memberId": 42,
                                                      "nickname": "탈퇴한 회원",
                                                      "email": null,
                                                      "status": "DELETED",
                                                      "role": "USER",
                                                      "createdAt": "2026-11-10T01:00:00Z",
                                                      "updatedAt": "2026-11-10T02:00:00Z",
                                                      "withdrawnAt": "2026-11-10T02:00:00Z"
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "memberId가 Long으로 변환되지 않음 또는 Long 범위 초과",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "parameter",
                                            value = """
                                                    {
                                                      "code": "INVALID_REQUEST_PARAMETER",
                                                      "message": "요청 파라미터의 형식과 값을 확인해 주세요.",
                                                      "fieldErrors": []
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "대상 없음 또는 Long으로 변환 가능한 0·음수 ID",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "missing",
                                            value = """
                                                    {
                                                      "code": "MEMBER_NOT_FOUND",
                                                      "message": "회원을 찾을 수 없습니다.",
                                                      "fieldErrors": []
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    })
    @GetMapping("/{memberId}")
    public ResponseEntity<AdminMemberDetailResponse> getMember(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal,

            @Parameter(description = "양의 Long 회원 ID. 없음/0/음수는 404 MEMBER_NOT_FOUND. 숫자 변환 불가/Long 범위 초과는 400 INVALID_REQUEST_PARAMETER")
            @PathVariable("memberId") Long memberId
    ) {
        requirePrincipal(principal);

        AdminMemberDetailResult result = adminMemberReadService.getMember(
                principal.getMemberId(),
                principal.getCredentialVersion(),
                memberId
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AdminMemberDetailResponse.from(result));
    }


    private void requirePrincipal(MemberPrincipal principal) {
        if (principal == null) {
            throw new AdminMemberReadException(
                    AdminMemberReadException.Code.AUTHENTICATION_REQUIRED
            );
        }
    }
}
