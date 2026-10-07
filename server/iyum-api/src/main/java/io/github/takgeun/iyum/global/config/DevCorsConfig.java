package io.github.takgeun.iyum.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 개발용 프론트엔드에서 백엔드의 인증 API를 호출할 수 있도록 허용하는 CORS 설정
 *
 * CORS 란?
 * Cross-Origin Resource Sharing. 서로 다른 출처 간의 리소스 공유를 허용하는 방식
 * 여기서 origin은 프로토콜 + 호스트 + 포트의 조합.
 * 이 셋 중 하나라도 다르면 서로 다른 출처로 취급한다.
 * 브라우저는 기본적으로 다른 출처의 응답을 JavaScript가 마음대로 읽지 못하도록 제한하는데
 * 백엔드는 CORS 응답 헤더를 통해 "이 프론트엔드에는 응답 접근을 허용한다"고 알려줄 수 있다.
 *
 */
@Configuration(proxyBeanMethods = false)    // Spring 설정 클래스. @Bean 메서드 간 직접 호출을 관리하는 프록시를 만들지 않는다.
@Profile("dev")
public class DevCorsConfig {

    // SecurityConfig의 cors(withDefaults())가 이 이름의 빈을 사용한다.
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${iyum.cors.allowed-origin}") String allowedOrigin
    ) {
        CorsConfiguration cors = new CorsConfiguration();

        // 설정된 출처에서 오는 교차 출처 요청을 허용
        cors.setAllowedOrigins(List.of(allowedOrigin));

        // 쿠키 같은 자격 증명을 포함하는 교차 출처 요청을 허용
//        fetch("http://127.0.0.1:8082/api/v1/auth/csrf", {
//                credentials: "include"
//        });
        cors.setAllowCredentials(true);

        // CORS 정책에서 허용할 HTTP 메서드 지정
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));

        // 교차 출처 요청에서 허용할 요청 헤더를 지정
        // Accept : 클라이언트가 받고 싶은 응답 형식
        cors.setAllowedHeaders(List.of("Accept", "Content-Type", "X-CSRF-TOKEN"));

        // 사전 확인 요청의 허용 결과를 600초, 즉 10분간 캐시할 수 있도록 지정. (다만 브라우저의 자체 제한이 적용될 수 있음을 감안)
        // 여기서 캐시하는 것은 "이 요청 방식이 허용되는가?"라는 결과임. (회원가입 응답이나 CSRF 토큰 자체를 10분간 저장한다는 뜻이 아님을 주의)
        // 사전 확인 요청
        // 브라우저가 실제 POST 전에 OPTIONS 요청을 보낼 수 있음. 이것을 preflight라고 한다.
        // -> "이 출처에서 JSON과 CSRF 헤더를 담은 POST 요청을 보내도 되나요?" 라는 의미
        // -> 서버가 CORS 설정에 따라 허용하면 브라우저가 실제 회원가입 요청을 보냄.
        cors.setMaxAge(600L);

        // 설정을 허용할 API 경로 지정
        // UrlBasedCorsConfigurationSource : 요청 URL에 맞는 CORS 설정을 찾아주는 역할
        // SecurityCOnfig의 .cors(Customizer.withDefaults()) 코드가 Spring Security의 요청 처리에 아래 설정을 연결한다.
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/auth/csrf", cors);
        source.registerCorsConfiguration("/api/v1/auth/signup", cors);
        source.registerCorsConfiguration("/api/v1/auth/email/verify", cors);

        return source;
    }
}
