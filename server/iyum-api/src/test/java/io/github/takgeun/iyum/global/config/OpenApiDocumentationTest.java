package io.github.takgeun.iyum.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
class OpenApiDocumentationTest extends AbstractOpenApiWebTest {

    @Test
    void generatedDocumentDescribesActualSignupContract() throws Exception {
        var result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("IYUM API"))
                .andExpect(jsonPath("$.info.version").value("v1")).andReturn();
        JsonNode document = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode signup = document.at("/paths/~1api~1v1~1auth~1signup/post");
        assertThat(signup.isMissingNode()).isFalse();
        assertThat(signup.at("/requestBody/required").asBoolean()).isTrue();
        assertThat(signup.at("/requestBody/content/application~1json/schema/$ref").asString())
                .isEqualTo("#/components/schemas/SignupRequest");
        assertThat(signup.at("/responses/201/content/application~1json/schema/$ref").asString())
                .isEqualTo("#/components/schemas/SignupResponse");
        assertThat(signup.at("/responses/201/content/application~1json/example/status").asString()).isEqualTo("PENDING");
        for (String status : new String[]{"400", "409", "403", "500"}) {
            JsonNode response = signup.at("/responses/" + status + "/content/application~1json");
            assertThat(response.at("/schema/$ref").asString()).isEqualTo("#/components/schemas/ApiErrorResponse");
        }
        assertThat(signup.at("/responses/400/content/application~1json/examples").propertyNames())
                .contains("validation", "invalidBody", "missingTerms", "notAccepted", "termsVersion", "duplicateTerms");
        assertThat(signup.at("/responses/409/content/application~1json/examples").propertyNames())
                .contains("email", "nickname");
        assertThat(signup.get("parameters").valueStream().anyMatch(parameter ->
                parameter.get("name").asString().equals("X-CSRF-TOKEN")
                        && parameter.get("in").asString().equals("header"))).isTrue();

        JsonNode schemas = document.at("/components/schemas");
        JsonNode request = schemas.get("SignupRequest");
        assertThat(request.get("required").valueStream().map(JsonNode::asString).toList())
                .containsExactlyInAnyOrder("email", "password", "passwordConfirm", "nickname", "agreements");
        JsonNode fields = request.get("properties");
        assertThat(fields.get("password").get("writeOnly").asBoolean()).isTrue();
        assertThat(fields.get("passwordConfirm").get("writeOnly").asBoolean()).isTrue();
        assertThat(fields.get("password").get("description").asString()).contains("UTF-8", "72바이트", "8자");
        assertThat(fields.get("nickname").get("description").asString()).contains("중복", "2~20자");
        assertThat(fields.at("/agreements/items/$ref").asString()).isEqualTo("#/components/schemas/AgreementRequest");
        JsonNode agreement = schemas.get("AgreementRequest").get("properties");
        assertThat(agreement.at("/termsCode/enum").valueStream().map(JsonNode::asString).toList())
                .containsExactlyInAnyOrder("SERVICE_TERMS", "PRIVACY_COLLECTION_USE");
        assertThat(agreement.at("/version/description").asString()).contains("개발용", "dev-v1");
        assertThat(schemas.at("/SignupResponse/properties").propertyNames()).containsExactly("status");
        assertThat(schemas.at("/SignupResponse/properties/status/enum").valueStream().map(JsonNode::asString).toList())
                .containsExactly("PENDING");
        assertThat(schemas.at("/ApiErrorResponse/properties").propertyNames())
                .containsExactlyInAnyOrder("code", "message", "fieldErrors");
        assertThat(schemas.at("/ApiFieldError/properties").propertyNames()).containsExactlyInAnyOrder("field", "message");
    }

    @Test
    void swaggerUiAndConfigurationAreAccessibleAnonymouslyInDev() throws Exception {
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", endsWith("/swagger-ui/index.html")));
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"));
    }
}
