package io.github.takgeun.iyum.global.config;

import io.github.takgeun.iyum.admin.report.api.AdminReportReadController;
import io.github.takgeun.iyum.admin.report.application.AdminReportReadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("dev")
@Import(AdminReportReadController.class)
class AdminReportOpenApiDocumentationTest extends AbstractOpenApiWebTest {

    @MockitoBean
    AdminReportReadService reportService;

    private JsonNode document() throws Exception {
        var response = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse();
        var document = objectMapper.readTree(response.getContentAsString());
        var path = Path.of("build/reports/admin-report-openapi.json");
        Files.createDirectories(path.getParent());
        Files.writeString(path, document.toPrettyString());
        return document;
    }

    @Test
    void queryParametersPreserveEnumsNumericDefaultsAndSessionContract() throws Exception {
        var doc = document();
        var operation = doc.get("paths").get("/api/v1/admin/reports").get("get");
        var params = operation.get("parameters");
        assertThat(params.valueStream().map(p -> p.get("name").asString()).toList())
                .containsExactlyInAnyOrder("targetType", "status", "reasonType", "startDate",
                        "endDate", "sort", "page", "size");
        for (var p : params) {
            assertThat(p.get("in").asString()).isEqualTo("query");
            assertThat(p.get("required").booleanValue()).isFalse();
            String name = p.get("name").asString();
            var schema = p.get("schema");
            if (name.equals("page") || name.equals("size")) {
                assertThat(schema.get("type").asString()).isEqualTo("integer");
                assertThat(schema.get("format").asString()).isEqualTo("int32");
                assertThat(schema.get("default").isIntegralNumber()).isTrue();
                assertThat(schema.get("default").intValue()).isEqualTo(name.equals("page") ? 0 : 20);
                assertThat(schema.get("minimum").isNumber()).isTrue();
                assertThat(schema.get("minimum").intValue()).isEqualTo(name.equals("page") ? 0 : 1);
                if (name.equals("size")) {
                    assertThat(schema.get("maximum").isNumber()).isTrue();
                    assertThat(schema.get("maximum").intValue()).isEqualTo(50);
                } else assertThat(schema.has("maximum")).isFalse();
            }
            var enums = Map.of(
                    "targetType", List.of("ALL", "POST", "COMMENT"),
                    "status", List.of("RECEIVED", "RESOLVED", "DISMISSED"),
                    "reasonType", List.of("SPAM_ADVERTISING", "ABUSE_HATE", "PERSONAL_INFORMATION",
                            "DUPLICATE_FLOODING", "OFF_TOPIC", "OTHER"),
                    "sort", List.of("LATEST", "OLDEST")
            );
            if (enums.containsKey(name)) {
                assertThat(schema.get("enum").valueStream().map(JsonNode::asString).toList())
                        .containsExactlyInAnyOrderElementsOf(enums.get(name));
            }
        }
        var detailParams = doc.get("paths").get("/api/v1/admin/reports/{reportId}")
                .get("get").get("parameters");
        assertThat(detailParams.size()).isEqualTo(1);
        assertThat(detailParams.get(0).get("name").asString()).isEqualTo("reportId");
        assertThat(detailParams.get(0).at("/schema/type").asString()).isEqualTo("integer");
        assertThat(detailParams.get(0).at("/schema/format").asString()).isEqualTo("int64");
        assertThat(doc.at("/components/securitySchemes").toString()).doesNotContainIgnoringCase("bearer");
    }

