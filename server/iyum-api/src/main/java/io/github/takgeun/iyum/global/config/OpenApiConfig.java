package io.github.takgeun.iyum.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    public OpenAPI iyumOpenApi() {
        return new OpenAPI().info(new Info()
                .title("IYUM API")
                .version("v1")
                .description("IYUM 서비스의 회원 및 인증 API. 회원가입 후 PENDING 상태이며, "
                        + "이메일 인증으로 ACTIVE가 된 후 사용자가 직접 로그인합니다. "
                        + "현재 이메일 인증과 로그인은 구현 예정입니다."));
    }
}
