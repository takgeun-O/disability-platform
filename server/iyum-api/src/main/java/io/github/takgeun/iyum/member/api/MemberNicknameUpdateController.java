package io.github.takgeun.iyum.member.api;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.global.error.ApiErrorResponse;
import io.github.takgeun.iyum.member.api.dto.MemberNicknameUpdateRequest;
import io.github.takgeun.iyum.member.api.dto.MemberProfileResponse;
import io.github.takgeun.iyum.member.application.MemberNicknameUpdateService;
import io.github.takgeun.iyum.member.application.MemberProfileResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "회원")
public class MemberNicknameUpdateController {

    private final MemberNicknameUpdateService nicknameUpdateService;

    @Operation(
            summary = "내 닉네임 수정",
            description = """
                    현재 로그인한 회원의 닉네임을 변경합니다.
                    로그인 세션과 유효한 CSRF 토큰이 필요합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "변경 성공 또는 현재와 동일한 닉네임",
                    content = @Content(schema = @Schema(
                            implementation = MemberProfileResponse.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 또는 닉네임 형식",
                    content = @Content(schema = @Schema(
                            implementation = ApiErrorResponse.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인이 필요하거나 회원이 없음",
                    content = @Content(schema = @Schema(
                            implementation = ApiErrorResponse.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "CSRF 검증 실패 또는 비활성 회원",
                    content = @Content(schema = @Schema(
                            implementation = ApiErrorResponse.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "다른 회원이 사용 중인 닉네임",
                    content = @Content(schema = @Schema(
                            implementation = ApiErrorResponse.class
                    ))
            )
    })
    @PutMapping(
            value = "/api/v1/members/me/nickname",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<MemberProfileResponse> updateNickname(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody MemberNicknameUpdateRequest request
    ) {
        Long memberId = principal == null
                ? null
                : principal.getMemberId();

        MemberProfileResult result = nicknameUpdateService.updateNickname(
                memberId,
                request.nickname()
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(MemberProfileResponse.from(result));
    }
}
