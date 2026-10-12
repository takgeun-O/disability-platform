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

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    public OpenAPI iyumOpenApi() {
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
