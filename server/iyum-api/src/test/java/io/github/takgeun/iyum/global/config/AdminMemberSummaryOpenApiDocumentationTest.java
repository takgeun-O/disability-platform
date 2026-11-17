package io.github.takgeun.iyum.global.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import io.github.takgeun.iyum.admin.member.api.AdminMemberSummaryReadController;
import io.github.takgeun.iyum.admin.member.application.AdminMemberSummaryReadService;
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
@Import(AdminMemberSummaryReadController.class)
class AdminMemberSummaryOpenApiDocumentationTest
        extends AbstractOpenApiWebTest {

    private static final String API_PATH =
            "/api/v1/admin/members/summary";

    /*
     * 기존 JSON Schema 검증 라이브러리가 사용하는 Jackson 2.
     * API 런타임의 Jackson 설정을 변경하는 것은 아니다.
     */
    private static final ObjectMapper SCHEMA_MAPPER =
            new ObjectMapper();

    @MockitoBean
    private AdminMemberSummaryReadService summaryReadService;

    @Test
    void 요청_파라미터가_없는_조회_API와_캐시_정책을_문서화한다()
            throws Exception {
        JsonNode document = document();
        JsonNode operation = operation(document);

        assertThat(operation.path("parameters").size())
                .isZero();

        assertThat(operation.has("requestBody"))
                .isFalse();

        for (String responseCode : List.of("200", "401", "403")) {
            JsonNode response = operation.path("responses")
                    .path(responseCode);

            assertThat(response.isObject())
                    .as("%s 응답이 존재해야 한다", responseCode)
                    .isTrue();

            assertThat(
                    response.at(
                            "/headers/Cache-Control/schema/example"
                    ).asText()
            ).isEqualTo("no-store");
        }

        assertThat(
                responseContent(document, "200")
                        .at("/schema/$ref")
                        .asText()
        ).isEqualTo(
                "#/components/schemas/AdminMemberSummaryResponse"
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"200", "401", "403"})
    void 모든_응답_예시가_생성된_스키마를_만족한다(
            String responseCode
    ) throws Exception {
        JsonNode document = document();
        JsonNode examples = responseContent(
                document,
                responseCode
        ).path("examples");

        assertThat(examples.isObject()).isTrue();
        assertThat(examples.size()).isGreaterThan(0);

        JsonSchema schema = responseSchema(
                document,
                responseCode
        );

        var fields = examples.fields();

        while (fields.hasNext()) {
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
    void 관리자만_있는_예시는_현재_회원에_관리자를_포함한다()
            throws Exception {
        JsonNode document = document();
        ObjectNode example = successExample(
                document,
                "adminOnly"
        );

        assertThat(
                responseSchema(document, "200").validate(example)
        ).isEmpty();

        JsonNode counts = example.path("counts");

        assertThat(counts.path("currentMemberCount").longValue())
                .isEqualTo(1L);

        assertThat(counts.path("todayMemberCount").longValue())
                .isZero();

        assertThat(counts.path("pendingMemberCount").longValue())
                .isZero();

        assertThat(counts.path("suspendedMemberCount").longValue())
                .isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "ROOT, queriedAt",
            "ROOT, baseDate",
            "ROOT, counts",
            "COUNTS, currentMemberCount",
            "COUNTS, todayMemberCount",
            "COUNTS, pendingMemberCount",
            "COUNTS, suspendedMemberCount"
    })
    void 필수_응답_필드의_누락과_null을_거부한다(
            String location,
            String field
    ) throws Exception {
        JsonNode document = document();
        JsonSchema schema = responseSchema(document, "200");
        ObjectNode original = successExample(document, "summary");

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
            "currentMemberCount",
            "todayMemberCount",
            "pendingMemberCount",
            "suspendedMemberCount"
    })
    void 집계는_0_이상의_정수만_허용한다(
            String field
    ) throws Exception {
        JsonNode document = document();

        JsonNode property = document.at(
                "/components/schemas/AdminMemberSummaryCountsResponse/properties"
        ).path(field);

        assertThat(property.path("type").asText())
                .isEqualTo("integer");

        assertThat(property.path("format").asText())
                .isEqualTo("int64");

        assertThat(property.path("minimum").isNumber())
                .isTrue();

        assertThat(property.path("minimum").decimalValue())
                .isEqualByComparingTo("0");

        JsonSchema schema = responseSchema(document, "200");
        ObjectNode original = successExample(document, "summary");

        assertThat(schema.validate(original)).isEmpty();

        for (long validValue : new long[]{
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

        for (String invalidJson : List.of(
                "-1",
                "0.5",
                "\"1\"",
                "true",
                "{}",
                "[]"
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

    @ParameterizedTest
    @CsvSource({
            "queriedAt, date-time, 2026-11-18T03:00:00Z, not-a-time",
            "baseDate, date, 2026-11-18, 2026-02-30"
    })
    void 날짜와_시각의_실제_형식을_검증한다(
            String field,
            String format,
            String valid,
            String invalid
    ) throws Exception {
        JsonNode document = document();

        JsonNode property = document.at(
                "/components/schemas/AdminMemberSummaryResponse/properties"
        ).path(field);

        assertThat(property.path("type").asText())
                .isEqualTo("string");

        assertThat(property.path("format").asText())
                .isEqualTo(format);

        JsonSchema schema = responseSchema(document, "200");
        ObjectNode response = successExample(document, "summary");

        response.put(field, valid);

        assertThat(schema.validate(response))
                .as("%s의 정상 날짜 형식은 허용한다", field)
                .isEmpty();

        response.put(field, invalid);

        assertThat(schema.validate(response))
                .as("%s의 잘못된 날짜 형식은 거부한다", field)
                .isNotEmpty();

        response.put(field, 123);

        assertThat(schema.validate(response))
                .as("%s에 숫자는 허용하지 않는다", field)
                .isNotEmpty();
    }

    @Test
    void 성공_예시의_이름을_유지한다() throws Exception {
        JsonNode document = document();

        assertThat(
                responseContent(document, "200")
                        .path("examples")
                        .properties()
                        .stream()
                        .map(entry -> entry.getKey())
                        .toList()
        ).containsExactlyInAnyOrder(
                "summary",
                "adminOnly"
        );
    }

    @ParameterizedTest
    @CsvSource({
            "401, authenticationRequired, AUTHENTICATION_REQUIRED",
            "401, loginRequired, AUTHENTICATION_REQUIRED",
            "403, accessDenied, ACCESS_DENIED",
            "403, adminRequired, ADMIN_REQUIRED"
    })
    void 오류_예시의_이름과_코드를_유지한다(
            String responseCode,
            String exampleName,
            String expectedCode
    ) throws Exception {
        JsonNode document = document();

        JsonNode example = responseContent(
                document,
                responseCode
        )
                .path("examples")
                .path(exampleName)
                .path("value");

        assertThat(example.isObject()).isTrue();

        assertThat(example.path("code").asText())
                .isEqualTo(expectedCode);

        assertThat(example.path("fieldErrors").isArray())
                .isTrue();

        assertThat(example.path("fieldErrors").size())
                .isZero();
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
                "build/reports/admin-member-summary-openapi.json"
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
                .as("회원 요약 GET API가 문서에 존재해야 한다")
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
        ).getSchema(
                schemaDocument,
                SchemaValidatorsConfig.builder()
                        .formatAssertionsEnabled(true)
                        .build()
        );
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
        JsonNode node = switch (location) {
            case "ROOT" -> response;
            case "COUNTS" -> response.path("counts");
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