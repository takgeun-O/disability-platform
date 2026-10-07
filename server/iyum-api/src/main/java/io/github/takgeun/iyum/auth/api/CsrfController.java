package io.github.takgeun.iyum.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(
        name = "인증",
        description = "회원가입 및 인증 요청"
)
public class CsrfController {

    @Operation(
            summary = "CSRF 토큰 조회",
            description = "비로그인 상태에서도 조회할 수 있습니다. "
                    + "응답 token을 회원가입 또는 이메일 인증 요청의 "
                    + "X-CSRF-TOKEN 헤더에 넣고 동일한 세션 쿠키를 유지하세요. "
    )
    @GetMapping(
            value = "/api/v1/auth/csrf",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CsrfResponse> csrf(
            @Parameter(hidden = true) CsrfToken csrfToken
    ) {
        return ResponseEntity
                .ok()
                .cacheControl(CacheControl.noStore())
                .body(
                        new CsrfResponse(
                                csrfToken.getHeaderName(),
                                csrfToken.getToken()
                        )
                );
    }

    @Schema(description = "현재 세션의 CSRF 토큰")
    public record CsrfResponse(
            @Schema(example = "X-CSRF-TOKEN")
            String headerName,

            @Schema(description = "동일한 세션의 요청 헤더에 전달할 CSRF 토큰")
            String token
    ) {
    }
}