    @Test
    void responseSchemasDistinguishRequiredPresenceAndNullableValues() throws Exception {
        var schemas = document().at("/components/schemas");
        var expected = Map.of(
                "AdminReportItemResponse", List.of("reportId", "targetType", "targetId", "status", "reasonType",
                        "titleSnapshot", "contentSnapshotPreview", "createdAt", "currentAuthor"),
                "AdminReportDetailResponse", List.of("reportId", "reporterMemberId", "targetType", "targetId",
                        "status", "reasonType", "reasonDetail", "titleSnapshot", "contentSnapshot", "createdAt", "currentTarget"),
                "AdminReportCurrentTargetResponse", List.of("targetId", "parentPostId", "author", "title", "content", "hiddenAt", "deletedAt"),
                "AdminReportTargetAuthorResponse", List.of("memberId", "nickname", "withdrawn"),
                "AdminReportListResponse", List.of("items", "page", "size", "totalElements", "totalPages", "hasNext", "hasPrevious")
        );
        for (var e : expected.entrySet()) {
            assertThat(schemas.get(e.getKey()).get("properties").propertyNames())
                    .containsExactlyInAnyOrderElementsOf(e.getValue());
            assertThat(schemas.get(e.getKey()).get("required").valueStream().map(JsonNode::asString).toList())
                    .containsExactlyInAnyOrderElementsOf(e.getValue());
        }
        assertThat(schemas.at("/AdminReportListResponse/properties/items/items/$ref").asString())
                .endsWith("/AdminReportItemResponse");
        for (String name : List.of("AdminReportItemResponse", "AdminReportDetailResponse")) {
            assertThat(schemas.get(name).at("/properties/targetType/enum").valueStream().map(JsonNode::asString).toList())
                    .containsExactlyInAnyOrder("POST", "COMMENT");
        }
        for (String path : List.of("AdminReportItemResponse/properties/titleSnapshot",
                "AdminReportDetailResponse/properties/reasonDetail",
                "AdminReportDetailResponse/properties/titleSnapshot",
                "AdminReportCurrentTargetResponse/properties/parentPostId", "AdminReportCurrentTargetResponse/properties/title",
                "AdminReportCurrentTargetResponse/properties/hiddenAt", "AdminReportCurrentTargetResponse/properties/deletedAt")) {
            assertThat(schemas.at("/" + path + "/type").valueStream().map(JsonNode::asString).toList()).contains("null");
        }
    }

