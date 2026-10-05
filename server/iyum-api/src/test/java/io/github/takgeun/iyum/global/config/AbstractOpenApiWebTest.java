package io.github.takgeun.iyum.global.config;

import io.github.takgeun.iyum.auth.api.CsrfController;
import io.github.takgeun.iyum.auth.api.SignupController;
import io.github.takgeun.iyum.auth.application.SignupService;
import io.github.takgeun.iyum.global.error.GlobalExceptionHandler;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.configuration.SpringDocSecurityConfiguration;
import org.springdoc.core.configuration.SpringDocSpecPropertiesConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

// MVC 슬라이스에 실제 springdoc 자동 설정을 추가한다. DB나 보안 필터 제외는 필요하지 않다.
@WebMvcTest(controllers = {SignupController.class, CsrfController.class}, properties = "spring.config.import=")
@Import({SecurityConfig.class, OpenApiConfig.class, GlobalExceptionHandler.class})
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class,
        SpringDocSecurityConfiguration.class, SpringDocSpecPropertiesConfiguration.class,
        SpringDocWebMvcConfiguration.class, SwaggerConfig.class,
        SwaggerUiConfigProperties.class, SwaggerUiOAuthProperties.class})
abstract class AbstractOpenApiWebTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean SignupService signupService;
}
