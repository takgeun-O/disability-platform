package io.github.takgeun.iyum.global.config;

import io.github.takgeun.iyum.auth.api.CsrfController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CsrfController.class, properties = "spring.config.import=")
@Import({SecurityConfig.class, DevCorsConfig.class})
@ActiveProfiles("test")
class CorsDisabledTest {
    @Autowired MockMvc mockMvc;

    @Test
    void localDevelopmentOriginIsNotAllowedOutsideDevProfile() throws Exception {
        mockMvc.perform(options("/api/v1/auth/signup")
                        .header(HttpHeaders.ORIGIN, "http://127.0.0.1:8443")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                // Spring 7.1은 매핑 없는 preflight를 200으로 끝낼 수 있다.
                // 허용 헤더가 없으므로 브라우저는 교차 출처 요청을 허용하지 않는다.
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }
}