    @Test
    void generatedExamplesPreserveSnapshotsNullsAndPagination() throws Exception {
        var doc = document();
        var paths = doc.get("paths");
        for (String path : List.of("/api/v1/admin/reports", "/api/v1/admin/reports/{reportId}")) {
            var responses = paths.get(path).get("get").get("responses");
            assertThat(responses.at("/200/headers/Cache-Control/schema/example").asString()).isEqualTo("no-store");
            for (String code : List.of("400", "401", "403")) {
                assertThat(responses.at("/" + code + "/content/application~1json/schema/$ref").asString())
                        .endsWith("/ApiErrorResponse");
                for (var example : responses.at("/" + code + "/content/application~1json/examples")) {
                    var value = example.get("value");
                    assertThat(value.isObject()).isTrue();
                    assertThat(value.propertyNames()).containsExactlyInAnyOrder("code", "message", "fieldErrors");
                    assertThat(value.get("fieldErrors").isArray()).isTrue();
                }
            }
        }
        var lists = paths.get("/api/v1/admin/reports").get("get")
                .at("/responses/200/content/application~1json/examples");
        assertThat(lists.propertyNames()).containsExactlyInAnyOrder("mixed", "empty", "outOfRange", "missingCurrentAuthor");
        for (var example : lists) {
            var v = example.get("value");
            assertThat(v.isObject()).isTrue();
            int total = v.get("totalElements").intValue();
            assertThat(v.get("totalPages").intValue()).isEqualTo((total + 19) / 20);
        }
        assertThat(lists.at("/mixed/value/items/1/titleSnapshot").isNull()).isTrue();
        assertThat(lists.at("/mixed/value/items/1/currentAuthor/nickname").asString()).isEqualTo("탈퇴한 회원");
        assertThat(lists.at("/outOfRange/value/totalElements").intValue()).isEqualTo(21);
        assertThat(lists.at("/outOfRange/value/items").isEmpty()).isTrue();
        var details = paths.get("/api/v1/admin/reports/{reportId}").get("get")
                .at("/responses/200/content/application~1json/examples");
        assertThat(details.propertyNames()).containsExactlyInAnyOrder("post", "comment", "missingTarget");
        assertThat(details.at("/post/value/currentTarget/parentPostId").isNull()).isTrue();
        assertThat(details.at("/comment/value/currentTarget/title").isNull()).isTrue();
        assertThat(details.at("/comment/value/currentTarget/parentPostId").intValue()).isEqualTo(104);
        assertThat(details.at("/missingTarget/value/currentTarget").isNull()).isTrue();
        assertThat(details.at("/missingTarget/value/contentSnapshot").asString()).isNotBlank();
        assertThat(details.at("/post/value/reporterMemberId").intValue())
                .isNotEqualTo(details.at("/post/value/currentTarget/author/memberId").intValue());
    }
    @ParameterizedTest
    @ValueSource(strings = {"list", "detail"})
    void resolvedResponseSchemasAcceptNullAndRejectInvalidObjects(String kind) throws Exception {
        var doc = document();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var root = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(doc.toString());
        var factory = com.networknt.schema.JsonSchemaFactory.getInstance(
                com.networknt.schema.SpecVersion.VersionFlag.V202012
        );

        boolean list = kind.equals("list");
        String path = list ? "/api/v1/admin/reports" : "/api/v1/admin/reports/{reportId}";
        var content = root.path("paths").path(path).path("get")
                .path("responses").path("200").path("content").path("application/json");
        // All references are local to the generated document. No remote schema retrieval.
        var schemaDocument = mapper.createObjectNode();
        schemaDocument.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        schemaDocument.set("components", root.path("components"));
        schemaDocument.set("$ref", content.path("schema").path("$ref"));
        assertThat(root.findValues("$ref"))
                .allSatisfy(reference -> assertThat(reference.asText()).startsWith("#/"));
        var schema = factory.getSchema(schemaDocument);
        for (var example : content.path("examples")) {
            assertThat(schema.validate(example.path("value")))
                    .as("%s success example", kind).isEmpty();
        }
        String exampleName = list ? "mixed" : "post";
        var valid = content.path("examples").path(exampleName).path("value");
        assertThat(schema.validate(valid)).isEmpty();
        String field = list ? "currentAuthor" : "currentTarget";
        var nullable = valid.deepCopy();
        var holder = (com.fasterxml.jackson.databind.node.ObjectNode)
                (list ? nullable.path("items").get(0) : nullable);
        holder.putNull(field);
        assertThat(schema.validate(nullable)).as("%s accepts explicit null", field).isEmpty();
        holder.remove(field);
        assertThat(schema.validate(nullable)).as("%s remains required", field).isNotEmpty();
        holder.put(field, "invalid");
        assertThat(schema.validate(nullable)).isNotEmpty();
        holder.put(field, 42);
        assertThat(schema.validate(nullable)).isNotEmpty();
        var original = (com.fasterxml.jackson.databind.node.ObjectNode)
                (list ? valid.path("items").get(0).path(field) : valid.path(field));
        var invalidObject = original.deepCopy();
        invalidObject.remove(list ? "memberId" : "content");
        holder.set(field, invalidObject);
        assertThat(schema.validate(nullable)).as("referenced required field").isNotEmpty();
        invalidObject = original.deepCopy();
        if (list) {
            invalidObject.put("memberId", "not-an-id");
        } else {
            invalidObject.put("content", 42);
        }
        holder.set(field, invalidObject);
        assertThat(schema.validate(nullable)).as("referenced property type").isNotEmpty();

        // The shared component itself must not become nullable for other references.
        schemaDocument.put("$ref", "#/components/schemas/" + (list
                ? "AdminReportTargetAuthorResponse" : "AdminReportCurrentTargetResponse"));
        var sharedSchema = factory.getSchema(schemaDocument);
        assertThat(sharedSchema.validate(mapper.nullNode())).isNotEmpty();
        assertThat(sharedSchema.validate(original)).isEmpty();
    }

}
