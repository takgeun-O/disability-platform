package io.github.takgeun.iyum.global.config;

import io.github.takgeun.iyum.admin.member.api.AdminMemberReadController;
import io.github.takgeun.iyum.admin.member.application.AdminMemberReadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tests the real springdoc document, not the mocked member service's business rules. */
@ActiveProfiles("dev")
@Import(AdminMemberReadController.class)
class AdminMemberOpenApiDocumentationTest extends AbstractOpenApiWebTest {
    @MockitoBean AdminMemberReadService memberReadService;

    private JsonNode document() throws Exception {
        var response = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse();
        JsonNode document = objectMapper.readTree(response.getContentAsString());
        Path evidence = Path.of("build/reports/admin-member-openapi.json");
        Files.createDirectories(evidence.getParent());
        Files.writeString(evidence, document.toPrettyString());
        return document;
    }

    private JsonNode operation(JsonNode document, boolean detail) {
        return document.at(detail ? "/paths/~1api~1v1~1admin~1members~1{memberId}/get"
                : "/paths/~1api~1v1~1admin~1members/get");
    }

    private JsonNode parameter(JsonNode operation, String name) {
        return operation.get("parameters").valueStream()
                .filter(p -> p.get("name").asString().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void parametersMatchBindingAndDoNotExposePrincipalOrBearer() throws Exception {
        JsonNode doc = document(), list = operation(doc, false), detail = operation(doc, true);
        assertThat(doc.get("openapi").asString()).startsWith("3.1.");
        assertThat(list.get("parameters").valueStream().map(p -> p.get("name").asString()).toList())
                .containsExactlyInAnyOrder("searchType", "keyword", "status", "role", "sort", "page", "size");
        assertThat(detail.get("parameters").valueStream().map(p -> p.get("name").asString()).toList())
                .containsExactly("memberId");
        assertThat(parameter(detail, "memberId").get("in").asString()).isEqualTo("path");
        assertThat(parameter(detail, "memberId").get("required").asBoolean()).isTrue();
        assertThat(parameter(list, "searchType").at("/schema/enum").valueStream().map(JsonNode::asString).toList())
                .containsExactly("MEMBER_ID", "NICKNAME", "EMAIL");
        assertThat(parameter(list, "status").at("/schema/enum").valueStream().map(JsonNode::asString).toList())
                .containsExactly("PENDING", "ACTIVE", "RESTRICTED", "SUSPENDED", "WITHDRAWN", "DELETED");
        assertThat(parameter(list, "role").at("/schema/enum").valueStream().map(JsonNode::asString).toList())
                .containsExactly("USER", "ADMIN");
        assertThat(parameter(list, "sort").at("/schema/default").asString()).isEqualTo("LATEST");
        assertThat(list.get("description").asString()).contains("세션", "CSRF", "100", "254");
        assertThat(doc.at("/components/securitySchemes").toString()).doesNotContain("bearer");
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "size"})
    void paginationUsesOptionalIntegerQueryWithNumericDefaultsAndBounds(String name) throws Exception {
        JsonNode parameter = parameter(operation(document(), false), name);
        assertThat(parameter.get("in").asString()).isEqualTo("query");
        assertThat(parameter.has("required")).isTrue();
        assertThat(parameter.get("required").isBoolean()).isTrue();
        assertThat(parameter.get("required").booleanValue()).isFalse();
        JsonNode schema = parameter.get("schema");
        assertThat(schema.get("type").asString()).isEqualTo("integer");
        assertThat(schema.get("format").asString()).isEqualTo("int32");
        assertThat(schema.has("default")).isTrue();
        assertThat(schema.get("default").isIntegralNumber()).isTrue();
        assertThat(schema.get("default").intValue()).isEqualTo(name.equals("page") ? 0 : 20);
        assertThat(schema.has("minimum")).isTrue();
        assertThat(schema.get("minimum").isNumber()).isTrue();
        assertThat(schema.get("minimum").decimalValue())
                .isEqualByComparingTo(BigDecimal.valueOf(name.equals("page") ? 0 : 1));
        if (name.equals("page")) {
            assertThat(schema.has("maximum")).isFalse();
        } else {
            assertThat(schema.has("maximum")).isTrue();
            assertThat(schema.get("maximum").isNumber()).isTrue();
            assertThat(schema.get("maximum").decimalValue()).isEqualByComparingTo(BigDecimal.valueOf(50));
        }
    }

    @Test
    void schemasKeepRequiredNullableFieldsAndTypedPageItems() throws Exception {
        JsonNode schemas = document().at("/components/schemas");
        JsonNode item = schemas.get("AdminMemberItemResponse"), detail = schemas.get("AdminMemberDetailResponse");
        List<String> common = List.of("memberId", "nickname", "email", "status", "role", "createdAt");
        assertThat(item.get("properties").propertyNames()).containsExactlyInAnyOrderElementsOf(common);
        assertThat(item.get("required").valueStream().map(JsonNode::asString).toList()).containsExactlyInAnyOrderElementsOf(common);
        for (JsonNode schema : List.of(item, detail)) {
            assertThat(schema.at("/properties/email/type").valueStream().map(JsonNode::asString).toList())
                    .containsExactlyInAnyOrder("string", "null");
            assertThat(schema.get("properties").propertyNames()).doesNotContain("password", "passwordHash", "credentialVersion");
            assertThat(schema.at("/properties/createdAt/format").asString()).isEqualTo("date-time");
        }
        assertThat(detail.get("required").valueStream().map(JsonNode::asString).toList())
                .containsExactlyInAnyOrder("memberId", "nickname", "email", "status", "role", "createdAt", "updatedAt", "withdrawnAt");
        assertThat(detail.at("/properties/withdrawnAt/type").valueStream().map(JsonNode::asString).toList())
                .containsExactlyInAnyOrder("string", "null");
        assertThat(detail.at("/properties/withdrawnAt/format").asString()).isEqualTo("date-time");
        JsonNode page = schemas.get("AdminMemberListResponse");
        assertThat(page.at("/properties/items/items/$ref").asString()).isEqualTo("#/components/schemas/AdminMemberItemResponse");
        assertThat(page.get("required").valueStream().map(JsonNode::asString).toList())
                .containsExactlyInAnyOrder("items", "page", "size", "totalElements", "totalPages", "hasNext", "hasPrevious");
    }

    @Test
    void generatedExamplesAndResponseSchemasMatchReadOnlyContract() throws Exception {
        JsonNode doc = document();
        for (boolean isDetail : List.of(false, true)) {
            JsonNode op = operation(doc, isDetail), responses = op.get("responses");
            assertThat(responses.at("/200/content/application~1json/schema/$ref").asString())
                    .isEqualTo("#/components/schemas/" + (isDetail ? "AdminMemberDetailResponse" : "AdminMemberListResponse"));
            assertThat(responses.at("/200/headers/Cache-Control/schema/example").asString()).isEqualTo("no-store");
            for (String status : isDetail ? List.of("400", "401", "403", "404") : List.of("400", "401", "403")) {
                JsonNode media = responses.at("/" + status + "/content/application~1json");
                assertThat(media.at("/schema/$ref").asString()).isEqualTo("#/components/schemas/ApiErrorResponse");
                assertThat(media.get("examples").size()).isPositive();
                for (JsonNode example : media.get("examples")) {
                    JsonNode value = example.get("value");
                    assertThat(value.propertyNames()).containsExactlyInAnyOrder("code", "message", "fieldErrors");
                    assertThat(value.get("fieldErrors").isArray()).isTrue();
                    assertThat(value.get("fieldErrors").size()).isZero();
                }
            }
        }
        JsonNode examples = operation(doc, false).at("/responses/200/content/application~1json/examples");
        assertThat(examples.at("/members/value/items/0/email").asString()).isEqualTo("member@example.com");
        assertThat(examples.at("/members/value/totalElements").asInt()).isEqualTo(1);
        assertThat(examples.at("/members/value/totalPages").asInt()).isEqualTo(1);
        assertThat(examples.at("/members/value/hasNext").asBoolean()).isFalse();
        assertThat(examples.at("/empty/value/items").size()).isZero();
        assertThat(examples.at("/empty/value/totalElements").asInt()).isZero();
        assertThat(examples.at("/empty/value/totalPages").asInt()).isZero();
        JsonNode details = operation(doc, true).at("/responses/200/content/application~1json/examples");
        assertThat(details.at("/member/value").has("withdrawnAt")).isTrue();
        assertThat(details.at("/member/value/withdrawnAt").isNull()).isTrue();
        assertThat(details.at("/deleted/value").has("email")).isTrue();
        assertThat(details.at("/deleted/value/email").isNull()).isTrue();
        assertThat(details.at("/deleted/value/nickname").asString()).isEqualTo("탈퇴한 회원");
        assertThat(details.at("/deleted/value/status").asString()).isEqualTo("DELETED");
    }
}
