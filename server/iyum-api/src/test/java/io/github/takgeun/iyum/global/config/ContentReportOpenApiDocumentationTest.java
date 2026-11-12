package io.github.takgeun.iyum.global.config;

import io.github.takgeun.iyum.community.report.api.ContentReportController;
import io.github.takgeun.iyum.community.report.api.dto.ContentReportCreateRequest;
import io.github.takgeun.iyum.community.report.application.ContentReportCreateService;
import io.github.takgeun.iyum.community.report.domain.ContentReportInputPolicy;
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
@Import(ContentReportController.class)
class ContentReportOpenApiDocumentationTest extends AbstractOpenApiWebTest {

    @MockitoBean
    ContentReportCreateService reportService;

    private JsonNode document() throws Exception {
        var response = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        var document = objectMapper.readTree(response.getContentAsString());
        var path = Path.of("build/reports/content-report-openapi.json");
        Files.createDirectories(path.getParent());
        Files.writeString(path, document.toPrettyString());
        return document;
    }

    @ParameterizedTest
    @ValueSource(strings = {"/{postId}/reports", "/{postId}/comments/{commentId}/reports"})
    void generatedOperationsDescribeSessionCsrfAndTypedResponses(String suffix) throws Exception {
        var document = document();
        var operation = document.get("paths")
                .get("/api/v1/community/posts" + suffix).get("post");
        var parameters = operation.get("parameters");
        assertThat(parameters.valueStream().map(p -> p.get("name").asString()).toList())
                .containsExactlyInAnyOrderElementsOf(suffix.contains("comments")
                        ? List.of("postId", "commentId", "X-CSRF-TOKEN")
                        : List.of("postId", "X-CSRF-TOKEN"));
        for (var parameter : parameters) {
            String name = parameter.get("name").asString();
            assertThat(parameter.get("required").booleanValue()).isTrue();
            assertThat(parameter.get("in").asString())
                    .isEqualTo(name.equals("X-CSRF-TOKEN") ? "header" : "path");
            if (!name.equals("X-CSRF-TOKEN")) {
                assertThat(parameter.at("/schema/type").asString()).isEqualTo("integer");
                assertThat(parameter.at("/schema/format").asString()).isEqualTo("int64");
            }
        }
        assertThat(operation.at("/requestBody/required").booleanValue()).isTrue();
        assertThat(operation.at("/requestBody/content/application~1json/schema/$ref").asString())
                .endsWith("/ContentReportCreateRequest");
        assertThat(operation.at("/responses/201/content/application~1json/schema/$ref").asString())
                .endsWith("/ContentReportCreateResponse");
        assertThat(operation.at("/responses/201/headers/Cache-Control/schema/example").asString())
                .isEqualTo("no-store");
        assertThat(operation.at("/responses/201/headers").has("Location")).isFalse();
        var success = operation.at("/responses/201/content/application~1json/examples/received/value");
        assertThat(success.propertyNames()).containsExactlyInAnyOrder("reportId", "status", "createdAt");
        assertThat(success.get("reportId").isIntegralNumber()).isTrue();
        assertThat(success.get("status").asString()).isEqualTo("RECEIVED");
        assertThat(success.get("createdAt").asString()).isEqualTo("2026-11-12T10:00:00Z");
        assertThat(document.at("/components/securitySchemes").toString()).doesNotContainIgnoringCase("bearer");
        assertThat(document.get("paths").propertyNames())
                .noneMatch(path -> path.contains("/admin/") && path.contains("reports"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/{postId}/reports", "/{postId}/comments/{commentId}/reports"})
    void generatedErrorExamplesDistinguishParsingPolicyAndParentFailure(String suffix) throws Exception {
        var operation = document().get("paths").get("/api/v1/community/posts" + suffix).get("post");
        var expected = Map.of(
                "400", List.of("INVALID_REASON", "INVALID_REQUEST_BODY", "INVALID_REQUEST_PARAMETER"),
                "401", List.of("AUTHENTICATION_REQUIRED"),
                "403", List.of("SELF_REPORT_NOT_ALLOWED", "CSRF_TOKEN_INVALID", "ACCESS_DENIED"),
                "404", suffix.contains("comments")
                        ? List.of("POST_NOT_FOUND", "COMMENT_NOT_FOUND") : List.of("POST_NOT_FOUND"),
                "409", List.of("REPORT_ALREADY_EXISTS"),
                "415", List.of("HTTP_ERROR")
        );
        for (var entry : expected.entrySet()) {
            var content = operation.at("/responses/" + entry.getKey() + "/content/application~1json");
            assertThat(content.at("/schema/$ref").asString()).endsWith("/ApiErrorResponse");
            var examples = content.get("examples");
            assertThat(examples.propertyNames()).containsExactlyInAnyOrderElementsOf(entry.getValue());
            for (String code : entry.getValue()) {
                var value = examples.get(code).get("value");
                assertThat(value.propertyNames()).containsExactlyInAnyOrder("code", "message", "fieldErrors");
                assertThat(value.get("code").asString()).isEqualTo(code);
                assertThat(value.get("message").asString()).isNotBlank();
                assertThat(value.get("fieldErrors").isArray()).isTrue();
                assertThat(value.get("fieldErrors").isEmpty()).isTrue();
            }
        }
    }

    @Test
    void generatedDtoSchemasAndRequestExamplesMatchInputPolicy() throws Exception {
        var document = document();
        var request = document.at("/components/schemas/ContentReportCreateRequest");
        assertThat(request.get("required").valueStream().map(JsonNode::asString).toList())
                .containsExactly("reasonType");
        assertThat(request.at("/properties").propertyNames())
                .containsExactlyInAnyOrder("reasonType", "reasonDetail");
        assertThat(request.at("/properties/reasonType/enum").valueStream().map(JsonNode::asString).toList())
                .containsExactlyInAnyOrder("SPAM_ADVERTISING", "ABUSE_HATE", "PERSONAL_INFORMATION",
                        "DUPLICATE_FLOODING", "OFF_TOPIC", "OTHER");
        var detail = request.at("/properties/reasonDetail");
        assertThat(detail.get("type").valueStream().map(JsonNode::asString).toList())
                .containsExactlyInAnyOrder("string", "null");
        assertThat(detail.get("description").asString()).contains("String.strip()", "코드 포인트", "500", "NBSP");
        // The limit applies after strip(), not to the raw JSON string length.
        assertThat(detail.has("maxLength")).isFalse();
        var response = document.at("/components/schemas/ContentReportCreateResponse");
        assertThat(response.get("required").valueStream().map(JsonNode::asString).toList())
                .containsExactlyInAnyOrder("reportId", "status", "createdAt");
        assertThat(response.get("properties").propertyNames())
                .containsExactlyInAnyOrder("reportId", "status", "createdAt");
        assertThat(response.at("/properties/reportId/type").asString()).isEqualTo("integer");
        assertThat(response.at("/properties/reportId/format").asString()).isEqualTo("int64");
        assertThat(response.at("/properties/status/enum").valueStream().map(JsonNode::asString).toList())
                .containsExactly("RECEIVED");
        assertThat(response.at("/properties/createdAt/format").asString()).isEqualTo("date-time");
        for (String suffix : List.of("/{postId}/reports", "/{postId}/comments/{commentId}/reports")) {
            var examples = document.get("paths").get("/api/v1/community/posts" + suffix).get("post")
                    .at("/requestBody/content/application~1json/examples");
            assertThat(examples.propertyNames()).containsExactlyInAnyOrder("general", "other");
            for (String name : List.of("general", "other")) {
                var input = objectMapper.treeToValue(examples.get(name).get("value"), ContentReportCreateRequest.class);
                var normalized = ContentReportInputPolicy.normalizeReasonDetail(input.reasonType(), input.reasonDetail());
                if (name.equals("general")) assertThat(normalized).isNull();
                else assertThat(normalized).isNotBlank();
            }
        }
    }
}
