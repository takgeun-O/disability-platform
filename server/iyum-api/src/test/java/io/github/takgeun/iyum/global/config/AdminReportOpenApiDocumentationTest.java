package io.github.takgeun.iyum.global.config;

import io.github.takgeun.iyum.admin.report.api.AdminReportHandlingController;
import io.github.takgeun.iyum.admin.report.api.AdminReportReadController;
import io.github.takgeun.iyum.admin.report.application.AdminReportHandlingService;
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
@Import({
        AdminReportReadController.class,
        AdminReportHandlingController.class
})
class AdminReportOpenApiDocumentationTest extends AbstractOpenApiWebTest {

    @MockitoBean
    AdminReportReadService reportService;

    @MockitoBean
    AdminReportHandlingService handlingService;

    private JsonNode document() throws Exception {
        var response = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
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
                .containsExactlyInAnyOrder(
                        "targetType",
                        "status",
                        "reasonType",
                        "startDate",
                        "endDate",
                        "sort",
                        "page",
                        "size"
                );
        for (var p : params) {
            assertThat(p.get("in").asString()).isEqualTo("query");
            assertThat(p.get("required").booleanValue()).isFalse();
            String name = p.get("name").asString();
            var schema = p.get("schema");
            if (name.equals("page") || name.equals("size")) {
                assertThat(schema.get("type").asString()).isEqualTo("integer");
                assertThat(schema.get("format").asString()).isEqualTo("int32");
                assertThat(schema.get("default").isIntegralNumber()).isTrue();
                assertThat(schema.get("default").intValue())
                        .isEqualTo(name.equals("page") ? 0 : 20);
                assertThat(schema.get("minimum").isNumber()).isTrue();
                assertThat(schema.get("minimum").intValue())
                        .isEqualTo(name.equals("page") ? 0 : 1);
                if (name.equals("size")) {
                    assertThat(schema.get("maximum").isNumber()).isTrue();
                    assertThat(schema.get("maximum").intValue()).isEqualTo(50);
                } else {
                    assertThat(schema.has("maximum")).isFalse();
                }
            }
            var enums = Map.of(
                    "targetType", List.of("ALL", "POST", "COMMENT"),
                    "status", List.of("RECEIVED", "RESOLVED", "DISMISSED"),
                    "reasonType", List.of(
                            "SPAM_ADVERTISING",
                            "ABUSE_HATE",
                            "PERSONAL_INFORMATION",
                            "DUPLICATE_FLOODING",
                            "OFF_TOPIC",
                            "OTHER"
                    ),
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
        assertThat(doc.at("/components/securitySchemes")
                .toString())
                .doesNotContainIgnoringCase("bearer");
    }

    @Test
    void responseSchemasDistinguishRequiredPresenceAndNullableValues()
            throws Exception {

        var schemas = document().at("/components/schemas");

        var expected = Map.of(
                "AdminReportItemResponse",
                List.of(
                        "reportId",
                        "targetType",
                        "targetId",
                        "status",
                        "reasonType",
                        "titleSnapshot",
                        "contentSnapshotPreview",
                        "createdAt",
                        "currentAuthor"
                ),

                "AdminReportDetailResponse",
                List.of(
                        "reportId",
                        "reporterMemberId",
                        "targetType",
                        "targetId",
                        "status",
                        "reasonType",
                        "reasonDetail",
                        "titleSnapshot",
                        "contentSnapshot",
                        "createdAt",
                        "currentTarget",
                        "handledByMemberId",
                        "handledAt",
                        "handlingReason"
                ),

                "AdminReportCurrentTargetResponse",
                List.of(
                        "targetId",
                        "parentPostId",
                        "author",
                        "title",
                        "content",
                        "hiddenAt",
                        "deletedAt"
                ),

                "AdminReportTargetAuthorResponse",
                List.of(
                        "memberId",
                        "nickname",
                        "withdrawn"
                ),

                "AdminReportListResponse",
                List.of(
                        "items",
                        "page",
                        "size",
                        "totalElements",
                        "totalPages",
                        "hasNext",
                        "hasPrevious"
                )
        );

        // 값이 null일 수 있는 필드도 응답에서는 생략하지 않는다.
        // 따라서 properties와 required에 모두 포함되어야 한다.
        for (var entry : expected.entrySet()) {
            String schemaName = entry.getKey();
            List<String> expectedFields = entry.getValue();

            var schema = schemas.path(schemaName);

            assertThat(schema.isObject())
                    .as("%s 스키마가 존재해야 한다", schemaName)
                    .isTrue();

            var properties = schema.path("properties");

            assertThat(properties.isObject())
                    .as("%s의 properties가 존재해야 한다", schemaName)
                    .isTrue();

            assertThat(properties.propertyNames())
                    .as("%s의 응답 필드", schemaName)
                    .containsExactlyInAnyOrderElementsOf(expectedFields);

            var required = schema.path("required");

            assertThat(required.isArray())
                    .as("%s의 required가 배열이어야 한다", schemaName)
                    .isTrue();

            assertThat(
                    required.valueStream()
                            .map(JsonNode::asString)
                            .toList()
            )
                    .as("%s의 필수 응답 필드", schemaName)
                    .containsExactlyInAnyOrderElementsOf(expectedFields);
        }

        assertThat(
                schemas.at(
                        "/AdminReportListResponse/properties/items/items/$ref"
                ).asString()
        )
                .endsWith("/AdminReportItemResponse");

        for (String schemaName : List.of(
                "AdminReportItemResponse",
                "AdminReportDetailResponse"
        )) {
            var targetType = schemas.path(schemaName)
                    .at("/properties/targetType");

            assertThat(
                    targetType.path("enum")
                            .valueStream()
                            .map(JsonNode::asString)
                            .toList()
            )
                    .as("%s의 대상 유형", schemaName)
                    .containsExactlyInAnyOrder(
                            "POST",
                            "COMMENT"
                    );
        }

        // 필드 존재 여부(required)와 값의 null 허용 여부(type)는
        // 서로 다른 계약이므로 각각 검증한다.
        for (String path : List.of(
                "AdminReportItemResponse/properties/titleSnapshot",
                "AdminReportDetailResponse/properties/reasonDetail",
                "AdminReportDetailResponse/properties/titleSnapshot",
                "AdminReportDetailResponse/properties/handledByMemberId",
                "AdminReportDetailResponse/properties/handledAt",
                "AdminReportDetailResponse/properties/handlingReason",
                "AdminReportCurrentTargetResponse/properties/parentPostId",
                "AdminReportCurrentTargetResponse/properties/title",
                "AdminReportCurrentTargetResponse/properties/hiddenAt",
                "AdminReportCurrentTargetResponse/properties/deletedAt"
        )) {
            var types = schemas.at("/" + path + "/type");

            assertThat(types.isArray())
                    .as("%s의 type이 null을 포함한 배열이어야 한다", path)
                    .isTrue();

            assertThat(
                    types.valueStream()
                            .map(JsonNode::asString)
                            .toList()
            )
                    .as("%s는 명시적 null을 허용해야 한다", path)
                    .contains("null");
        }

        // 새로 추가한 처리 정보의 실제 값 타입도 검증한다.
        var detailProperties = schemas.at(
                "/AdminReportDetailResponse/properties"
        );

        var handlerSchema = detailProperties.path("handledByMemberId");

        assertThat(
                handlerSchema.path("type")
                        .valueStream()
                        .map(JsonNode::asString)
                        .toList()
        )
                .containsExactlyInAnyOrder(
                        "integer",
                        "null"
                );

        assertThat(handlerSchema.path("format").asString())
                .isEqualTo("int64");

        var handledAtSchema = detailProperties.path("handledAt");

        assertThat(
                handledAtSchema.path("type")
                        .valueStream()
                        .map(JsonNode::asString)
                        .toList()
        )
                .containsExactlyInAnyOrder(
                        "string",
                        "null"
                );

        assertThat(handledAtSchema.path("format").asString())
                .isEqualTo("date-time");

        var handlingReasonSchema = detailProperties.path("handlingReason");

        assertThat(
                handlingReasonSchema.path("type")
                        .valueStream()
                        .map(JsonNode::asString)
                        .toList()
        )
                .containsExactlyInAnyOrder(
                        "string",
                        "null"
                );
    }

    @Test
    void generatedExamplesPreserveSnapshotsNullsAndPagination() throws Exception {
        var doc = document();
        var paths = doc.get("paths");
        for (String path : List.of("/api/v1/admin/reports", "/api/v1/admin/reports/{reportId}")) {
            var responses = paths.get(path).get("get").get("responses");
            assertThat(responses.at("/200/headers/Cache-Control/schema/example")
                    .asString())
                    .isEqualTo("no-store");
            for (String code : List.of("400", "401", "403")) {
                assertThat(responses.at("/" + code + "/content/application~1json/schema/$ref")
                        .asString())
                        .endsWith("/ApiErrorResponse");
                for (var example : responses.at("/" + code + "/content/application~1json/examples")) {
                    var value = example.get("value");
                    assertThat(value.isObject()).isTrue();
                    assertThat(value.propertyNames()).containsExactlyInAnyOrder(
                            "code",
                            "message",
                            "fieldErrors"
                    );
                    assertThat(value.get("fieldErrors").isArray()).isTrue();
                }
            }
        }
        var lists = paths.get("/api/v1/admin/reports").get("get")
                .at("/responses/200/content/application~1json/examples");
        assertThat(lists.propertyNames()).containsExactlyInAnyOrder(
                "mixed",
                "empty",
                "outOfRange",
                "missingCurrentAuthor"
        );
        for (var example : lists) {
            var v = example.get("value");
            assertThat(v.isObject()).isTrue();
            int total = v.get("totalElements").intValue();
            assertThat(v.get("totalPages").intValue()).isEqualTo((total + 19) / 20);
        }
        assertThat(lists.at("/mixed/value/items/1/titleSnapshot").isNull()).isTrue();
        assertThat(lists.at("/mixed/value/items/1/currentAuthor/nickname")
                .asString())
                .isEqualTo("탈퇴한 회원");
        assertThat(lists.at("/outOfRange/value/totalElements").intValue()).isEqualTo(21);
        assertThat(lists.at("/outOfRange/value/items").isEmpty()).isTrue();
        var details = paths.get("/api/v1/admin/reports/{reportId}").get("get")
                .at("/responses/200/content/application~1json/examples");
        assertThat(details.propertyNames()).containsExactlyInAnyOrder(
                "post",
                "comment",
                "missingTarget",
                "resolved",
                "dismissed"
        );
        assertThat(details.at("/post/value/currentTarget/parentPostId").isNull()).isTrue();
        assertThat(details.at("/comment/value/currentTarget/title").isNull()).isTrue();
        assertThat(details.at("/comment/value/currentTarget/parentPostId").intValue())
                .isEqualTo(104);
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

    @ParameterizedTest
    @ValueSource(strings = {
            "RECEIVED",
            "RESOLVED",
            "DISMISSED"
    })
    void detailHandlingFieldsAcceptValidValuesAndRejectMissingOrInvalidTypes(
            String reportStatus
    ) throws Exception {

        var document = document();

        // 현재 프로젝트의 JSON Schema 검증 라이브러리는
        // Jackson 2 JsonNode를 사용한다.
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        var root = mapper.readTree(document.toString());

        var content = root.path("paths")
                .path("/api/v1/admin/reports/{reportId}")
                .path("get")
                .path("responses")
                .path("200")
                .path("content")
                .path("application/json");

        String responseSchemaRef = content.path("schema")
                .path("$ref")
                .asText();

        assertThat(responseSchemaRef)
                .endsWith("/AdminReportDetailResponse");

        // 외부 스키마를 조회하지 않고 생성된 문서 내부 참조만 사용한다.
        assertThat(root.findValues("$ref"))
                .allSatisfy(reference ->
                        assertThat(reference.asText())
                                .startsWith("#/")
                );

        var schemaDocument = mapper.createObjectNode();

        schemaDocument.put(
                "$schema",
                "https://json-schema.org/draft/2020-12/schema"
        );

        schemaDocument.set(
                "components",
                root.path("components")
        );

        schemaDocument.put(
                "$ref",
                responseSchemaRef
        );

        var factory = com.networknt.schema.JsonSchemaFactory.getInstance(
                com.networknt.schema.SpecVersion.VersionFlag.V202012
        );

        var schema = factory.getSchema(schemaDocument);

        var example = content.path("examples")
                .path("post")
                .path("value");

        assertThat(example.isObject())
                .as("게시글 신고 상세 성공 예시가 존재해야 한다")
                .isTrue();

        var response =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        example.deepCopy();

        response.put("status", reportStatus);

        if ("RECEIVED".equals(reportStatus)) {
            response.putNull("handledByMemberId");
            response.putNull("handledAt");
            response.putNull("handlingReason");
        } else {
            response.put("handledByMemberId", 40L);
            response.put("handledAt", "2026-11-12T02:00:00Z");
            response.put(
                    "handlingReason",
                    "RESOLVED".equals(reportStatus)
                            ? "신고 내용을 검토하고 필요한 조치를 완료했습니다."
                            : "검토 결과 운영 정책 위반에 해당하지 않습니다."
            );
        }

        assertThat(schema.validate(response))
                .as("%s 상태의 상세 응답이 스키마를 만족해야 한다", reportStatus)
                .isEmpty();

        for (String field : List.of(
                "handledByMemberId",
                "handledAt",
                "handlingReason"
        )) {
            // null 허용과 별개로 필드 자체는 반드시 존재해야 한다.
            var missingField = response.deepCopy();

            missingField.remove(field);

            assertThat(schema.validate(missingField))
                    .as(
                            "%s 응답에서 %s 필드 누락을 거부해야 한다",
                            reportStatus,
                            field
                    )
                    .isNotEmpty();

            // 회원 ID는 정수, 처리 시각과 사유는 문자열이어야 한다.
            var invalidType = response.deepCopy();

            switch (field) {
                case "handledByMemberId" ->
                        invalidType.put(field, "not-an-id");

                case "handledAt", "handlingReason" ->
                        invalidType.put(field, 42);

                default ->
                        throw new IllegalArgumentException(field);
            }

            assertThat(schema.validate(invalidType))
                    .as(
                            "%s 응답에서 %s의 잘못된 타입을 거부해야 한다",
                            reportStatus,
                            field
                    )
                    .isNotEmpty();
        }
    }

    @Test
    void detailExamplesDescribeStoredHandlingInformation() throws Exception {
        var examples = document().get("paths").get("/api/v1/admin/reports/{reportId}")
                .get("get").at("/responses/200/content/application~1json/examples");
        for (var example : examples) {
            var value = example.get("value");
            for (String field : List.of("handledByMemberId", "handledAt", "handlingReason")) {
                assertThat(value.has(field)).isTrue();
                if (value.get("status").asString().equals("RECEIVED")) {
                    assertThat(value.get(field).isNull()).isTrue();
                }
            }
        }
        for (String name : List.of("resolved", "dismissed")) {
            var value = examples.get(name).get("value");
            assertThat(value.get("status").asString())
                    .isEqualTo(name.equals("resolved") ? "RESOLVED" : "DISMISSED");
            assertThat(value.get("handledByMemberId").isIntegralNumber()).isTrue();
            assertThat(value.get("handledByMemberId").longValue()).isEqualTo(40L);
            assertThat(value.get("handledAt").isString()).isTrue();
            assertThat(java.time.Instant.parse(value.get("handledAt").asString()))
                    .isAfter(java.time.Instant.parse(value.get("createdAt").asString()));
            assertThat(value.get("handlingReason").isString()).isTrue();
            assertThat(value.get("handlingReason").asString()).isNotBlank();
        }
        assertThat(examples.at("/resolved/value/currentTarget/hiddenAt").isNull()).isTrue();
        assertThat(examples.at("/dismissed/value/currentTarget").isNull()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"resolve", "dismiss"})
    void handlingOperationsDocumentSessionCsrfSchemasAndExamples(String action) throws Exception {
        var doc = document();
        var operation = doc.get("paths").get("/api/v1/admin/reports/{reportId}/" + action).get("post");
        assertThat(operation.get("parameters").valueStream().map(p -> p.get("name").asString()).toList())
                .containsExactlyInAnyOrder("reportId", "X-CSRF-TOKEN");
        for (var parameter : operation.get("parameters")) {
            assertThat(parameter.get("required").booleanValue()).isTrue();
            boolean id = parameter.get("name").asString().equals("reportId");
            assertThat(parameter.get("in").asString()).isEqualTo(id ? "path" : "header");
            assertThat(parameter.at("/schema/type").asString())
                    .isEqualTo(id ? "integer" : "string");
            if (id) {
                assertThat(parameter.at("/schema/format").asString()).isEqualTo("int64");
            }
        }
        assertThat(operation.at("/requestBody/required").booleanValue()).isTrue();
        assertThat(operation.at("/requestBody/content/application~1json/schema/$ref").asString())
                .endsWith("/AdminReportHandlingRequest");
        var requestSchema = doc.at("/components/schemas/AdminReportHandlingRequest");
        assertThat(requestSchema.get("required").valueStream().map(JsonNode::asString).toList())
                .containsExactly("handlingReason");
        assertThat(requestSchema.get("properties").propertyNames()).containsExactly("handlingReason");
        assertThat(requestSchema.at("/properties/handlingReason").has("maxLength")).isFalse();
        var responseSchema = doc.at("/components/schemas/AdminReportHandlingResponse");
        assertThat(responseSchema.get("required").valueStream().map(JsonNode::asString).toList())
                .containsExactlyInAnyOrder(
                        "reportId",
                        "status",
                        "handledByMemberId",
                        "handledAt",
                        "handlingReason"
                );
        assertThat(responseSchema.at("/properties/status/enum")
                .valueStream()
                .map(JsonNode::asString)
                .toList())
                .containsExactlyInAnyOrder("RESOLVED", "DISMISSED");
        assertThat(operation.at("/responses/200/headers/Cache-Control/schema/example")
                .asString())
                .isEqualTo("no-store");
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var root = mapper.readTree(doc.toString());
        var factory = com.networknt.schema.JsonSchemaFactory.getInstance(
                com.networknt.schema.SpecVersion.VersionFlag.V202012);
        for (String pointer : List.of(
                "/requestBody/content/application~1json",
                "/responses/200/content/application~1json"
        )) {
            var content = operation.at(pointer);
            var schemaDoc = mapper.createObjectNode();
            schemaDoc.set("components", root.path("components"));
            schemaDoc.put("$ref", content.at("/schema/$ref").asString());
            var schema = factory.getSchema(schemaDoc);
            for (var example : content.get("examples")) {
                assertThat(schema.validate(mapper.readTree(example.get("value").toString())))
                        .isEmpty();
            }
        }
        var response = operation.at("/responses/200/content/application~1json/examples/" + action + "/value");
        assertThat(response.get("status")
                .asString())
                .isEqualTo(action.equals("resolve") ? "RESOLVED" : "DISMISSED");
        var errors = Map.of(
                "400", List.of(
                        "INVALID_HANDLING_REASON",
                        "INVALID_REQUEST_BODY",
                        "INVALID_REQUEST_PARAMETER"
                ),
                "401", List.of("AUTHENTICATION_REQUIRED"),
                "403", List.of("ACCESS_DENIED", "ADMIN_REQUIRED", "CSRF_TOKEN_INVALID"),
                "404", List.of("REPORT_NOT_FOUND"),
                "409", List.of("REPORT_ALREADY_HANDLED"),
                "415", List.of("HTTP_ERROR"));
        for (var entry : errors.entrySet()) {
            var content = operation.at("/responses/" + entry.getKey() + "/content/application~1json");
            assertThat(content.at("/schema/$ref").asString()).endsWith("/ApiErrorResponse");
            assertThat(content.get("examples")
                    .propertyNames())
                    .containsExactlyInAnyOrderElementsOf(entry.getValue());
            for (String code : entry.getValue()) {
                var value = content.get("examples").get(code).get("value");
                assertThat(value.get("code").asString()).isEqualTo(code);
                assertThat(value.get("message").asString()).isNotBlank();
                assertThat(value.get("fieldErrors").isArray()).isTrue();
            }
        }
    }

}
