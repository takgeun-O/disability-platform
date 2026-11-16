package io.github.takgeun.iyum.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import io.github.takgeun.iyum.admin.report.api.AdminReportHandlingController;
import io.github.takgeun.iyum.admin.report.api.AdminReportHideAndResolveController;
import io.github.takgeun.iyum.admin.report.api.AdminReportReadController;
import io.github.takgeun.iyum.admin.report.application.AdminReportHandlingService;
import io.github.takgeun.iyum.admin.report.application.AdminReportHideAndResolveService;
import io.github.takgeun.iyum.admin.report.application.AdminReportReadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("dev")
@Import({
        AdminReportReadController.class,
        AdminReportHandlingController.class,
        AdminReportHideAndResolveController.class
})
class AdminReportOpenApiDocumentationTest
        extends AbstractOpenApiWebTest {

    private static final String LIST_PATH =
            "/api/v1/admin/reports";

    private static final String DETAIL_PATH =
            "/api/v1/admin/reports/{reportId}";

    private static final String SCHEMA_PREFIX =
            "#/components/schemas/";

    private static final String JSON_SCHEMA_VERSION =
            "https://json-schema.org/draft/2020-12/schema";

    private static final List<String> HANDLING_FIELDS = List.of(
            "handledByMemberId",
            "handledAt",
            "handlingReason",
            "handlingOutcome"
    );

    /*
     * 애플리케이션과 OpenAPI 문서 조회는 Jackson 3을 사용한다.
     * 현재 JSON Schema 검증 라이브러리는 Jackson 2를 사용하므로
     * 검증에 전달할 때만 노드를 변환한다.
     */
    private static final ObjectMapper SCHEMA_MAPPER =
            new ObjectMapper();

    @MockitoBean
    private AdminReportReadService reportService;

    @MockitoBean
    private AdminReportHandlingService handlingService;

    @MockitoBean
    private AdminReportHideAndResolveService hideAndResolveService;

    @Test
    void 조회_파라미터의_타입과_기본값과_세션_인증_계약을_문서화한다()
            throws Exception {

        JsonNode document = document();

        JsonNode parameters = operation(
                document,
                LIST_PATH,
                "get"
        ).path("parameters");

        assertThat(parameterNames(parameters))
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

        Map<String, List<String>> expectedEnums = Map.of(
                "targetType",
                List.of(
                        "ALL",
                        "POST",
                        "COMMENT"
                ),
                "status",
                List.of(
                        "RECEIVED",
                        "RESOLVED",
                        "DISMISSED"
                ),
                "reasonType",
                List.of(
                        "SPAM_ADVERTISING",
                        "ABUSE_HATE",
                        "PERSONAL_INFORMATION",
                        "DUPLICATE_FLOODING",
                        "OFF_TOPIC",
                        "OTHER"
                ),
                "sort",
                List.of(
                        "LATEST",
                        "OLDEST"
                )
        );

        for (JsonNode parameter : parameters) {
            String name = parameter.path("name").asString();

            JsonNode schema = parameter.path("schema");

            assertThat(parameter.path("in").asString())
                    .isEqualTo("query");

            assertThat(parameter.path("required").isBoolean())
                    .isTrue();

            assertThat(parameter.path("required").booleanValue())
                    .isFalse();

            if ("page".equals(name) || "size".equals(name)) {
                boolean page = "page".equals(name);

                assertThat(schema.path("type").asString())
                        .isEqualTo("integer");

                assertThat(schema.path("format").asString())
                        .isEqualTo("int32");

                assertThat(schema.path("default").isIntegralNumber())
                        .isTrue();

                assertThat(schema.path("default").intValue())
                        .isEqualTo(page ? 0 : 20);

                assertThat(schema.path("minimum").isNumber())
                        .isTrue();

                assertThat(schema.path("minimum").intValue())
                        .isEqualTo(page ? 0 : 1);

                if (page) {
                    assertThat(schema.has("maximum"))
                            .isFalse();
                } else {
                    assertThat(schema.path("maximum").isNumber())
                            .isTrue();

                    assertThat(schema.path("maximum").intValue())
                            .isEqualTo(50);
                }
            }

            if (expectedEnums.containsKey(name)) {
                assertThat(strings(schema.path("enum")))
                        .as("%s 파라미터의 허용 값", name)
                        .containsExactlyInAnyOrderElementsOf(
                                expectedEnums.get(name)
                        );
            }
        }

        JsonNode detailParameters = operation(
                document,
                DETAIL_PATH,
                "get"
        ).path("parameters");

        assertThat(detailParameters.size())
                .isEqualTo(1);

        JsonNode reportId = detailParameters.path(0);

        assertThat(reportId.path("name").asString())
                .isEqualTo("reportId");

        assertThat(reportId.path("in").asString())
                .isEqualTo("path");

        assertThat(reportId.path("required").booleanValue())
                .isTrue();

        assertThat(reportId.at("/schema/type").asString())
                .isEqualTo("integer");

        assertThat(reportId.at("/schema/format").asString())
                .isEqualTo("int64");

        assertThat(
                document.at("/components/securitySchemes").toString()
        )
                .doesNotContainIgnoringCase("bearer");
    }

    @Test
    void 응답_필드의_필수_존재와_null_허용을_구분한다()
            throws Exception {

        JsonNode schemas = document().at("/components/schemas");

        Map<String, List<String>> expectedFields = Map.of(
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
                        "handlingReason",
                        "handlingOutcome"
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

        for (var entry : expectedFields.entrySet()) {
            assertRequiredFields(
                    schemas.path(entry.getKey()),
                    entry.getKey(),
                    entry.getValue()
            );
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
            JsonNode targetType = schemas.path(schemaName)
                    .at("/properties/targetType");

            assertThat(strings(targetType.path("enum")))
                    .as("%s의 대상 유형", schemaName)
                    .containsExactlyInAnyOrder(
                            "POST",
                            "COMMENT"
                    );
        }

        for (String propertyPath : List.of(
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
            JsonNode types = schemas.at(
                    "/" + propertyPath + "/type"
            );

            assertThat(strings(types))
                    .as("%s는 명시적 null을 허용해야 한다", propertyPath)
                    .contains("null");
        }

        JsonNode detailProperties = schemas.at(
                "/AdminReportDetailResponse/properties"
        );

        JsonNode handlerSchema =
                detailProperties.path("handledByMemberId");

        assertThat(strings(handlerSchema.path("type")))
                .containsExactlyInAnyOrder(
                        "integer",
                        "null"
                );

        assertThat(handlerSchema.path("format").asString())
                .isEqualTo("int64");

        JsonNode handledAtSchema =
                detailProperties.path("handledAt");

        assertThat(strings(handledAtSchema.path("type")))
                .containsExactlyInAnyOrder(
                        "string",
                        "null"
                );

        assertThat(handledAtSchema.path("format").asString())
                .isEqualTo("date-time");

        JsonNode handlingReasonSchema =
                detailProperties.path("handlingReason");

        assertThat(strings(handlingReasonSchema.path("type")))
                .containsExactlyInAnyOrder(
                        "string",
                        "null"
                );

        /*
         * handlingOutcome은 nullable enum이다.
         * type 배열 또는 anyOf 등 표현 방식에 의존하지 않고,
         * 아래 테스트에서 실제 응답 값으로 null과 enum을 검증한다.
         */
        assertThat(
                detailProperties.path("handlingOutcome").isObject()
        )
                .isTrue();
    }

    @Test
    void 조회_성공_예시는_스냅샷과_null과_페이지_정보를_유지한다()
            throws Exception {

        JsonNode document = document();

        for (String path : List.of(
                LIST_PATH,
                DETAIL_PATH
        )) {
            JsonNode responses = operation(
                    document,
                    path,
                    "get"
            ).path("responses");

            assertThat(
                    responses.at(
                            "/200/headers/Cache-Control/schema/example"
                    ).asString()
            )
                    .isEqualTo("no-store");

            for (String statusCode : List.of(
                    "400",
                    "401",
                    "403"
            )) {
                JsonNode content = responses.path(statusCode)
                        .at("/content/application~1json");

                assertThat(content.at("/schema/$ref").asString())
                        .endsWith("/ApiErrorResponse");

                JsonNode examples = content.path("examples");

                assertThat(examples.isObject())
                        .isTrue();

                assertThat(examples.isEmpty())
                        .isFalse();

                for (JsonNode example : examples) {
                    assertErrorValue(example.path("value"));
                }
            }
        }

        JsonNode listExamples = successContent(
                operation(
                        document,
                        LIST_PATH,
                        "get"
                )
        ).path("examples");

        assertThat(listExamples.propertyNames())
                .containsExactlyInAnyOrder(
                        "mixed",
                        "empty",
                        "outOfRange",
                        "missingCurrentAuthor"
                );

        for (JsonNode example : listExamples) {
            JsonNode value = example.path("value");

            assertThat(value.isObject())
                    .isTrue();

            int totalElements = value.path("totalElements").intValue();

            assertThat(value.path("size").intValue())
                    .isEqualTo(20);

            assertThat(value.path("totalPages").intValue())
                    .isEqualTo((totalElements + 19) / 20);
        }

        assertThat(
                listExamples.at(
                        "/mixed/value/items/1/titleSnapshot"
                ).isNull()
        )
                .isTrue();

        assertThat(
                listExamples.at(
                        "/mixed/value/items/1/currentAuthor/nickname"
                ).asString()
        )
                .isEqualTo("탈퇴한 회원");

        assertThat(
                listExamples.at(
                        "/outOfRange/value/totalElements"
                ).intValue()
        )
                .isEqualTo(21);

        assertThat(
                listExamples.at(
                        "/outOfRange/value/items"
                ).isEmpty()
        )
                .isTrue();

        JsonNode detailExamples = successContent(
                operation(
                        document,
                        DETAIL_PATH,
                        "get"
                )
        ).path("examples");

        assertThat(detailExamples.propertyNames())
                .containsExactlyInAnyOrder(
                        "post",
                        "comment",
                        "missingTarget",
                        "resolved",
                        "dismissed",
                        "contentHidden",
                        "contentAlreadyHidden",
                        "legacyUnknown"
                );

        assertThat(
                detailExamples.at(
                        "/post/value/currentTarget/parentPostId"
                ).isNull()
        )
                .isTrue();

        assertThat(
                detailExamples.at(
                        "/comment/value/currentTarget/title"
                ).isNull()
        )
                .isTrue();

        assertThat(
                detailExamples.at(
                        "/comment/value/currentTarget/parentPostId"
                ).intValue()
        )
                .isEqualTo(104);

        assertThat(
                detailExamples.at(
                        "/missingTarget/value/currentTarget"
                ).isNull()
        )
                .isTrue();

        assertThat(
                detailExamples.at(
                        "/missingTarget/value/contentSnapshot"
                ).asString()
        )
                .isNotBlank();

        assertThat(
                detailExamples.at(
                        "/post/value/reporterMemberId"
                ).longValue()
        )
                .isNotEqualTo(
                        detailExamples.at(
                                "/post/value/currentTarget/author/memberId"
                        ).longValue()
                );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "list",
            "detail"
    })
    void 현재_대상_객체는_null을_허용하지만_누락과_잘못된_객체는_거부한다(
            String kind
    ) throws Exception {

        JsonNode document = document();

        boolean list = "list".equals(kind);

        String path = list
                ? LIST_PATH
                : DETAIL_PATH;

        JsonNode content = successContent(
                operation(
                        document,
                        path,
                        "get"
                )
        );

        JsonSchema schema = responseSchema(
                document,
                content
        );

        assertExamplesMatchSchema(
                schema,
                content.path("examples")
        );

        String exampleName = list
                ? "mixed"
                : "post";

        ObjectNode valid = exampleObject(
                content,
                exampleName
        );

        assertThat(schema.validate(valid))
                .isEmpty();

        String field = list
                ? "currentAuthor"
                : "currentTarget";

        ObjectNode modified = valid.deepCopy();

        ObjectNode holder = list
                ? (ObjectNode) modified.path("items").path(0)
                : modified;

        holder.putNull(field);

        assertThat(schema.validate(modified))
                .as("%s의 명시적 null을 허용해야 한다", field)
                .isEmpty();

        holder.remove(field);

        assertThat(schema.validate(modified))
                .as("%s 필드 누락을 거부해야 한다", field)
                .isNotEmpty();

        holder.put(field, "invalid");

        assertThat(schema.validate(modified))
                .as("%s에 문자열을 넣을 수 없다", field)
                .isNotEmpty();

        holder.put(field, 42);

        assertThat(schema.validate(modified))
                .as("%s에 숫자를 넣을 수 없다", field)
                .isNotEmpty();

        ObjectNode original = (ObjectNode) (
                list
                        ? valid.path("items").path(0).path(field)
                        : valid.path(field)
        );

        ObjectNode missingProperty = original.deepCopy();

        missingProperty.remove(
                list ? "memberId" : "content"
        );

        holder.set(field, missingProperty);

        assertThat(schema.validate(modified))
                .as("참조 객체 내부의 필수 필드 누락을 거부해야 한다")
                .isNotEmpty();

        ObjectNode invalidProperty = original.deepCopy();

        if (list) {
            invalidProperty.put(
                    "memberId",
                    "not-an-id"
            );
        } else {
            invalidProperty.put(
                    "content",
                    42
            );
        }

        holder.set(field, invalidProperty);

        assertThat(schema.validate(modified))
                .as("참조 객체 내부의 잘못된 타입을 거부해야 한다")
                .isNotEmpty();

        /*
         * 응답 필드에서 null을 허용하더라도
         * 공용 객체 스키마 자체를 nullable로 바꾸면 안 된다.
         */
        String sharedSchemaName = list
                ? "AdminReportTargetAuthorResponse"
                : "AdminReportCurrentTargetResponse";

        JsonSchema sharedSchema = schema(
                document,
                SCHEMA_PREFIX + sharedSchemaName
        );

        assertThat(
                sharedSchema.validate(
                        SCHEMA_MAPPER.nullNode()
                )
        )
                .isNotEmpty();

        assertThat(sharedSchema.validate(original))
                .isEmpty();
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                    "RECEIVED, NULL",
                    "RESOLVED, CONTENT_HIDDEN",
                    "RESOLVED, CONTENT_ALREADY_HIDDEN",
                    "RESOLVED, NO_CONTENT_ACTION",
                    "RESOLVED, LEGACY_UNKNOWN",
                    "DISMISSED, NO_CONTENT_ACTION",
                    "DISMISSED, LEGACY_UNKNOWN"
            },
            nullValues = "NULL"
    )
    void 상세_응답은_처리_정보를_허용하고_필드_누락과_잘못된_타입을_거부한다(
            String reportStatus,
            String handlingOutcome
    ) throws Exception {

        JsonNode document = document();

        JsonNode content = successContent(
                operation(
                        document,
                        DETAIL_PATH,
                        "get"
                )
        );

        assertThat(content.at("/schema/$ref").asString())
                .endsWith("/AdminReportDetailResponse");

        JsonSchema schema = responseSchema(
                document,
                content
        );

        ObjectNode response = exampleObject(
                content,
                "post"
        );

        response.put(
                "status",
                reportStatus
        );

        if ("RECEIVED".equals(reportStatus)) {
            for (String field : HANDLING_FIELDS) {
                response.putNull(field);
            }
        } else {
            response.put(
                    "handledByMemberId",
                    40L
            );

            response.put(
                    "handledAt",
                    "2026-11-12T02:00:00Z"
            );

            response.put(
                    "handlingReason",
                    "RESOLVED".equals(reportStatus)
                            ? "신고 내용을 검토하고 처리를 완료했습니다."
                            : "검토 결과 운영 정책 위반에 해당하지 않습니다."
            );

            response.put(
                    "handlingOutcome",
                    handlingOutcome
            );
        }

        assertThat(schema.validate(response))
                .as(
                        "%s 상태와 %s 처리 결과의 상세 응답",
                        reportStatus,
                        handlingOutcome
                )
                .isEmpty();

        for (String field : HANDLING_FIELDS) {
            ObjectNode missingField = response.deepCopy();

            missingField.remove(field);

            assertThat(schema.validate(missingField))
                    .as(
                            "%s 응답에서 %s 필드 누락을 거부해야 한다",
                            reportStatus,
                            field
                    )
                    .isNotEmpty();

            ObjectNode invalidType = response.deepCopy();

            switch (field) {
                case "handledByMemberId" ->
                        invalidType.put(
                                field,
                                "not-an-id"
                        );

                case "handledAt",
                     "handlingReason",
                     "handlingOutcome" ->
                        invalidType.put(
                                field,
                                42
                        );

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

        ObjectNode stringNullOutcome = response.deepCopy();
        stringNullOutcome.put("handlingOutcome", "null");

        assertThat(schema.validate(stringNullOutcome))
                .as("문자열 null은 실제 JSON null이 아니다")
                .isNotEmpty();

        ObjectNode unknownOutcome = response.deepCopy();

        unknownOutcome.put(
                "handlingOutcome",
                "UNKNOWN"
        );

        assertThat(schema.validate(unknownOutcome))
                .as("정의되지 않은 처리 결과를 거부해야 한다")
                .isNotEmpty();
    }

    @Test
    void 상세_성공_예시는_저장된_처리_정보와_처리_결과를_보여준다()
            throws Exception {

        JsonNode document = document();

        JsonNode examples = successContent(
                operation(
                        document,
                        DETAIL_PATH,
                        "get"
                )
        ).path("examples");

        assertThat(examples.isObject())
                .isTrue();

        assertThat(examples.isEmpty())
                .isFalse();

        for (JsonNode example : examples) {
            JsonNode value = example.path("value");

            for (String field : HANDLING_FIELDS) {
                assertThat(value.has(field))
                        .as("상세 성공 예시에 %s 필드가 있어야 한다", field)
                        .isTrue();

                if ("RECEIVED".equals(
                        value.path("status").asString()
                )) {
                    assertThat(value.path(field).isNull())
                            .as("접수 상태의 %s 값은 null이어야 한다", field)
                            .isTrue();
                }
            }
        }

        for (String exampleName : List.of(
                "resolved",
                "dismissed",
                "contentHidden",
                "contentAlreadyHidden",
                "legacyUnknown"
        )) {
            JsonNode value = examples.path(exampleName)
                    .path("value");

            String expectedStatus = "dismissed".equals(exampleName)
                    ? "DISMISSED"
                    : "RESOLVED";

            assertThat(value.path("status").asString())
                    .isEqualTo(expectedStatus);

            assertThat(
                    value.path("handledByMemberId").isIntegralNumber()
            )
                    .isTrue();

            assertThat(value.path("handledByMemberId").longValue())
                    .isEqualTo(40L);

            assertThat(value.path("handledAt").isString())
                    .isTrue();

            Instant handledAt = Instant.parse(
                    value.path("handledAt").asString()
            );

            Instant createdAt = Instant.parse(
                    value.path("createdAt").asString()
            );

            assertThat(handledAt)
                    .isAfter(createdAt);

            assertThat(value.path("handlingReason").isString())
                    .isTrue();

            assertThat(value.path("handlingReason").asString())
                    .isNotBlank();

            String expectedOutcome = switch (exampleName) {
                case "contentHidden" -> "CONTENT_HIDDEN";
                case "contentAlreadyHidden" -> "CONTENT_ALREADY_HIDDEN";
                case "legacyUnknown" -> "LEGACY_UNKNOWN";
                default -> "NO_CONTENT_ACTION";
            };

            assertThat(value.path("handlingOutcome").asString())
                    .isEqualTo(expectedOutcome);

            if ("contentHidden".equals(exampleName)
                    || "contentAlreadyHidden".equals(exampleName)) {
                Instant hiddenAt = Instant.parse(
                        value.at("/currentTarget/hiddenAt").asString()
                );

                if ("contentHidden".equals(exampleName)) {
                    assertThat(hiddenAt).isEqualTo(handledAt);
                } else {
                    assertThat(hiddenAt).isBefore(handledAt);
                }
            }
        }

        assertThat(
                examples.at(
                        "/resolved/value/currentTarget/hiddenAt"
                ).isNull()
        )
                .isTrue();

        assertThat(
                examples.at(
                        "/dismissed/value/currentTarget"
                ).isNull()
        )
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "resolve",
            "dismiss"
    })
    void 처리_API는_CSRF와_요청_응답_스키마와_오류_예시를_문서화한다(
            String action
    ) throws Exception {

        JsonNode document = document();

        JsonNode operation = operation(
                document,
                DETAIL_PATH + "/" + action,
                "post"
        );

        JsonNode parameters = operation.path("parameters");

        assertThat(parameterNames(parameters))
                .containsExactlyInAnyOrder(
                        "reportId",
                        "X-CSRF-TOKEN"
                );

        for (JsonNode parameter : parameters) {
            boolean reportId = "reportId".equals(
                    parameter.path("name").asString()
            );

            assertThat(parameter.path("required").booleanValue())
                    .isTrue();

            assertThat(parameter.path("in").asString())
                    .isEqualTo(
                            reportId ? "path" : "header"
                    );

            assertThat(parameter.at("/schema/type").asString())
                    .isEqualTo(
                            reportId ? "integer" : "string"
                    );

            if (reportId) {
                assertThat(
                        parameter.at("/schema/format").asString()
                )
                        .isEqualTo("int64");
            }
        }

        assertThat(
                operation.at("/requestBody/required").booleanValue()
        )
                .isTrue();

        JsonNode requestContent = operation.at(
                "/requestBody/content/application~1json"
        );

        assertThat(requestContent.at("/schema/$ref").asString())
                .endsWith("/AdminReportHandlingRequest");

        JsonNode requestSchema = document.at(
                "/components/schemas/AdminReportHandlingRequest"
        );

        assertRequiredFields(
                requestSchema,
                "AdminReportHandlingRequest",
                List.of("handlingReason")
        );

        assertThat(
                requestSchema.at(
                        "/properties/handlingReason"
                ).has("maxLength")
        )
                .isFalse();

        JsonNode handlingResponseSchema = document.at(
                "/components/schemas/AdminReportHandlingResponse"
        );

        assertRequiredFields(
                handlingResponseSchema,
                "AdminReportHandlingResponse",
                List.of(
                        "reportId",
                        "status",
                        "handledByMemberId",
                        "handledAt",
                        "handlingReason",
                        "handlingOutcome"
                )
        );

        assertThat(
                strings(
                        handlingResponseSchema.at(
                                "/properties/status/enum"
                        )
                )
        )
                .containsExactlyInAnyOrder(
                        "RESOLVED",
                        "DISMISSED"
                );

        assertThat(
                operation.at(
                        "/responses/200/headers/Cache-Control/schema/example"
                ).asString()
        )
                .isEqualTo("no-store");

        JsonNode responseContent = successContent(operation);

        assertThat(responseContent.at("/schema/$ref").asString())
                .endsWith("/AdminReportHandlingResponse");

        for (JsonNode content : List.of(
                requestContent,
                responseContent
        )) {
            JsonSchema schema = responseSchema(
                    document,
                    content
            );

            assertExamplesMatchSchema(
                    schema,
                    content.path("examples")
            );
        }

        JsonNode response = responseContent.path("examples")
                .path(action)
                .path("value");

        assertThat(response.isObject())
                .isTrue();

        assertThat(response.path("status").asString())
                .isEqualTo(
                        "resolve".equals(action)
                                ? "RESOLVED"
                                : "DISMISSED"
                );

        assertThat(response.path("handlingOutcome").asString())
                .isEqualTo("NO_CONTENT_ACTION");

        Map<String, List<String>> expectedErrors = Map.of(
                "400",
                List.of(
                        "INVALID_HANDLING_REASON",
                        "INVALID_REQUEST_BODY",
                        "INVALID_REQUEST_PARAMETER"
                ),
                "401",
                List.of(
                        "AUTHENTICATION_REQUIRED"
                ),
                "403",
                List.of(
                        "ACCESS_DENIED",
                        "ADMIN_REQUIRED",
                        "CSRF_TOKEN_INVALID"
                ),
                "404",
                List.of(
                        "REPORT_NOT_FOUND"
                ),
                "409",
                List.of(
                        "REPORT_ALREADY_HANDLED"
                ),
                "415",
                List.of(
                        "HTTP_ERROR"
                )
        );

        for (var entry : expectedErrors.entrySet()) {
            JsonNode content = operation.path("responses")
                    .path(entry.getKey())
                    .at("/content/application~1json");

            assertThat(content.at("/schema/$ref").asString())
                    .endsWith("/ApiErrorResponse");

            JsonNode examples = content.path("examples");

            assertThat(examples.isObject())
                    .isTrue();

            assertThat(examples.propertyNames())
                    .containsExactlyInAnyOrderElementsOf(
                            entry.getValue()
                    );

            for (String code : entry.getValue()) {
                JsonNode value = examples.path(code)
                        .path("value");

                assertErrorValue(value);

                assertThat(value.path("code").asString())
                        .isEqualTo(code);
            }
        }
    }

    @Test
    void 신규_처리_응답은_현재_처리_결과만_허용하고_null과_과거_구분값을_거부한다()
            throws Exception {

        JsonNode document = document();

        JsonNode content = successContent(
                operation(
                        document,
                        DETAIL_PATH + "/resolve",
                        "post"
                )
        );

        JsonSchema schema = responseSchema(
                document,
                content
        );

        ObjectNode response = exampleObject(
                content,
                "resolve"
        );

        assertThat(schema.validate(response))
                .as("실제 처리 완료 성공 예시가 유효해야 한다")
                .isEmpty();

        /*
         * 여러 처리 API가 사용하는 공용 응답 스키마를 검증한다.
         * resolve API가 콘텐츠 숨김까지 실행한다는 뜻은 아니다.
         */
        for (String outcome : List.of(
                "CONTENT_HIDDEN",
                "CONTENT_ALREADY_HIDDEN",
                "NO_CONTENT_ACTION"
        )) {
            ObjectNode validOutcome = response.deepCopy();

            validOutcome.put(
                    "handlingOutcome",
                    outcome
            );

            assertThat(schema.validate(validOutcome))
                    .as("신규 처리 결과 %s를 허용해야 한다", outcome)
                    .isEmpty();
        }

        for (String outcome : List.of(
                "LEGACY_UNKNOWN",
                "UNKNOWN"
        )) {
            ObjectNode invalidOutcome = response.deepCopy();

            invalidOutcome.put(
                    "handlingOutcome",
                    outcome
            );

            assertThat(schema.validate(invalidOutcome))
                    .as("신규 처리 응답에서 %s를 거부해야 한다", outcome)
                    .isNotEmpty();
        }

        ObjectNode nullOutcome = response.deepCopy();

        nullOutcome.putNull("handlingOutcome");

        assertThat(schema.validate(nullOutcome))
                .as("신규 처리 결과는 null일 수 없다")
                .isNotEmpty();

        ObjectNode missingOutcome = response.deepCopy();

        missingOutcome.remove("handlingOutcome");

        assertThat(schema.validate(missingOutcome))
                .as("신규 처리 결과 필드는 생략할 수 없다")
                .isNotEmpty();

        ObjectNode invalidType = response.deepCopy();

        invalidType.put(
                "handlingOutcome",
                42
        );

        assertThat(schema.validate(invalidType))
                .as("신규 처리 결과에 숫자를 넣을 수 없다")
                .isNotEmpty();
    }

    @Test
    void 숨김_처리_API는_요청_계약과_두_가지_성공_결과를_문서화한다()
            throws Exception {

        JsonNode document = document();

        JsonNode operation = operation(
                document,
                DETAIL_PATH + "/hide-and-resolve",
                "post"
        );

        JsonNode parameters = operation.path("parameters");

        assertThat(parameterNames(parameters))
                .containsExactlyInAnyOrder(
                        "reportId",
                        "X-CSRF-TOKEN"
                );

        for (JsonNode parameter : parameters) {
            boolean reportId = "reportId".equals(
                    parameter.path("name").asString()
            );

            assertThat(parameter.path("required").booleanValue())
                    .isTrue();

            assertThat(parameter.path("in").asString())
                    .isEqualTo(
                            reportId ? "path" : "header"
                    );

            assertThat(parameter.at("/schema/type").asString())
                    .isEqualTo(
                            reportId ? "integer" : "string"
                    );

            if (reportId) {
                assertThat(
                        parameter.at("/schema/format").asString()
                )
                        .isEqualTo("int64");
            }
        }

        assertThat(
                operation.at("/requestBody/required").booleanValue()
        )
                .isTrue();

        JsonNode requestContent = operation.at(
                "/requestBody/content/application~1json"
        );

        assertThat(requestContent.at("/schema/$ref").asString())
                .endsWith("/AdminReportHandlingRequest");

        JsonSchema requestSchema = responseSchema(
                document,
                requestContent
        );

        assertExamplesMatchSchema(
                requestSchema,
                requestContent.path("examples")
        );

        assertThat(
                operation.at(
                        "/responses/200/headers/Cache-Control/schema/example"
                ).asString()
        )
                .isEqualTo("no-store");

        JsonNode responseContent = successContent(operation);

        assertThat(responseContent.at("/schema/$ref").asString())
                .endsWith("/AdminReportHandlingResponse");

        JsonSchema responseSchema = responseSchema(
                document,
                responseContent
        );

        JsonNode examples = responseContent.path("examples");

        assertThat(examples.isObject())
                .isTrue();

        assertThat(examples.propertyNames())
                .containsExactlyInAnyOrder(
                        "CONTENT_HIDDEN",
                        "CONTENT_ALREADY_HIDDEN"
                );

        assertExamplesMatchSchema(
                responseSchema,
                examples
        );

        for (String outcome : List.of(
                "CONTENT_HIDDEN",
                "CONTENT_ALREADY_HIDDEN"
        )) {
            JsonNode value = examples.path(outcome)
                    .path("value");

            assertThat(value.propertyNames())
                    .containsExactlyInAnyOrder(
                            "reportId",
                            "status",
                            "handledByMemberId",
                            "handledAt",
                            "handlingReason",
                            "handlingOutcome"
                    );

            assertThat(value.path("status").asString())
                    .isEqualTo("RESOLVED");

            assertThat(value.path("handlingOutcome").asString())
                    .isEqualTo(outcome);

            assertThat(value.path("reportId").longValue())
                    .isPositive();

            assertThat(value.path("handledByMemberId").longValue())
                    .isPositive();

            assertThat(value.path("handlingReason").asString())
                    .isNotBlank();

            // 처리 시각 예시가 실제 ISO 8601 시각인지 확인한다.
            Instant.parse(
                    value.path("handledAt").asString()
            );
        }
    }

    @Test
    void 숨김_처리_API는_인증과_대상_상태별_오류를_문서화한다()
            throws Exception {

        JsonNode document = document();

        JsonNode operation = operation(
                document,
                DETAIL_PATH + "/hide-and-resolve",
                "post"
        );

        Map<String, List<String>> expectedErrors = Map.of(
                "400",
                List.of(
                        "INVALID_HANDLING_REASON",
                        "INVALID_REQUEST_BODY",
                        "INVALID_REQUEST_PARAMETER"
                ),
                "401",
                List.of(
                        "AUTHENTICATION_REQUIRED"
                ),
                "403",
                List.of(
                        "ACCESS_DENIED",
                        "ADMIN_REQUIRED",
                        "CSRF_TOKEN_INVALID"
                ),
                "404",
                List.of(
                        "REPORT_NOT_FOUND",
                        "POST_NOT_FOUND",
                        "COMMENT_NOT_FOUND"
                ),
                "409",
                List.of(
                        "REPORT_ALREADY_HANDLED",
                        "POST_DELETED",
                        "COMMENT_DELETED"
                ),
                "415",
                List.of(
                        "HTTP_ERROR"
                )
        );

        for (var entry : expectedErrors.entrySet()) {
            String statusCode = entry.getKey();

            JsonNode response = operation.path("responses")
                    .path(statusCode);

            assertThat(response.isObject())
                    .as("%s 오류 응답이 문서에 있어야 한다", statusCode)
                    .isTrue();

            assertThat(
                    response.at(
                            "/content/application~1json/schema/$ref"
                    ).asString()
            )
                    .endsWith("/ApiErrorResponse");

            String description = response.path("description")
                    .asString();

            for (String errorCode : entry.getValue()) {
                assertThat(description)
                        .as(
                                "%s 응답에 %s 오류를 설명해야 한다",
                                statusCode,
                                errorCode
                        )
                        .contains(errorCode);
            }
        }
    }

    private JsonNode document() throws Exception {
        var response = mockMvc.perform(
                        get("/v3/api-docs")
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();

        JsonNode document = objectMapper.readTree(
                response.getContentAsString()
        );

        Path outputPath = Path.of(
                "build/reports/admin-report-openapi.json"
        );

        Files.createDirectories(
                outputPath.getParent()
        );

        Files.writeString(
                outputPath,
                document.toPrettyString()
        );

        return document;
    }

    private JsonNode operation(
            JsonNode document,
            String path,
            String method
    ) {
        JsonNode operation = document.path("paths")
                .path(path)
                .path(method);

        assertThat(operation.isObject())
                .as("%s %s 작업이 문서에 있어야 한다", method, path)
                .isTrue();

        return operation;
    }

    private JsonNode successContent(
            JsonNode operation
    ) {
        JsonNode content = operation.at(
                "/responses/200/content/application~1json"
        );

        assertThat(content.isObject())
                .as("200 응답의 JSON 문서가 존재해야 한다")
                .isTrue();

        return content;
    }

    private List<String> parameterNames(
            JsonNode parameters
    ) {
        assertThat(parameters.isArray())
                .as("parameters는 배열이어야 한다")
                .isTrue();

        return parameters.valueStream()
                .map(parameter ->
                        parameter.path("name").asString()
                )
                .toList();
    }

    private List<String> strings(
            JsonNode values
    ) {
        assertThat(values.isArray())
                .as("문자열 값 목록은 배열이어야 한다")
                .isTrue();

        return values.valueStream()
                .map(JsonNode::asString)
                .toList();
    }

    private void assertRequiredFields(
            JsonNode schema,
            String schemaName,
            List<String> expectedFields
    ) {
        assertThat(schema.isObject())
                .as("%s 스키마가 존재해야 한다", schemaName)
                .isTrue();

        JsonNode properties = schema.path("properties");

        assertThat(properties.isObject())
                .as("%s의 properties가 존재해야 한다", schemaName)
                .isTrue();

        assertThat(properties.propertyNames())
                .as("%s의 응답 또는 요청 필드", schemaName)
                .containsExactlyInAnyOrderElementsOf(
                        expectedFields
                );

        assertThat(strings(schema.path("required")))
                .as("%s의 필수 필드", schemaName)
                .containsExactlyInAnyOrderElementsOf(
                        expectedFields
                );
    }

    private void assertErrorValue(
            JsonNode value
    ) {
        assertThat(value.isObject())
                .isTrue();

        assertThat(value.propertyNames())
                .containsExactlyInAnyOrder(
                        "code",
                        "message",
                        "fieldErrors"
                );

        assertThat(value.path("code").asString())
                .isNotBlank();

        assertThat(value.path("message").asString())
                .isNotBlank();

        assertThat(value.path("fieldErrors").isArray())
                .isTrue();
    }

    private JsonSchema responseSchema(
            JsonNode document,
            JsonNode content
    ) throws Exception {
        String reference = content.at("/schema/$ref").asString();

        return schema(
                document,
                reference
        );
    }

    private JsonSchema schema(
            JsonNode document,
            String reference
    ) throws Exception {
        assertThat(reference)
                .startsWith(SCHEMA_PREFIX);

        var root = SCHEMA_MAPPER.readTree(
                document.toString()
        );

        /*
         * 생성 문서 내부의 참조만 사용한다.
         * 문서 검증 중 외부 스키마를 조회하지 않는다.
         */
        assertThat(root.findValues("$ref"))
                .allSatisfy(localReference ->
                        assertThat(localReference.asText())
                                .startsWith("#/")
                );

        ObjectNode schemaDocument =
                SCHEMA_MAPPER.createObjectNode();

        schemaDocument.put(
                "$schema",
                JSON_SCHEMA_VERSION
        );

        schemaDocument.set(
                "components",
                root.path("components")
        );

        schemaDocument.put(
                "$ref",
                reference
        );

        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(
                SpecVersion.VersionFlag.V202012
        );

        return factory.getSchema(schemaDocument);
    }

    private ObjectNode exampleObject(
            JsonNode content,
            String exampleName
    ) throws Exception {
        JsonNode value = content.path("examples")
                .path(exampleName)
                .path("value");

        assertThat(value.isObject())
                .as("%s 예시가 JSON 객체여야 한다", exampleName)
                .isTrue();

        return (ObjectNode) SCHEMA_MAPPER.readTree(
                value.toString()
        );
    }

    private void assertExamplesMatchSchema(
            JsonSchema schema,
            JsonNode examples
    ) throws Exception {
        assertThat(examples.isObject())
                .as("문서에 예시 목록이 존재해야 한다")
                .isTrue();

        assertThat(examples.isEmpty())
                .as("문서의 예시 목록이 비어 있으면 안 된다")
                .isFalse();

        for (String exampleName : examples.propertyNames()) {
            JsonNode value = examples.path(exampleName)
                    .path("value");

            assertThat(value.isObject())
                    .as("%s 예시가 JSON 객체여야 한다", exampleName)
                    .isTrue();

            var validationValue = SCHEMA_MAPPER.readTree(
                    value.toString()
            );

            assertThat(schema.validate(validationValue))
                    .as("%s 예시가 생성된 스키마를 만족해야 한다", exampleName)
                    .isEmpty();
        }
    }
}