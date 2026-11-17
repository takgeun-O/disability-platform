package io.github.takgeun.iyum.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
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
        // 프론트에서 아래와 같이 사용할 것
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
        // SecurityConfig의 .cors(Customizer.withDefaults()) 코드가 Spring Security의 요청 처리에 아래 설정을 연결한다.
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/auth/csrf", cors);
        source.registerCorsConfiguration("/api/v1/auth/signup", cors);
        source.registerCorsConfiguration("/api/v1/auth/email/verify", cors);

        source.registerCorsConfiguration("/api/v1/auth/email/resend", cors);
        source.registerCorsConfiguration("/api/v1/auth/login", cors);   // 프론트엔드에서 로그인 API 호출 시 기존 개발 Origin과 쿠키 포함 요청 허용

        source.registerCorsConfiguration("/api/v1/auth/me", cors);
        source.registerCorsConfiguration("/api/v1/auth/logout", cors);

        source.registerCorsConfiguration("/api/v1/community/posts", cors);
        // 기존 Origin·쿠키·허용 헤더·캐시 설정을 복사
        // new CorsConfiguration(cors): 기존 설정을 복사하는 생성자 (복사한 객체의 허용 메서드만 바꾸고자 하는 의도)
        CorsConfiguration postDetailCors = new CorsConfiguration(cors);
        // 게시글 상세 경로에만 PUT을 추가한다.
        postDetailCors.setAllowedMethods(
                List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")
        );
        source.registerCorsConfiguration(
                "/api/v1/community/posts/*",
                postDetailCors);

        source.registerCorsConfiguration("/api/v1/community/posts/*/comments", cors);
        CorsConfiguration commentItemCors = new CorsConfiguration(cors);
        commentItemCors.setAllowedMethods(List.of("PUT", "DELETE", "OPTIONS"));
        source.registerCorsConfiguration("/api/v1/community/posts/*/comments/*", commentItemCors);

        // 내 프로필 조회: 기존 출처·쿠키·헤더 설정 유지
        CorsConfiguration memberProfileCors =
                new CorsConfiguration(cors);

        memberProfileCors.setAllowedMethods(
                List.of("GET", "OPTIONS")
        );

        source.registerCorsConfiguration(
                "/api/v1/members/me",
                memberProfileCors
        );

        CorsConfiguration memberNicknameCors =
                new CorsConfiguration(cors);

        memberNicknameCors.setAllowedMethods(
                List.of("PUT", "OPTIONS")
        );

        source.registerCorsConfiguration(
                "/api/v1/members/me/nickname",
                memberNicknameCors
        );

        // 내 활동 요약 조회에 기존 개발 Origin·쿠키·허용 헤더 설정을 적용한다.
        // 기존의 cors 복사하므로 허용 Origin, 쿠키 포함 요청, 허용 헤더, preflight 캐시 설정이 이어짐
        CorsConfiguration memberActivityCors =
                new CorsConfiguration(cors);

        memberActivityCors.setAllowedMethods(
                List.of("GET", "OPTIONS")
        );

        source.registerCorsConfiguration(
                "/api/v1/members/me/activity-summary",
                memberActivityCors
        );

        source.registerCorsConfiguration(
                "/api/v1/members/me/posts",
                memberActivityCors
        );

        source.registerCorsConfiguration(
                "/api/v1/members/me/comments",
                memberActivityCors
        );

        CorsConfiguration memberWithdrawalCors =
                new CorsConfiguration(cors);

        memberWithdrawalCors.setAllowedMethods(
                List.of("POST", "OPTIONS")
        );

        source.registerCorsConfiguration(
                "/api/v1/members/me/withdrawal",
                memberWithdrawalCors
        );

        CorsConfiguration passwordResetCors =
                new CorsConfiguration(cors);

        passwordResetCors.setAllowedMethods(
                List.of("POST", "OPTIONS")
        );

        source.registerCorsConfiguration(
                "/api/v1/auth/password-reset/request",
                passwordResetCors
        );

        source.registerCorsConfiguration(
                "/api/v1/auth/password-reset/complete",
                passwordResetCors
        );

        // 사전 확인은 쿠키 발급·변경 없이 GET으로 조회한다. 기존 POST 허용 범위는 그대로 둔다.
        CorsConfiguration resetValidationCors = new CorsConfiguration(cors);
        resetValidationCors.setAllowedMethods(List.of("GET", "OPTIONS"));
        source.registerCorsConfiguration("/api/v1/auth/password-reset/validate", resetValidationCors);


        CorsConfiguration memberPasswordCors =
                new CorsConfiguration(cors);

        memberPasswordCors.setAllowedMethods(
                List.of("POST", "OPTIONS")
        );

        // 프론트 JavaScript에서 Retry-After 응답 헤더를 읽을 수 있도록 허용
        memberPasswordCors.setExposedHeaders(
                List.of(HttpHeaders.RETRY_AFTER)
        );

        source.registerCorsConfiguration(
                "/api/v1/members/me/password",
                memberPasswordCors
        );

        CorsConfiguration postLikeCors =
                new CorsConfiguration(cors);

        postLikeCors.setAllowedMethods(
                List.of("PUT", "DELETE", "OPTIONS")
        );

        source.registerCorsConfiguration(
                "/api/v1/community/posts/*/like",
                postLikeCors
        );

        CorsConfiguration commentLikeCors =
                new CorsConfiguration(cors);

        commentLikeCors.setAllowedMethods(
                List.of("PUT", "DELETE", "OPTIONS")
        );

        source.registerCorsConfiguration(
                "/api/v1/community/posts/*/comments/*/like",
                commentLikeCors
        );

        CorsConfiguration adminPostModerationCors =
                new CorsConfiguration(cors);

        adminPostModerationCors.setAllowedMethods(
                List.of("POST", "OPTIONS")
        );

        source.registerCorsConfiguration(
                "/api/v1/admin/posts/*/hide",
                adminPostModerationCors
        );

        source.registerCorsConfiguration(
                "/api/v1/admin/posts/*/restore",
                adminPostModerationCors
        );

        CorsConfiguration adminCommentModerationCors =
                new CorsConfiguration(cors);

        adminCommentModerationCors.setAllowedMethods(
                List.of("POST", "OPTIONS")
        );

        source.registerCorsConfiguration(
                "/api/v1/admin/posts/*/comments/*/hide",
                adminCommentModerationCors
        );

        source.registerCorsConfiguration(
                "/api/v1/admin/posts/*/comments/*/restore",
                adminCommentModerationCors
        );

        // 관리자 게시글 목록·상세 조회
        CorsConfiguration adminPostReadCors =
                new CorsConfiguration(cors);

        adminPostReadCors.setAllowedMethods(
                List.of("GET", "OPTIONS")
        );

        source.registerCorsConfiguration(
                "/api/v1/admin/posts",
                adminPostReadCors
        );

        source.registerCorsConfiguration(
                "/api/v1/admin/posts/*",
                adminPostReadCors
        );

        CorsConfiguration adminCommentReadCors = new CorsConfiguration();

        adminCommentReadCors.setAllowedOrigins(
                List.of(allowedOrigin)
        );

        adminCommentReadCors.setAllowCredentials(true);

        adminCommentReadCors.setAllowedMethods(
                List.of("GET", "OPTIONS")
        );

        adminCommentReadCors.setAllowedHeaders(
                List.of(
                        "Accept",
                        "Content-Type",
                        "X-CSRF-TOKEN"
                )
        );

        adminCommentReadCors.setMaxAge(600L);

        source.registerCorsConfiguration(
                "/api/v1/admin/posts/*/comments",
                adminCommentReadCors
        );

        // 관리자 댓글 단건 조회에도 기존 댓글 조회 CORS 정책을 적용한다.
        // 허용된 개발 Origin에서 쿠키를 포함한 GET 요청을 허용한다.
        source.registerCorsConfiguration(
                "/api/v1/admin/comments/*",
                adminCommentReadCors
        );

        // 관리자 운영 기록 조회
        CorsConfiguration adminOperationLogReadCors =
                new CorsConfiguration(cors);

        adminOperationLogReadCors.setAllowedMethods(
                List.of("GET", "OPTIONS")
        );

        source.registerCorsConfiguration(
                "/api/v1/admin/operation-logs",
                adminOperationLogReadCors
        );

        source.registerCorsConfiguration(
                "/api/v1/admin/operation-logs/summary",
                adminOperationLogReadCors
        );

        // 관리자 회원 목록·상세 조회
        CorsConfiguration adminMemberReadCors =
                new CorsConfiguration(cors);

        adminMemberReadCors.setAllowedMethods(
                List.of("GET", "OPTIONS")
        );

        source.registerCorsConfiguration(
                "/api/v1/admin/members",
                adminMemberReadCors
        );

        source.registerCorsConfiguration(
                "/api/v1/admin/members/*",
                adminMemberReadCors
        );

        // 관리자 회원 이용 정지·해제
        CorsConfiguration adminMemberModerationCors =
                new CorsConfiguration(cors);

        adminMemberModerationCors.setAllowedMethods(
                List.of("POST", "OPTIONS")
        );

        source.registerCorsConfiguration(
                "/api/v1/admin/members/*/suspend",
                adminMemberModerationCors
        );

        source.registerCorsConfiguration(
                "/api/v1/admin/members/*/lift-suspension",
                adminMemberModerationCors
        );

        // 관리자 회원별 이용 정지·해제 이력 조회
        source.registerCorsConfiguration(
                "/api/v1/admin/members/*/moderation-logs",
                adminMemberReadCors
        );

        // 게시글·댓글 신고 접수
        CorsConfiguration contentReportCors =
                new CorsConfiguration(cors);

        contentReportCors.setAllowedMethods(
                List.of("POST", "OPTIONS")
        );

        source.registerCorsConfiguration(
                "/api/v1/community/posts/*/reports",
                contentReportCors
        );

        source.registerCorsConfiguration(
                "/api/v1/community/posts/*/comments/*/reports",
                contentReportCors
        );

        // 관리자 신고 목록·상세 조회
        CorsConfiguration adminReportReadCors =
                new CorsConfiguration(cors);

        adminReportReadCors.setAllowedMethods(
                List.of("GET", "OPTIONS")
        );

        source.registerCorsConfiguration(
                "/api/v1/admin/reports",
                adminReportReadCors
        );

        source.registerCorsConfiguration(
                "/api/v1/admin/reports/*",
                adminReportReadCors
        );

        // 관리자 신고 처리 완료·기각
        CorsConfiguration adminReportHandlingCors =
                new CorsConfiguration(cors);

        adminReportHandlingCors.setAllowedMethods(
                List.of("POST", "OPTIONS")
        );

        source.registerCorsConfiguration(
                "/api/v1/admin/reports/*/resolve",
                adminReportHandlingCors
        );

        source.registerCorsConfiguration(
                "/api/v1/admin/reports/*/dismiss",
                adminReportHandlingCors
        );

        source.registerCorsConfiguration(
                "/api/v1/admin/reports/*/hide-and-resolve",
                adminReportHandlingCors
        );

        // 관리자 대시보드 조회
        CorsConfiguration adminDashboardReadCors =
                new CorsConfiguration(cors);

        adminDashboardReadCors.setAllowedMethods(
                List.of("GET", "OPTIONS")
        );

        source.registerCorsConfiguration(
                "/api/v1/admin/dashboard",
                adminDashboardReadCors
        );

        // 관리자 게시글·댓글 요약 조회
        CorsConfiguration adminContentSummaryReadCors =
                new CorsConfiguration(cors);

        adminContentSummaryReadCors.setAllowedMethods(
                List.of("GET", "OPTIONS")
        );

        source.registerCorsConfiguration(
                "/api/v1/admin/content/summary",
                adminContentSummaryReadCors
        );

        return source;
    }
}
