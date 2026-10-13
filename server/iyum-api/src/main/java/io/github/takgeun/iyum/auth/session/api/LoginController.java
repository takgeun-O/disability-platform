package io.github.takgeun.iyum.auth.session.api;

import io.github.takgeun.iyum.auth.session.api.dto.LoginRequest;
import io.github.takgeun.iyum.auth.session.api.dto.LoginResponse;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.global.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "인증")
public class LoginController {

    private final AuthenticationManager authenticationManager;

    private final SessionAuthenticationStrategy loginSessionAuthenticationStrategy;

    private final SecurityContextRepository securityContextRepository;

    @Operation(
            summary = "로그인",
            description = "이메일과 비밀번호를 검증하고 ACTIVE 회원을 로그인시킵니다. "
                    + "성공하면 인증 정보를 세션에 저장합니다. "
                    + "로그인 성공 후 GET /api/v1/auth/csrf를 다시 호출하여 "
                    + "새 CSRF 토큰을 받아야 합니다.",
            parameters = @Parameter(
                    name = "X-CSRF-TOKEN",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "동일한 세션에서 조회한 CSRF 토큰",
                    schema = @Schema(type = "string")
            )
    )
    @ApiResponse(
            responseCode = "200",
            description = "로그인 성공",
            content = @Content(
                    schema = @Schema(implementation = LoginResponse.class)
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "입력 검증 또는 JSON 형식 오류",
            content = @Content(
                    schema = @Schema(implementation = ApiErrorResponse.class)
            )
    )
    @ApiResponse(
            responseCode = "401",
            description = "이메일 또는 비밀번호 불일치",
            content = @Content(
                    schema = @Schema(implementation = ApiErrorResponse.class)
            )
    )
    @ApiResponse(
            responseCode = "403",
            description = "이메일 인증 필요, 로그인 불가 상태 또는 CSRF 오류",
            content = @Content(
                    schema = @Schema(implementation = ApiErrorResponse.class)
            )
    )
    @ApiResponse(
            responseCode = "500",
            description = "서버 오류",
            content = @Content(
                    schema = @Schema(implementation = ApiErrorResponse.class)
            )
    )
    @PostMapping(
            value = "/api/v1/auth/login",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {

        // 입력한 이메일과 비밀번호를 인증 전 객체로 만든다.
        UsernamePasswordAuthenticationToken loginToken =
                UsernamePasswordAuthenticationToken.unauthenticated(
                        loginRequest.email(),
                        loginRequest.password()
                );

        Authentication authentication;

        try {
            // 기존에 만든 인증 설정을 호출한다.
            // 여기서 회원 조회, 비밀번호 비교, 회원 상태 검사가 수행된다.
            // 실패하면 예외가 발생하면 아래의 세션 저장 코드로 진행하지 않는다.

            /**
             * 1. AuthenticationConfig에서 인증 담당자 연결
             * @Bean public AuthenticationManager authenticationManager(...)
             * -> ProviderManager를 반환하므로 실제로 ProviderManager에게 인증을 요청하고
             * ProviderManager는 등록된 DaoAuthenticationProvider에 처리를 맡긴다.
             *
             * 2. DaoAuthenticationProvider에 세 가지 검사 방법을 설정했었음.
             *      - 회원 조회 : new DaoAuthenticationProvider(memberUserDetailsService)
             *      - 비밀번호 비교에 인코더 사용 : setPasswordEncoder(passwordEncoder)
             *      - 비밀번호 비교 전 계정 상태 검사 비우기 : setPreAuthenticationChecks(userDetails -> {})
             *      - 비밀번호가 맞으면 이 검사기로 회원 상태 검사 : setPostAuthenticationChecks(new MemberLoginStatusChecker())
             *
             * 3. 회원 조회는 MemberUserDetailsService에서 수행
             *  Provider가 MemberUserDetailsService의 loadUserByUsername(String username) 호출
             *  이 때 로그인 요청에서 전달한 이메일이 username으로 들어올 것.
             *  회원이 있는 경우) DB의 비밀번호 해시와 회원 상태 등을 담은 MemberPrincipal 반환
             *  회원이 없는 경우) 예외 발생 -> provider.setHideUserNotFoundException(true)에 따라 일반적인 인증 실패인 BadCredentialsException으로 바꿔 전달
             *
             * 4. 비밀번호 비교는 Spring Security의 DaoAuthenticationProvider 내부에서 수행
             *
             * 5. 회원 상태 검사는 MemberLoginStatusChecker의 check()에서 수행
             *
             * 위 단계가 전부 통과해야 authentication 이 정해진다.
             */
            authentication =
                    authenticationManager.authenticate(loginToken);
        } finally {
            loginToken.eraseCredentials();
        }

        if(!(authentication.getPrincipal()
                instanceof MemberPrincipal principal)) {
            throw new InternalAuthenticationServiceException(
                    "지원하지 않는 회원 인증 정보입니다."
            );
        }

        // 로그인 성공 시 세션 ID 변경과 기존 CSRF 토큰 폐기
        // loginSessionAuthenticationStrategy 는 SessionSecurityConfig 설정 파일에서 @Bean 등록되어 있음.
        loginSessionAuthenticationStrategy.onAuthentication(
                authentication,
                request,
                response
        );

        // 현재 요청에서 사용할 인증 정보 설정
        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        // 다음 요청에서도 인증 정보를 사용할 수 있도록 저장
        securityContextRepository.saveContext(
                context,
                request,
                response
        );

        return ResponseEntity
                .ok()
                .cacheControl(CacheControl.noStore())
                .body(LoginResponse.from(principal));
    }
}
