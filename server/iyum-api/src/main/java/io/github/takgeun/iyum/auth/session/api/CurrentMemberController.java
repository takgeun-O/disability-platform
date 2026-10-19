package io.github.takgeun.iyum.auth.session.api;

import io.github.takgeun.iyum.auth.session.api.dto.CurrentMemberResponse;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.auth.session.application.CurrentMemberReadService;
import io.github.takgeun.iyum.auth.session.application.CurrentMemberResult;
import io.github.takgeun.iyum.global.error.ApiErrorResponse;
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
@Tag(name = "인증")
public class CurrentMemberController {

    private final CurrentMemberReadService currentMemberReadService;

    @Operation(
            summary = "현재 로그인한 회원 조회",
            description = """
                로그인 세션의 회원 ID로 DB의 최신 회원 정보를 조회합니다.
                로그인 이후 변경된 닉네임도 반영합니다.
                """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = CurrentMemberResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인이 필요하거나 세션이 만료됨",
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
            value = "/api/v1/auth/me",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CurrentMemberResponse> me(
            @Parameter(hidden = true)
            // @AuthenticationPrincipal : 앞서 로그인할 때 저장한 인증 정보에서 MemberPrincipal을 가져와 메서드 인자로 전달
            // 따라서 프론트엔드가 memberId나 이메일을 보내면서 "내 정보"라고 주장하는 구조가 아니라
            // 서버가 세션을 통해 현재 회원을 식별한다.
            @AuthenticationPrincipal MemberPrincipal principal
    ) {

        Long memberId = principal == null
                ? null
                : principal.getMemberId();

        CurrentMemberResult result = currentMemberReadService.getCurrentMember(memberId);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CurrentMemberResponse.from(result));

    }
}
