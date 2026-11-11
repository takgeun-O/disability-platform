package io.github.takgeun.iyum.global.config;

import io.github.takgeun.iyum.admin.member.api.AdminMemberModerationLogReadController;
import io.github.takgeun.iyum.admin.member.api.AdminMemberModerationController;
import io.github.takgeun.iyum.admin.member.application.AdminMemberModerationService;
import io.github.takgeun.iyum.admin.member.application.AdminMemberModerationLogReadService;
import org.junit.jupiter.api.Test;
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

@ActiveProfiles("dev")
@Import({AdminMemberModerationController.class, AdminMemberModerationLogReadController.class})
class AdminMemberModerationOpenApiDocumentationTest extends AbstractOpenApiWebTest {
    @MockitoBean AdminMemberModerationService moderationService;
    @MockitoBean AdminMemberModerationLogReadService logReadService;

    private JsonNode document() throws Exception {
        var result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var doc = objectMapper.readTree(result.getResponse().getContentAsString());
        var file = Path.of("build/reports/admin-member-moderation-openapi.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, doc.toPrettyString());
        return doc;
    }

    private JsonNode operation(JsonNode doc, String suffix, String method) {
        return doc.get("paths").get("/api/v1/admin/members/{memberId}/" + suffix).get(method);
    }

    @Test
    void generatedPostSchemasAndExamplesPreserveTypesAndPrivacy() throws Exception {
        var doc = document();
        for (String action : List.of("suspend", "lift-suspension")) {
            var op = operation(doc, action, "post");
            assertThat(
                            op.get("parameters")
                                    .valueStream()
                                    .map(p -> p.get("name").asString())
                                    .toList())
                    .containsExactly("memberId");
            assertThat(op.at("/requestBody/required").booleanValue()).isTrue();
            assertThat(op.at("/requestBody/content/application~1json/schema/$ref").asString())
                    .endsWith("/AdminMemberModerationRequest");
            for (String code : List.of("200", "400", "401", "403", "404", "409"))
                assertThat(
                                op.at(
                                                "/responses/"
                                                        + code
                                                        + "/content/application~1json/schema/$ref")
                                        .asString())
                        .endsWith(
                                code.equals("200")
                                        ? "/AdminMemberModerationResponse"
                                        : "/ApiErrorResponse");
            for (String example : List.of("changed", "unchanged")) {
                var value =
                        op.at(
                                "/responses/200/content/application~1json/examples/"
                                        + example
                                        + "/value");
                assertThat(value.get("memberId").isIntegralNumber()).isTrue();
                assertThat(value.get("changed").isBoolean()).isTrue();
                assertThat(value.get("changed").booleanValue())
                        .isEqualTo(example.equals("changed"));
                assertThat(value.get("status").asString())
                        .isEqualTo(action.equals("suspend") ? "SUSPENDED" : "ACTIVE");
            }
        }
        var schemas = doc.at("/components/schemas");
        assertThat(
                        schemas.at("/AdminMemberModerationRequest/required")
                                .valueStream()
                                .map(JsonNode::asString)
                                .toList())
                .containsExactly("reason");
        assertThat(schemas.at("/AdminMemberModerationResponse/properties/memberId/type").asString())
                .isEqualTo("integer");
        assertThat(
                        schemas.at("/AdminMemberModerationResponse/properties/memberId/format")
                                .asString())
                .isEqualTo("int64");
        assertThat(schemas.at("/AdminMemberModerationResponse/properties/changed/type").asString())
                .isEqualTo("boolean");
        assertThat(doc.at("/components/securitySchemes").toString()).doesNotContain("bearer");
    }

    @Test
    void generatedHistorySchemaHasNumericPaginationAndCompleteTypedExamples() throws Exception {
        var doc = document();
        var op = operation(doc, "moderation-logs", "get");
        assertThat(op.get("parameters").valueStream().map(p -> p.get("name").asString()).toList())
                .containsExactlyInAnyOrder("memberId", "page", "size");
        for (String name : List.of("page", "size")) {
            var p =
                    op.get("parameters")
                            .valueStream()
                            .filter(n -> n.get("name").asString().equals(name))
                            .findFirst()
                            .orElseThrow();
            assertThat(p.get("in").asString()).isEqualTo("query");
            assertThat(p.get("required").booleanValue()).isFalse();
            var schema = p.get("schema");
            assertThat(schema.get("type").asString()).isEqualTo("integer");
            assertThat(schema.get("format").asString()).isEqualTo("int32");
            assertThat(schema.get("default").isIntegralNumber()).isTrue();
            assertThat(schema.get("default").intValue()).isEqualTo(name.equals("page") ? 0 : 20);
            assertThat(schema.get("minimum").isNumber()).isTrue();
            assertThat(schema.get("minimum").intValue()).isEqualTo(name.equals("page") ? 0 : 1);
            assertThat(schema.has("maximum")).isEqualTo(name.equals("size"));
            if (name.equals("size")) {
                assertThat(schema.get("maximum").isNumber()).isTrue();
                assertThat(schema.get("maximum").intValue()).isEqualTo(50);
            }
        }
        var schema = doc.at("/components/schemas/AdminMemberModerationLogListResponse");
        assertThat(schema.at("/properties/items/items/$ref").asString())
                .endsWith("/AdminMemberModerationLogResponse");
        for (String name : List.of("page", "size", "totalPages", "totalElements")) {
            assertThat(schema.at("/properties/" + name + "/type").asString()).isEqualTo("integer");
            assertThat(schema.at("/properties/" + name + "/format").asString())
                    .isEqualTo(name.equals("totalElements") ? "int64" : "int32");
        }
        var item = doc.at("/components/schemas/AdminMemberModerationLogResponse");
        assertThat(item.get("required").valueStream().map(JsonNode::asString).toList())
                .containsExactlyInAnyOrder(
                        "logId",
                        "actorMemberId",
                        "targetMemberId",
                        "action",
                        "beforeStatus",
                        "afterStatus",
                        "reason",
                        "createdAt");
        assertThat(item.get("properties").toString())
                .doesNotContain("passwordHash", "credentialVersion", "email", "nickname");
        for (String name : List.of("beforeStatus", "afterStatus"))
            assertThat(
                            item.at("/properties/" + name + "/enum")
                                    .valueStream()
                                    .map(JsonNode::asString)
                                    .toList())
                    .containsExactlyInAnyOrder("ACTIVE", "SUSPENDED");
        for (String name : List.of("history", "empty")) {
            var value =
                    op.at("/responses/200/content/application~1json/examples/" + name + "/value");
            assertThat(value.get("totalElements").isIntegralNumber()).isTrue();
            assertThat(value.get("totalElements").intValue())
                    .isEqualTo(name.equals("history") ? 2 : 0);
            assertThat(value.get("items").size()).isEqualTo(name.equals("history") ? 2 : 0);
            assertThat(value.get("hasNext").isBoolean()).isTrue();
        }
    }
}
