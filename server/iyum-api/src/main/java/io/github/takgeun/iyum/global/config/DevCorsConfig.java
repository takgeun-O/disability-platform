package io.github.takgeun.iyum.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@Profile("dev")
public class DevCorsConfig {

    // SecurityConfig의 cors(withDefaults())가 이 이름의 빈을 사용한다.
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${iyum.cors.allowed-origin}") String allowedOrigin
    ) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(allowedOrigin));
        cors.setAllowCredentials(true);
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Accept", "Content-Type", "X-CSRF-TOKEN"));
        cors.setMaxAge(600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/auth/csrf", cors);
        source.registerCorsConfiguration("/api/v1/auth/signup", cors);
        return source;
    }
}
