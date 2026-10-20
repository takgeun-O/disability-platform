package io.github.takgeun.iyum.member.api;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.global.error.ApiErrorResponse;
import io.github.takgeun.iyum.member.api.dto.MemberProfileResponse;
import io.github.takgeun.iyum.member.application.MemberProfileResult;
import io.github.takgeun.iyum.member.application.MemberProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "회원")
public class MemberProfileController {

    private final MemberProfileService memberProfileService;

    @Operation(
            summary = "내 프로필 조회",
            description = """
                    로그인 세션의 회원 ID로 DB의 최신 프로필을 조회합니다.
                    회원 ID를 요청 파라미터로 받지 않습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = MemberProfileResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인이 필요하거나 세션에 해당하는 회원이 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "현재 활성 상태가 아닌 회원",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            )
    })
    @GetMapping(
            value = "/api/v1/members/me",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<MemberProfileResponse> getProfile(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal
    ) {

        // 회원 ID는 반드시 principal에서 가져오기.
        // 사용자가 ?memberId=다른회원ID를 붙여도 조회 대상을 바꾸지 못하도록
        Long memberId = principal == null
                ? null
                : principal.getMemberId();

        MemberProfileResult result = memberProfileService.getProfile(memberId);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())   // 개인 프로필 응답을 HTTP 캐시에 저장 안하기
                .body(MemberProfileResponse.from(result));
    }
}
