package io.github.takgeun.iyum.member.api;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.global.error.ApiErrorResponse;
import io.github.takgeun.iyum.member.api.dto.MemberPasswordChangeRequest;
import io.github.takgeun.iyum.member.api.dto.MemberPasswordChangeResponse;
import io.github.takgeun.iyum.member.application.MemberPasswordChangeResult;
import io.github.takgeun.iyum.member.application.MemberPasswordChangeService;
import io.github.takgeun.iyum.member.security.MemberPasswordChangeSessionUpdater;
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
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@RestController
@Tag(name = "회원")
public class MemberPasswordChangeController {

    private final MemberPasswordChangeService passwordChangeService;
    private final MemberPasswordChangeSessionUpdater sessionUpdater;
    private final Clock clock;

    private final RequestAttributeSecurityContextRepository requestContextRepository =
            new RequestAttributeSecurityContextRepository();

    public MemberPasswordChangeController(
            MemberPasswordChangeService passwordChangeService,
            MemberPasswordChangeSessionUpdater sessionUpdater,
            Clock clock
    ) {
        this.passwordChangeService = passwordChangeService;
        this.sessionUpdater = sessionUpdater;
        this.clock = clock;
    }

    @Operation(
            summary = "내 비밀번호 변경",
            description = """
                    현재 비밀번호를 확인하고 새 비밀번호로 변경합니다.
                    로그인 세션과 유효한 CSRF 토큰이 필요합니다.
                    변경 성공 후 현재 세션의 로그인 유지를 시도합니다.
                    reloginRequired가 true여도 비밀번호 변경은 완료된 상태입니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "변경 성공. reloginRequired로 재로그인 필요 여부 확인",
                    content = @Content(schema = @Schema(
                            implementation = MemberPasswordChangeResponse.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력 오류, 현재 비밀번호 불일치 또는 변경 전과 같은 비밀번호",
                    content = @Content(schema = @Schema(
                            implementation = ApiErrorResponse.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인 또는 재로그인 필요",
                    content = @Content(schema = @Schema(
                            implementation = ApiErrorResponse.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "CSRF 검증 실패",
                    content = @Content(schema = @Schema(
                            implementation = ApiErrorResponse.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "변경 시도 제한. Retry-After 헤더로 대기 시간 안내",
                    content = @Content(schema = @Schema(
                            implementation = ApiErrorResponse.class
                    ))
            )
    })
    @PostMapping(
            value = "/api/v1/members/me/password",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> change(
            @Parameter(hidden = true)
            Authentication authentication,

            @Valid @RequestBody MemberPasswordChangeRequest request,

            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal()
                instanceof MemberPrincipal principal)) {
            return authenticationRequired(servletRequest, servletResponse);
        }

        // 서비스 호출 전에 기존 세션을 기록한다.
        // capture() 실패 -> 서비스 호출 전에 401로 종료
        var snapshot = sessionUpdater.capture(
                servletRequest,
                authentication
        );

        if (snapshot == null) {
            return authenticationRequired(servletRequest, servletResponse);
        }

        // 회원 ID와 인증 버전은 로그인 정보에서만 가져온다.
        // 이 호출이 Changed를 반환하면 비밀번호 변경 커밋까지 끝난 상태
        MemberPasswordChangeResult result = passwordChangeService.change(
                principal.getMemberId(),
                principal.getCredentialVersion(),
                request.currentPassword(),
                request.newPassword()
        );

        return switch (result) {
            case MemberPasswordChangeResult.Changed changed -> {
                var sessionResult = sessionUpdater.update(
                        snapshot,
                        changed,
                        servletRequest,
                        servletResponse
                );

                boolean reloginRequired = switch (sessionResult) {
                    case SESSION_RETAINED -> false;
                    case RELOGIN_REQUIRED -> true;
                };

                yield ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .body(new MemberPasswordChangeResponse(
                                reloginRequired
                        ));
            }

            case MemberPasswordChangeResult.Rejected rejected -> rejectionResponse(
                    rejected,
                    servletRequest,
                    servletResponse
            );
        };
    }

    private ResponseEntity<ApiErrorResponse> rejectionResponse(
            MemberPasswordChangeResult.Rejected rejected,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        return switch (rejected.reason()) {
            case AUTHENTICATION_REQUIRED -> authenticationRequired(request, response);

            case INVALID_CURRENT_PASSWORD -> fieldError(
                    "PASSWORD_CHANGE_INVALID_CURRENT_PASSWORD",
                    "현재 비밀번호 입력값을 확인해 주세요.",
                    "currentPassword"
            );

            case INVALID_NEW_PASSWORD -> fieldError(
                    "PASSWORD_CHANGE_INVALID_NEW_PASSWORD",
                    "새 비밀번호 입력값을 확인해 주세요.",
                    "newPassword"
            );

            case CURRENT_PASSWORD_MISMATCH -> fieldError(
                    "PASSWORD_CHANGE_CURRENT_PASSWORD_MISMATCH",
                    "현재 비밀번호가 일치하지 않습니다.",
                    "currentPassword"
            );

            case PASSWORD_UNCHANGED -> fieldError(
                    "PASSWORD_CHANGE_PASSWORD_UNCHANGED",
                    "현재 비밀번호와 다른 비밀번호를 입력해 주세요.",
                    "newPassword"
            );

            case RATE_LIMITED -> ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .cacheControl(CacheControl.noStore())
                    .header(
                            HttpHeaders.RETRY_AFTER,
                            Long.toString(
                                    retryAfterSeconds(rejected.blockedUntil())
                            )
                    )
                    .body(ApiErrorResponse.of(
                            "PASSWORD_CHANGE_RATE_LIMITED",
                            "비밀번호 변경 시도가 많습니다. 잠시 후 다시 시도해 주세요."
                    ));
        };
    }

    private ResponseEntity<ApiErrorResponse> fieldError(
            String code,
            String message,
            String field
    ) {
        return ResponseEntity.badRequest()
                .cacheControl(CacheControl.noStore())
                .body(new ApiErrorResponse(
                        code,
                        message,
                        List.of(new ApiErrorResponse.FieldError(
                                field,
                                message
                        ))
                ));
    }

    private ResponseEntity<ApiErrorResponse> authenticationRequired(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        // 다른 요청이 갱신한 세션에 영향을 주지 않도록
        // 현재 요청의 인증만 비운다.
        SecurityContextHolder.clearContext();

        requestContextRepository.saveContext(
                SecurityContextHolder.createEmptyContext(),
                request,
                response
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .cacheControl(CacheControl.noStore())
                .body(ApiErrorResponse.of(
                        "AUTHENTICATION_REQUIRED",
                        "로그인이 필요합니다."
                ));
    }

    private long retryAfterSeconds(Instant blockedUntil) {
        Duration remaining = Duration.between(
                clock.instant(),
                blockedUntil
        );

        if (remaining.isNegative() || remaining.isZero()) {
            return 0;
        }

        // 제한 종료 전에 재시도하지 않도록 남은 시간을 초 단위로 올림한다.
        return remaining.getSeconds()
                + (remaining.getNano() == 0 ? 0 : 1);
    }
}
