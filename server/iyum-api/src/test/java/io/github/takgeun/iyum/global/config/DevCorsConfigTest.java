package io.github.takgeun.iyum.global.config;

import io.github.takgeun.iyum.auth.session.api.CsrfController;
import io.github.takgeun.iyum.auth.signup.api.SignupController;
import io.github.takgeun.iyum.auth.signup.application.SignupResult;
import io.github.takgeun.iyum.auth.signup.application.SignupService;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.community.comment.api.CommentController;
import io.github.takgeun.iyum.community.comment.application.*;
import java.util.List;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {SignupController.class, CsrfController.class, CommentController.class}, properties = "spring.config.import=")
@Import({SecurityConfig.class, DevCorsConfig.class})
@ActiveProfiles("dev")
class DevCorsConfigTest {
    private static final String ORIGIN = "http://127.0.0.1:8443";
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean SignupService signupService;
    @MockitoBean CommentReadService commentReadService;
    @MockitoBean CommentCreateService commentCreateService;
    @MockitoBean CommentUpdateService commentUpdateService;
    @MockitoBean CommentDeleteService commentDeleteService;

    @Test
    void commentCorsAndCsrfBoundaries() throws Exception {
        String collection = "/api/v1/community/posts/7/comments";
        String item = collection + "/10";
        for (String method : List.of("GET", "POST")) {
            mockMvc.perform(options(collection).header(HttpHeaders.ORIGIN, ORIGIN)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,x-csrf-token"))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "content-type, x-csrf-token"));
        }
        for (String method : List.of("PUT", "DELETE")) {
            mockMvc.perform(options(item).header(HttpHeaders.ORIGIN, ORIGIN)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,x-csrf-token"))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "PUT,DELETE,OPTIONS"));
        }
        mockMvc.perform(options(collection).header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
            .andExpect(status().isForbidden()).andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        mockMvc.perform(options(collection).header(HttpHeaders.ORIGIN, ORIGIN).header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE"))
            .andExpect(status().isForbidden());
        mockMvc.perform(options(item).header(HttpHeaders.ORIGIN, ORIGIN).header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
            .andExpect(status().isForbidden());
        when(commentReadService.getComments(7L, null, null, 20)).thenReturn(new CommentListResult(List.of(), false, null));
        mockMvc.perform(get(collection).header(HttpHeaders.ORIGIN, ORIGIN))
            .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN));
        mockMvc.perform(post(collection).with(user("test-only")).header(HttpHeaders.ORIGIN, ORIGIN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"test\"}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
        mockMvc.perform(put(item).with(user("test-only")).header(HttpHeaders.ORIGIN, ORIGIN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"test\"}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
        mockMvc.perform(delete(item).with(user("test-only")).header(HttpHeaders.ORIGIN, ORIGIN))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
        verifyNoInteractions(commentCreateService, commentUpdateService, commentDeleteService);
    }


    @Test
    void signupPreflightAllowsOnlyConfiguredOriginWithCredentialsAndRequiredHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/auth/signup")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,x-csrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,OPTIONS"));
        verifyNoInteractions(signupService);
    }

    @Test
    void browserCanGetTokenAndSignupWithSameSessionWhileCsrfIsStillRequired() throws Exception {
        when(signupService.signup(any())).thenReturn(new SignupResult(1L, MemberStatus.PENDING));
        var result = mockMvc.perform(get("/api/v1/auth/csrf").header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andReturn();
        String token = objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asString();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        String request = """
                {"email":"cors@example.com","password":"Example123!","passwordConfirm":"Example123!",
                 "nickname":"CorsUser","agreements":[
                   {"termsCode":"SERVICE_TERMS","version":"dev-v1","agreed":true},
                   {"termsCode":"PRIVACY_COLLECTION_USE","version":"dev-v1","agreed":true}]}
                """;
        mockMvc.perform(post("/api/v1/auth/signup").session(session)
                        .header(HttpHeaders.ORIGIN, ORIGIN).header("X-CSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(jsonPath("$.status").value("PENDING"));
        mockMvc.perform(post("/api/v1/auth/signup").header(HttpHeaders.ORIGIN, ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8443", "http://127.0.0.1:3000", "https://evil.example"})
    void rejectsOtherOrigins(String origin) throws Exception {
        mockMvc.perform(options("/api/v1/auth/signup")
                        .header(HttpHeaders.ORIGIN, origin)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        verifyNoInteractions(signupService);
    }

    @Test
    void doesNotOpenUnrelatedApiPathsOrMethods() throws Exception {
        mockMvc.perform(options("/api/v1/members/me")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        mockMvc.perform(options("/api/v1/auth/signup")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE"))
                .andExpect(status().isForbidden());
    }
}
