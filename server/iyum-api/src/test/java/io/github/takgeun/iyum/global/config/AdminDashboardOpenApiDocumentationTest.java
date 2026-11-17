package io.github.takgeun.iyum.global.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import io.github.takgeun.iyum.admin.dashboard.api.AdminDashboardReadController;
import io.github.takgeun.iyum.admin.dashboard.application.AdminDashboardReadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("dev")
@Import(AdminDashboardReadController.class)
class AdminDashboardOpenApiDocumentationTest
        extends AbstractOpenApiWebTest {

    private static final String API_PATH =
            "/api/v1/admin/dashboard";

    /*
     * 현재 JSON Schema 검증 라이브러리는 Jackson 2를 사용한다.
     * 테스트에서 생성 문서와 예시를 검증할 때만 사용한다.
     */
    private static final ObjectMapper SCHEMA_MAPPER =
            new ObjectMapper();

    @MockitoBean
    private AdminDashboardReadService dashboardReadService;

    @Test
    void 조회_파라미터와_날짜_형식과_캐시_정책을_문서화한다()
            throws Exception {
        JsonNode document = document();
        JsonNode operation = operation(document);

        assertThat(operation.path("parameters").size())
                .isZero();

        assertThat(operation.has("requestBody"))
                .isFalse();

        for(String responseCode : List.of("200", "401", "403")) {
            JsonNode response = operation.path("responses")
                    .path(responseCode);

            assertThat(response.isObject())
                    .as("%s 응답이 문서에 존재해야 한다", responseCode)
                    .isTrue();

            assertThat(
                    response.at(
                            "/headers/Cache-Control/schema/example"
                    ).asText()
            ).isEqualTo("no-store");
        }

        JsonNode properties = document
                .at("/components/schemas/AdminDashboardResponse/properties");

        assertThat(properties.at("/queriedAt/type").asText())
                .isEqualTo("string");

        assertThat(properties.at("/queriedAt/format").asText())
                .isEqualTo("date-time");

        assertThat(properties.at("/baseDate/type").asText())
                .isEqualTo("string");

        assertThat(properties.at("/baseDate/format").asText())
                .isEqualTo("date");

        JsonNode createdAt = document.at(
                "/components/schemas/"
                        + "AdminDashboardPendingReportResponse/"
                        + "properties/createdAt"
        );

        assertThat(createdAt.path("type").asText())
                .isEqualTo("string");

        assertThat(createdAt.path("format").asText())
                .isEqualTo("date-time");
    }

    @ParameterizedTest
    @ValueSource(strings = {"200", "401", "403"})
    void 모든_응답_예시가_생성된_스키마를_만족한다(
            String responseCode
    ) throws Exception {
        JsonNode document = document();
        JsonNode content = responseContent(
                document,
                responseCode
        );

        JsonNode examples = content.path("examples");

        assertThat(examples.isObject()).isTrue();
        assertThat(examples.size()).isGreaterThan(0);

        JsonSchema schema = responseSchema(
                document,
                responseCode
        );

        var fields = examples.fields();

        while(fields.hasNext()) {
            var example = fields.next();
            JsonNode value = example.getValue().path("value");

            assertThat(value.isObject())
                    .as("%s 예시는 JSON 객체여야 한다", example.getKey())
                    .isTrue();

            assertThat(schema.validate(value))
                    .as(
                            "%s 응답의 %s 예시",
                            responseCode,
                            example.getKey()
                    )
                    .isEmpty();
        }
    }

    @Test
    void 빈_성공_예시는_0과_빈_배열을_반환한다()
            throws Exception {
        JsonNode document = document();
        ObjectNode empty = successExample(
                document,
                "empty"
        );

        assertThat(
                responseSchema(document, "200").validate(empty)
        ).isEmpty();

        for(String field : List.of(
                "pendingReportCount",
                "todayReportCount",
                "todayPostCount",
                "todayMemberCount"
        )) {
            JsonNode value = empty.path("counts").path(field);

            assertThat(value.isIntegralNumber()).isTrue();
            assertThat(value.longValue()).isZero();
        }

        assertThat(empty.path("pendingReports").isArray())
                .isTrue();

        assertThat(empty.path("pendingReports").size())
                .isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "ROOT, queriedAt",
            "ROOT, baseDate",
            "ROOT, counts",
            "ROOT, pendingReports",
            "COUNTS, pendingReportCount",
            "COUNTS, todayReportCount",
            "COUNTS, todayPostCount",
            "COUNTS, todayMemberCount",
            "ITEM, reportId",
            "ITEM, targetType",
            "ITEM, targetId",
            "ITEM, reasonType",
            "ITEM, createdAt"
    })
    void 필수_응답_필드의_누락과_null을_거부한다(
            String location,
            String field
    ) throws Exception {
        JsonNode document = document();
        JsonSchema schema = responseSchema(document, "200");
        ObjectNode original = successExample(document, "mixed");

        assertThat(schema.validate(original)).isEmpty();

        ObjectNode missing = original.deepCopy();
        holder(missing, location).remove(field);

        assertThat(schema.validate(missing))
                .as("%s.%s 누락은 허용하지 않는다", location, field)
                .isNotEmpty();

        ObjectNode explicitNull = original.deepCopy();
        holder(explicitNull, location).putNull(field);

        assertThat(schema.validate(explicitNull))
                .as("%s.%s의 null은 허용하지 않는다", location, field)
                .isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "pendingReportCount",
            "todayReportCount",
            "todayPostCount",
            "todayMemberCount"
    })
    void 집계는_0_이상의_정수만_허용한다(
            String field
    ) throws Exception {
        JsonNode document = document();

        JsonNode property = document
                .at("/components/schemas/AdminDashboardCountsResponse/properties")
                .path(field);

        assertThat(property.path("type").asText())
                .isEqualTo("integer");
        assertThat(property.path("format").asText())
                .isEqualTo("int64");
        assertThat(property.path("minimum").isNumber())
                .isTrue();
        assertThat(property.path("minimum").decimalValue())
                .isEqualByComparingTo("0");

        JsonSchema schema = responseSchema(document, "200");
        ObjectNode original = successExample(document, "mixed");

        assertThat(schema.validate(original)).isEmpty();

        for(long validValue : new long[]{
                0L,
                1L,
                3_000_000_000L
        }) {
            ObjectNode modified = original.deepCopy();

            holder(modified, "COUNTS").put(
                    field,
                    validValue
            );

            assertThat(schema.validate(modified))
                    .as("%s=%s는 허용한다", field, validValue)
                    .isEmpty();
        }

        for(String invalidJson : List.of(
                "-1",
                "0.5",
                "\"1\"",
                "true"
        )) {
            ObjectNode modified = original.deepCopy();

            holder(modified, "COUNTS").set(
                    field,
                    SCHEMA_MAPPER.readTree(invalidJson)
            );

            assertThat(schema.validate(modified))
                    .as("%s=%s는 거부한다", field, invalidJson)
                    .isNotEmpty();
        }
    }

    @Test
    void 신고_대상_종류는_POST와_COMMENT만_허용한다()
            throws Exception {
        JsonNode document = document();
        JsonSchema schema = responseSchema(document, "200");
        ObjectNode original = successExample(document, "mixed");

        assertThat(schema.validate(original)).isEmpty();

        for(String targetType : List.of("POST", "COMMENT")) {
            ObjectNode modified = original.deepCopy();

            holder(modified, "ITEM").put(
                    "targetType",
                    targetType
            );

            assertThat(schema.validate(modified))
                    .as("%s는 허용한다", targetType)
                    .isEmpty();
        }

        for(String invalidJson : List.of(
                "\"ALL\"",
                "\"UNKNOWN\"",
                "\"post\"",
                "\"\"",
                "1",
                "true"
        )) {
            ObjectNode modified = original.deepCopy();

            holder(modified, "ITEM").set(
                    "targetType",
                    SCHEMA_MAPPER.readTree(invalidJson)
            );

            assertThat(schema.validate(modified))
                    .as("%s는 거부한다", invalidJson)
                    .isNotEmpty();
        }
    }

    private JsonNode document() throws Exception {
        String json = mockMvc.perform(
                        get("/v3/api-docs")
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = SCHEMA_MAPPER.readTree(json);

        Path outputPath = Path.of(
                "build/reports/admin-dashboard-openapi.json"
        );

        Files.createDirectories(outputPath.getParent());

        Files.writeString(
                outputPath,
                document.toPrettyString()
        );

        return document;
    }

    private JsonNode operation(JsonNode document) {
        JsonNode operation = document.path("paths")
                .path(API_PATH)
                .path("get");

        assertThat(operation.isObject())
                .as("대시보드 GET API가 문서에 존재해야 한다")
                .isTrue();

        return operation;
    }

    private JsonNode responseContent(
            JsonNode document,
            String responseCode
    ) {
        JsonNode content = operation(document)
                .path("responses")
                .path(responseCode)
                .path("content")
                .path("application/json");

        assertThat(content.isObject())
                .as("%s JSON 응답이 존재해야 한다", responseCode)
                .isTrue();

        return content;
    }

    private JsonSchema responseSchema(
            JsonNode document,
            String responseCode
    ) {
        JsonNode responseSchema = responseContent(
                document,
                responseCode
        ).path("schema");

        assertThat(responseSchema.isObject()).isTrue();

        String reference = responseSchema.path("$ref").asText();

        assertThat(reference)
                .startsWith("#/components/schemas/");

        assertThat(document.findValues("$ref"))
                .allSatisfy(localReference ->
                        assertThat(localReference.asText())
                                .startsWith("#/")
                );

        ObjectNode schemaDocument =
                SCHEMA_MAPPER.createObjectNode();

        schemaDocument.put(
                "$schema",
                "https://json-schema.org/draft/2020-12/schema"
        );

        schemaDocument.set(
                "components",
                document.path("components")
        );

        schemaDocument.put(
                "$ref",
                reference
        );

        return JsonSchemaFactory.getInstance(
                SpecVersion.VersionFlag.V202012
        ).getSchema(schemaDocument);
    }

    private ObjectNode successExample(
            JsonNode document,
            String name
    ) {
        JsonNode value = responseContent(document, "200")
                .path("examples")
                .path(name)
                .path("value");

        assertThat(value.isObject())
                .as("%s 성공 예시가 JSON 객체여야 한다", name)
                .isTrue();

        return ((ObjectNode) value).deepCopy();
    }

    private ObjectNode holder(
            ObjectNode response,
            String location
    ) {
        JsonNode node = switch(location) {
            case "ROOT" -> response;
            case "COUNTS" -> response.path("counts");
            case "ITEM" -> response.path("pendingReports").path(0);
            default -> throw new IllegalArgumentException(
                    "알 수 없는 검증 위치: " + location
            );
        };

        assertThat(node.isObject())
                .as("%s 검증 대상이 객체여야 한다", location)
                .isTrue();

        return (ObjectNode) node;
    }
}