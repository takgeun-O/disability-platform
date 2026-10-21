package io.github.takgeun.iyum.auth.passwordreset.api;

import io.github.takgeun.iyum.auth.passwordreset.api.dto.PasswordResetCompleteRequest;
import io.github.takgeun.iyum.auth.passwordreset.api.dto.PasswordResetIssueRequest;
import io.github.takgeun.iyum.auth.passwordreset.api.dto.PasswordResetIssueResponse;
import io.github.takgeun.iyum.auth.passwordreset.application.PasswordResetRateLimitException;
import io.github.takgeun.iyum.auth.passwordreset.application.PasswordResetService;
import io.github.takgeun.iyum.auth.passwordreset.application.PasswordResetTokenIssueService;
import io.github.takgeun.iyum.auth.passwordreset.infrastructure.PasswordResetIpRateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 재설정 메일 요청 : POST /api/v1/auth/password-reset/request -> 202 Accepted + 공통 안내
 * 새 비밀번호 저장 : POST /api/v1/auth/password-reset/complete -> 204 No Content
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/password-reset")
@Tag(name = "인증")
public class PasswordResetController {

    private final PasswordResetTokenIssueService issueService;
    private final PasswordResetService resetService;
    private final PasswordResetIpRateLimiter ipRateLimiter;

    @Operation(
            summary = "비밀번호 재설정 메일 요청",
            description = "로그인 없이 요청할 수 있으며 CSRF 토큰이 필요합니다. "
                    + "가입 여부와 회원 상태, 회원별 발급 제한 여부는 "
                    + "응답으로 구분하지 않습니다."
    )
    @PostMapping(
            value = "/request",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<PasswordResetIssueResponse> request(
            @Valid @RequestBody PasswordResetIssueRequest request,
            HttpServletRequest servletRequest
    ) {

        String clientIp = servletRequest.getRemoteAddr();

        if(!ipRateLimiter.tryAcquire(clientIp)) {
            throw new PasswordResetRateLimitException();
        }

        issueService.issue(request.email());

        return ResponseEntity
                .accepted() // 요청을 접수했다는 것이며, 실제 메일 도착을 보장하지 않음.
                .cacheControl(CacheControl.noStore())
                .body(PasswordResetIssueResponse.accepted());
    }

    @Operation(
            summary = "비밀번호 재설정 완료",
            description = "원본 토큰과 새 비밀번호를 검증하고 변경합니다. "
                    + "로그인 없이 요청할 수 있으며 CSRF 토큰이 필요합니다. "
                    + "성공 후 자동 로그인하지 않습니다."
    )
    @PostMapping(
            value = "/complete",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Void> complete(
            @Valid @RequestBody PasswordResetCompleteRequest request
    ) {
        resetService.reset(
                request.token(),
                request.newPassword()
        );

        return ResponseEntity
                .noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
