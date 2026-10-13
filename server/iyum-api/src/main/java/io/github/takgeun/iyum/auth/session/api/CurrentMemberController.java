package io.github.takgeun.iyum.auth.session.api;

import io.github.takgeun.iyum.auth.session.api.dto.CurrentMemberResponse;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.global.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "인증")
public class CurrentMemberController {

    // 컨트롤러인데 별도의 서비스가 없는 이유
    // 이미 세션에 저장된 회원 정보를 반환하기 때문에 DB 조회가 필요없음.
    // 다만 이 떄 이 정보는 로그인 당시의 정보임.
    // 나중에 닉네임 변경이나 계정 정지 기능을 만들면 최신 정보 조회와 기존 세션의 권한 반영 정책을 추가하여야 함.

    @Operation(
            summary = "현재 로그인한 회원 조회",
            description = "현재 로그인 세션에 저장된 회원 정보를 반환합니다."
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
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CurrentMemberResponse.from(principal));

    }
}
