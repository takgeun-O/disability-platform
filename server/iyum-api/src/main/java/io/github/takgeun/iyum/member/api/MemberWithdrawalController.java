package io.github.takgeun.iyum.member.api;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.global.error.ApiErrorResponse;
import io.github.takgeun.iyum.member.api.dto.MemberWithdrawalRequest;
import io.github.takgeun.iyum.member.application.MemberWithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "회원")
public class MemberWithdrawalController {

    private final MemberWithdrawalService withdrawalService;
    private final SecurityContextLogoutHandler securityContextLogoutHandler;
    private final CookieClearingLogoutHandler cookieClearingLogoutHandler;

    public MemberWithdrawalController(
            MemberWithdrawalService withdrawalService,
            SecurityContextRepository securityContextRepository
    ) {
        this.withdrawalService = withdrawalService;

        this.securityContextLogoutHandler = new SecurityContextLogoutHandler();

        this.securityContextLogoutHandler
                .setInvalidateHttpSession(true);

        this.securityContextLogoutHandler
                .setClearAuthentication(true);

        this.securityContextLogoutHandler
                .setSecurityContextRepository(
                        securityContextRepository
                );

        this.cookieClearingLogoutHandler =
                new CookieClearingLogoutHandler("JSESSIONID");
    }

    @Operation(
            summary = "회원 탈퇴",
            description = """
                    현재 로그인한 회원의 비밀번호와 탈퇴 동의를 확인합니다.
                    탈퇴 성공 시 계정 식별·인증 정보를 제거하고
                    현재 로그인 세션을 정리합니다.
                    기존 게시글과 댓글은 유지됩니다.
                    로그인 세션과 유효한 CSRF 토큰이 필요합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "탈퇴 성공 및 현재 세션 정리",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력 오류, 탈퇴 동의 누락 또는 비밀번호 불일치",
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
                    description = "CSRF 검증 실패 또는 서비스에서 비활성 상태 확인",
                    content = @Content(schema = @Schema(
                            implementation = ApiErrorResponse.class
                    ))
            )
    })
    @PostMapping(
            value = "/api/v1/members/me/withdrawal",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Void> withdraw(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal,

            @Parameter(hidden = true)
            Authentication authentication,

            @Valid @RequestBody MemberWithdrawalRequest request,

            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        // 탈퇴 대상은 요청 본문이 아니라 로그인 정보에서 결정한다.
        Long memberId = principal == null
                ? null
                : principal.getMemberId();

        // 서비스가 정상 반환하면 탈퇴 트랜잭션의 커밋까지 완료된 상태다.
        withdrawalService.withdraw(
                memberId,
                request.currentPassword()
        );

        // 탈퇴 성공 후에만 현재 요청의 세션과 인증을 정리한다.
        securityContextLogoutHandler.logout(
                servletRequest,
                servletResponse,
                authentication
        );

        cookieClearingLogoutHandler.logout(
                servletRequest,
                servletResponse,
                authentication
        );

        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
