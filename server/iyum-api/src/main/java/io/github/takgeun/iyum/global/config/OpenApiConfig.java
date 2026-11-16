package io.github.takgeun.iyum.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.util.List;

/**
 * IYUM의 API 설명서를 만들고, 자동 생성된 설명서에서 부족하거나 잘못 표현될 수 있는 부분을 보완하는 설정
 *
 * OpenAPI 문서는 Swagger UI가 보여주는 설명서의 바탕이 된다.
 * 이 클래스는 이러한 문서를 설정한다. (실제 처리나 로직을 실행하는 코드는 아님)
 *
*/
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    /**
     * API 문서의 제목·버전·소개 설정
     * 실제 서비스의 정책을 문서로 안내한다. (실제로 동작 X)
     */
    @Bean
    public OpenAPI iyumOpenApi() {
        // API 문서의 기본 정보 설정
        // Swagger UI 상단에 표시할 제목과 설명
        return new OpenAPI().info(
                new Info()
                        .title("IYUM API")
                        .version("v1")
                        .description(
                                "IYUM 서비스의 회원 및 인증 API. 회원가입 후 PENDING 상태이며, "
                                        + "이메일 인증으로 ACTIVE가 된 후 사용자가 직접 로그인합니다. "
                                        + "이메일 인증 성공 후 자동 로그인하지 않습니다. "
                                        + "로그인은 세션 쿠키 방식이며, 현재 회원 조회와 로그아웃을 제공합니다."
                        )
        );
    }

    /**
     * 일부 객체 필드가 객체 또는 null일 수 있음을 명시
     */
    @Bean
    public OpenApiCustomizer adminReportNullableObjectsCustomizer() {

        return openApi -> {
            if (openApi.getComponents() == null
                    || openApi.getComponents().getSchemas() == null) {
                return;
            }
            var schemas = openApi.getComponents().getSchemas();

            // 대상 필드 지정
            // AdminReportItemResponse의 currentAuthor에는
            // AdminReportTargetAuthorResponse 형태의 객체 또는 null이 올 수 있다.
            nullableReportProperty(
                    schemas.get("AdminReportItemResponse"),
                    "currentAuthor",
                    "AdminReportTargetAuthorResponse"
            );
            nullableReportProperty(
                    schemas.get("AdminReportDetailResponse"),
                    "currentTarget",
                    "AdminReportCurrentTargetResponse"
            );
        };
    }

    /**
     * 신고 상세 조회의 처리 결과는 문자열 enum 또는 명시적 null을 허용한다.
     * 신규 처리 응답의 handlingOutcome 스키마에는 영향을 주지 않는다.
     */
    @Bean
    public OpenApiCustomizer adminReportHandlingOutcomeCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null
                    || openApi.getComponents().getSchemas() == null) {
                return;
            }

            var schemas = openApi.getComponents().getSchemas();

            Schema<?> detailSchema = schemas.get(
                    "AdminReportDetailResponse"
            );

            // 신고 조회 컨트롤러가 없는 문서 테스트에서는 생략한다.
            if (detailSchema == null
                    || detailSchema.getProperties() == null) {
                return;
            }

            Schema<?> original = detailSchema.getProperties()
                    .get("handlingOutcome");

            if (original == null) {
                return;
            }

            Schema<String> outcomeSchema = new Schema<>();

            outcomeSchema.addType("string");

            outcomeSchema.setEnum(
                    List.of(
                            "CONTENT_HIDDEN",
                            "CONTENT_ALREADY_HIDDEN",
                            "NO_CONTENT_ACTION",
                            "LEGACY_UNKNOWN"
                    )
            );

            Schema<Object> nullSchema = new Schema<>();

            nullSchema.addType("null");

            /*
             * 문자열 enum 조건과 null 조건을 별도 분기로 구성한다.
             * 기존 스키마의 enum을 바깥에 남기지 않도록
             * 새 스키마로 교체한다.
             */
            Schema<Object> nullableOutcomeSchema = new Schema<>();

            nullableOutcomeSchema.setDescription(
                    original.getDescription()
            );

            nullableOutcomeSchema.addAnyOfItem(
                    outcomeSchema
            );

            nullableOutcomeSchema.addAnyOfItem(
                    nullSchema
            );

            detailSchema.addProperty(
                    "handlingOutcome",
                    nullableOutcomeSchema
            );
        };
    }

    private void nullableReportProperty(
            Schema<?> owner,
            String propertyName,
            String referenceName
    ) {
        // Some documentation slices do not include the report controllers.
        if (owner == null || owner.getProperties() == null) {
            return;
        }

        Schema<?> original = owner.getProperties().get(propertyName);

        if (original == null) {
            return;
        }

        Schema<Object> union = new Schema<>();

        union.setDescription(original.getDescription());

        union.addAnyOfItem(
                new Schema<>()
                        // 문서에 정의된 다른 스키마 참조
                        .$ref("#/components/schemas/" + referenceName)
        );

        Schema<Object> nullValue = new Schema<>();

        // JSON의 null값 허용
        nullValue.addType("null");

        union.addAnyOfItem(nullValue);
        owner.addProperty(propertyName, union);
    }

    // Swagger에 로그아웃 문서 추가하기
    // 로그아웃은 Security 필터(SecurityConfig)가 처리하므로 OpenApiCustomizer로 문서에 명시적으로 추가한다.
    @Bean
    public OpenApiCustomizer logoutOpenApiCustomizer() {
        return openApi -> {
            Content errorContent = new Content()
                    .addMediaType(
                            "application/json",
                            new MediaType().schema(
                                    new Schema<>()
                                            .$ref(
                                                    "#/components/schemas/ApiErrorResponse"
                                            )
                            )
                    );

            ApiResponses responses = new ApiResponses()
                    .addApiResponse(
                            "204",
                            new ApiResponse()
                                    .description(
                                            "로그아웃 완료. 응답 본문은 없습니다."
                                    )
                    )
                    .addApiResponse(
                            "403",
                            new ApiResponse()
                                    .description(
                                            "CSRF 토큰이 없거나 유효하지 않습니다."
                                    )
                                    .content(errorContent)
                    );

            Operation operation = new Operation()
                    .operationId("logout")
                    .addTagsItem("인증")
                    .summary("로그아웃")
                    .description(
                            "현재 세션을 종료합니다. "
                                    + "세션 쿠키와 유효한 CSRF 토큰이 필요합니다. "
                                    + "로그인 상태가 아니어도 CSRF 검증에 성공하면 "
                                    + "204를 반환합니다."
                    )
                    .addParametersItem(
                            new HeaderParameter()
                                    .name("X-CSRF-TOKEN")
                                    .required(true)
                                    .description(
                                            "GET /api/v1/auth/csrf에서 받은 토큰"
                                    )
                                    .schema(new StringSchema())
                    )
                    .responses(responses);

            if (openApi.getPaths() == null) {
                openApi.setPaths(new Paths());
            }

            openApi.getPaths().addPathItem(
                    "/api/v1/auth/logout",
                    new PathItem().post(operation)
            );
        };
    }
}
