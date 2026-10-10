package io.github.takgeun.iyum.global.config;

import io.github.takgeun.iyum.global.error.ApiErrorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.endpoint.web.WebServerNamespace;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            @Value("${springdoc.api-docs.enabled:false}")
            boolean apiDocsEnabled,
            @Value("${springdoc.swagger-ui.enabled:false}")
            boolean swaggerUiEnabled
    ) throws Exception {

        AccessDeniedHandler accessDeniedHandler =
                (request, response, exception) -> {
                    boolean csrfFailure =
                            exception instanceof CsrfException;

                    response.setStatus(403);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(
                            StandardCharsets.UTF_8.name()
                    );

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

        http
                .authorizeHttpRequests(authorize -> {
                    authorize
                            .requestMatchers(
                                    HttpMethod.POST,
                                    "/api/v1/auth/signup",
                                    "/api/v1/auth/email/verify",
                                    "/api/v1/auth/email/resend"
                            )
                            .permitAll()    // 로그인하지 않아도 접근 허용 (단, CSRF 검사는 계속 동작. Spring Security는 기본적으로 POST 같은 요청을 CSRF 보호 대상으로 처리)
                            .requestMatchers(
                                    HttpMethod.GET,
                                    "/api/v1/auth/csrf"
                            )
                            .permitAll();

                    if (apiDocsEnabled) {
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
                        authorize
                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/swagger-ui.html",
                                        "/swagger-ui/**"
                                )
                                .permitAll();
                    }

                    // 명시적인 필터 체인을 등록하면
                    // Boot의 Actuator 보안 자동 설정이 물러난다.
                    // 기존 health 공개 범위를 그대로 보존하고
                    // 나머지 경로는 인증을 요구한다.
                    authorize
                            .requestMatchers(
                                    EndpointRequest.to(HealthEndpoint.class),
                                    EndpointRequest.toAdditionalPaths(
                                            WebServerNamespace.SERVER,
                                            HealthEndpoint.class
                                    )
                            )
                            .permitAll()
                            .anyRequest()
                            .authenticated();
                })

                // 기본 HttpSession 저장소와
                // XOR 토큰 처리(BREACH 보호)를 그대로 사용한다.
                .csrf(Customizer.withDefaults())
                .cors(Customizer.withDefaults())

                .exceptionHandling(exceptions ->
                        exceptions.accessDeniedHandler(accessDeniedHandler)
                )

                // 기존 Boot 기본 인증 방식을 보존한다.
                // 회원 로그인 구현은 후속 작업이다.
                .formLogin(Customizer.withDefaults())
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}