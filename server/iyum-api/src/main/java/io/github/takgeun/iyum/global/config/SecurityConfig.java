package io.github.takgeun.iyum.global.config;

import io.github.takgeun.iyum.auth.security.MemberSessionStatusFilter;
import io.github.takgeun.iyum.auth.session.config.SessionSecurityConfig;

import io.github.takgeun.iyum.global.error.ApiErrorResponse;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.endpoint.web.WebServerNamespace;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@Configuration(proxyBeanMethods = false)
@Import(SessionSecurityConfig.class)
public class SecurityConfig {

    // SecurityFilterChain : 요청에 적용할 보안 필터 구성
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,  // 보안 규칙을 구성하는 빌더
            ObjectMapper objectMapper,  // 오류 응답 객체를 JSON으로 변환
            SecurityContextRepository securityContextRepository,    // 인증 정보를 저장하고 조회
            CsrfTokenRepository csrfTokenRepository,    // CSRF 토큰을 저장하고 조회
            MemberRepository memberRepository,
            @Value("${springdoc.api-docs.enabled:false}")
            boolean apiDocsEnabled, // API 명세 경로의 공개 여부 판단
            @Value("${springdoc.swagger-ui.enabled:false}")
            boolean swaggerUiEnabled    // Swagger UI 경로의 공개 여부 판단
    ) throws Exception {

        // AccessDeniedHandler : 403 Forbidden 접근 거절 응답 작성 (이 코드에서는 CSRF 실패도 포함)
        AccessDeniedHandler accessDeniedHandler =
                (request, response, exception) -> {

                    // 예외가 CSRF 관련인지 구분해서 응답 내용을 달리 한다.
                    // CSRF 토큰 누락·불일치 -> 상태코드 403 -> 응답의 code : CSRF_TOKEN_INVALID
                    // 그 외 접근 권한 부족 -> 상태코드 403 -> 응답의 code : ACCESS_DENIED
                    boolean csrfFailure =
                            exception instanceof CsrfException;

                    response.setStatus(403);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(
                            StandardCharsets.UTF_8.name()
                    );

                    // 오류 응답 객체를 JSON으로 변환
                    // 오류 객체를 JSON으로 변환해서 응답 본문에 직접 쓰기
                    // ApiErrorResponse 형식으로 일관되게 응답하도록
                    objectMapper.writeValue(
                            response.getWriter(),
                            ApiErrorResponse.of(
                                    csrfFailure
                                            ? "CSRF_TOKEN_INVALID"
                                            : "ACCESS_DENIED",
                                    csrfFailure
                                            ? "CSRF 토큰을 확인해 주세요."
                                            : "접근 권한이 없습니다."
                            )
                    );
                };

        // AuthenticationEntryPoint : 로그인하지 않은 사용자가 보호된 API에 접근했을 때 실행
        // 즉, 인증이 필요한 경우 401 Unauthorized 인증 필요 응답
        // 다만 이 EntryPoint가 JSON 로그인 API의 비밀번호 오류까지 자동으로 처리한다고 보면 안된다.
        AuthenticationEntryPoint authenticationEntryPoint =
                (request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(
                            StandardCharsets.UTF_8.name()
                    );

                    /**
                     * {
                     *   "code": "AUTHENTICATION_REQUIRED",
                     *   "message": "로그인이 필요합니다.",
                     *   "fieldErrors": []
                     * }
                     */
                    objectMapper.writeValue(
                            response.getWriter(),
                            ApiErrorResponse.of(
                                    "AUTHENTICATION_REQUIRED",
                                    "로그인이 필요합니다."
                            )
                    );
                };

        // authorizeHttpRequests : 경로별 접근 규칙
        // 어떤 요청은 공개하고, 어떤 요청은 로그인을 요구할지 정하는 부분
        http
                .authorizeHttpRequests(authorize -> {
                    authorize
                            // 다음 POST 요청은 로그인 없이 허용
                            .requestMatchers(
                                    HttpMethod.POST,
                                    "/api/v1/auth/signup",
                                    "/api/v1/auth/email/verify",
                                    "/api/v1/auth/email/resend",
                                    "/api/v1/auth/login",
                                    "/api/v1/auth/password-reset/request",
                                    "/api/v1/auth/password-reset/complete"
                            )
                            .permitAll()    // 로그인하지 않아도 접근 허용 (단, CSRF 검사는 계속 동작. Spring Security는 기본적으로 POST 같은 요청을 CSRF 보호 대상으로 처리)

                            // 비로그인 사용자가 로그인·회원가입 요청에 필요한 CSRF 토큰을 미리 받을 수 있도록 하는 설정
                            .requestMatchers(
                                    HttpMethod.GET,
                                    "/api/v1/auth/csrf",
                                    // 읽기 전용 사전 확인만 공개한다. POST의 CSRF 검사는 유지한다.
                                    "/api/v1/auth/password-reset/validate",
                                    "/api/v1/community/posts",
                                    "/api/v1/community/posts/*",
                                    "/api/v1/community/posts/*/comments"
                            )
                            .permitAll();

                    // Swagger 관련 경로는 설정값에 따라 공개
                    if (apiDocsEnabled) {
                        // /v3/api-docs 등의 GET 요청을 permitAll()
                        authorize
                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/v3/api-docs",
                                        "/v3/api-docs/**",
                                        "/v3/api-docs.yaml"
                                )
                                .permitAll();
                    }

                    if (swaggerUiEnabled) {
                        // /swagger-ui.html, /swagger-ui/** 공개
                        authorize
                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/swagger-ui.html",
                                        "/swagger-ui/**"
                                )
                                .permitAll();
                    }

                    // 서버 상태를 확인하는 주소는 로그인 없이 열어두고, 앞에서 공개하지 않은 나머지 주소는 로그인을 요구한다.
                    authorize
                            .requestMatchers(
                                    EndpointRequest.to(HealthEndpoint.class),   // Health 엔드포인트(즉, 서버 상태 확인 기능을 호출하는 주소라는 의미)
                                    EndpointRequest.toAdditionalPaths(  // 메인 서버에 설정된 Health 추가 경로
                                            WebServerNamespace.SERVER,
                                            HealthEndpoint.class
                                    )
                            )
                            .permitAll()
                            .anyRequest()

                            // 로그인 여부만 요구 (만약 비로그인 상태면 접근 거절)
                            // -> 등록해 둔 AuthenticationEntryPoint 실행 (아래 코드에 있음)
                            // -> 컨트롤러 실행하지 않고 응답 반환
                            .authenticated();
                })

                // securityContext : 인증 정보 저장소 연결
                .securityContext(securityContext -> securityContext
                        .securityContextRepository(securityContextRepository)
                        .requireExplicitSave(true)  // 인증 정보를 유지하려면 저장을 명시적으로 수행해야 한다는 의미
                )

                // csrf : 세션 기반 CSRF 저장소 연결
                // 1. GET /api/v1/auth/csrf 로 토큰을 받는다
                // 2. 같은 세션 쿠키를 유지한다.
                // 3. POST 요청 시 X-CSRF-TOKEN 헤더에 토큰을 보낸다.
                // 4. 서버가 전달된 토큰을 검증한다.
                // 기본적으로 GET·HEAD·OPTIONS·TRACE 를 제외한 요청에 CSRF 검사가 적용된다.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                )

                // 프론트엔드의 다른 출처 요청 처리
                // Spring Security의 CORS 처리를 활성화하고, 등록된 CORS 설정(DevCorsConfig)과 연동한다.
                // withDefaults()가 모든 출처를 허용한다는 의미는 아님. 실제 허용 범위는 CORS 설정에 따라 결정
                .cors(Customizer.withDefaults())

                // REST API에서는 로그인 후 이동할 HTML 요청(즉, 복귀용 요청) 저장 끄기
                // 일반적인 HTML 로그인에서는 보호된 페이지에 접근했다가 로그인한 후, 원래 페이지로 돌려보내기 위해 요청을 저장할 수 있음.
                // 지금은 프론트엔드가 화면 이동을 관리하는 JSON API 방식이므로 그 기능을 끈 것.
                .requestCache(AbstractHttpConfigurer::disable)

                // 오류 처리기 연결 및 기본 로그인 방식 비활성화
                // 앞에서 정의한 401·403 처리기를 실제 보안 예외 처리에 연결
                // 여기서 말하는 보안 예외 처리는 "보안 검사에서 요청을 거절했을 때, 클라이언트에게 어떤 응답을 보낼지 정하는 것"
                // 요청을 서버 측에서 어떠한 이유로 막기만 하면 프론트엔드가 그 이유를 알 수가 없으니까 서버가 이유가 담긴 응답을 보내줘야 한다.

                // 참고로 GlobalExceptionHandler는 주로 컨트롤러나 서비스 계층에서 발생한다.
                // 반면 Spring Security는 그보다 앞에 있는 필터 단계에서 요청을 거절할 수 있으므로
                // 여기에 작성한 설정 코드가 필요한 것..!
                /** 예시)
                 * 1. 비로그인 사용자가 보호된 API 요청
                 * 2. Spring Security가 인증이 없다는 것을 확인
                 * 3. 컨트롤러를 실행하지 않고 요청을 거절
                 * 4. 연결된 authenticationEntryPoint가 401 JSON 응답 작성
                 */
                .exceptionHandling(exceptions -> exceptions
                        // 비로그인 사용자의 보호된 API 접근 -> 401
                        .authenticationEntryPoint(authenticationEntryPoint)
                        // 접근 권한 부족 또는 CSRF 오류 -> 403
                        .accessDeniedHandler(accessDeniedHandler)
                )

                // 기본 HTML 로그인과 HTTP Basic 인증을 끄고, 내가 만들 JSON 로그인 API를 통해 인증하도록 준비
                .formLogin(AbstractHttpConfigurer::disable) // 비활성화 대상 : Spring Security의 기본 폼 로그인 페이지와 처리
                .httpBasic(AbstractHttpConfigurer::disable) // 비활성화 대상 : Authorization: Basic ... 방식의 인증

                // 참고로 로그아웃은 CSRF의 보호를 받고, 요청 시 POST로 요청된다.
                //  - 다른 사이트가 사용자를 강제로 로그아웃시키지 못하도록 해야 하기 떄문에 CSRF의 보호를 받아야 함
                //  - 로그아웃은 서버의 로그인 상태를 변경하는 작업이기 때문에 POST로 요청해야 함.
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")   // 로그아웃 요청 주소 지정
                        .invalidateHttpSession(true)    // 현재 서버 세션 무효화
                        .clearAuthentication(true)  // 현재 인증 정보 제거
                        .deleteCookies("JSESSIONID")    // ㅂ라우저에 세션 쿠키 삭제 지시
                        .logoutSuccessHandler(
                                new HttpStatusReturningLogoutSuccessHandler(
                                        HttpStatus.NO_CONTENT   // 리다이렉트 없이 204 반환
                                )
                        )
                );

        // 기존 세션의 인증 정보를 확인한 뒤, 필요하면 인증 제거
        // 이후 익명 인증 처리와 접근 권한 검사가 진행되므로 공개 조회는 그대로 허용하고 보호된 요청은 차단
        // 다만 CSRF 검사는 이 필터보다 앞에서 진행될 수 있으며 CSRF 토큰이 잘못된 변경 요청은 회원 상태 필터에 도달하기 전에 403으로 끝날 수 있음을 유념할 것.
        // 즉, 탈퇴 세션을 사용한 모든 요청이 무조건 401 이라고 가정하면 안된다.
        // 직접 만든 필터를 Spring Security의 필터 체인에 추가 + 실행 위치 지정
        // "회원 상태를 확인하는 MemberSessionStatusFilter를 익명 인증 처리 필터보다 먼저 실행하라"
        // http.addFilterBefore(추가할_필터, 기준이_되는_필터_클래스);
        http.addFilterBefore(
                new MemberSessionStatusFilter(
                        memberRepository,
                        securityContextRepository
                ),
                // 왜 익명 인증 처리보다 앞에 두나?
                // AnonymousAuthenticationFilter는 현재 인증 정보가 없으면 익명 사용자를 나타내는 인증 객체를 넣어주는 핉러임
                // MemberSessionStatusFilter가 DB에서 ACTIVE인지 확인하고, 아니면 인증을 제거해주고 나서야
                // AnonymousAuthenticationFilter에서 인증이 없어진 요청을 익명 사용자로 처리할 수 있다.
                AnonymousAuthenticationFilter.class
        );

        // 지금까지 설정한 규칙을 바탕으로 보안 필터 체인을 만들어 반환
        return http.build();
    }
}