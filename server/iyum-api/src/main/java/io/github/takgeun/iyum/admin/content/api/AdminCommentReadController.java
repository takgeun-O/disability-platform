package io.github.takgeun.iyum.admin.content.api;

import io.github.takgeun.iyum.admin.content.api.dto.AdminCommentListResponse;
import io.github.takgeun.iyum.admin.content.application.*;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.Explode;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.enums.ParameterStyle;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(
        value = "/api/v1/admin/posts/{postId}/comments",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class AdminCommentReadController {

    private final AdminCommentReadService commentReadService;

    @Parameters({
            @Parameter(
                    name = "status",
                    in = ParameterIn.QUERY,
                    description = """
                        기존 단일 상태 필터.
                        생략하거나 빈 문자열이면 전체 조회.
                        statuses와 동시에 전달할 수 없습니다.
                        """,
                    schema = @Schema(
                            type = "string",
                            allowableValues = {
                                    "ALL", "VISIBLE", "HIDDEN", "DELETED"
                            }
                    )
            ),
            @Parameter(
                    name = "statuses",
                    in = ParameterIn.QUERY,
                    description = """
                        다중 상태 필터.
                        예: statuses=VISIBLE&statuses=HIDDEN
                        빈 값과 ALL은 허용하지 않습니다.
                        status와 동시에 전달할 수 없습니다.
                        """,
                    style = ParameterStyle.FORM,
                    explode = Explode.TRUE,
                    array = @ArraySchema(
                            schema = @Schema(
                                    type = "string",
                                    allowableValues = {
                                            "VISIBLE", "HIDDEN", "DELETED"
                                    }
                            )
                    )
            )
    })
    // 정렬은 저장소에서 고정하므로 이 API에서 sort 파라미터를 추가하지 않음.
    @GetMapping
    public ResponseEntity<AdminCommentListResponse> getComments(
            @AuthenticationPrincipal MemberPrincipal principal,

            @PathVariable("postId") Long postId,

            @Parameter(hidden = true)
            @RequestParam MultiValueMap<String, String> parameters,

            @RequestParam(
                    name = "keyword",
                    required = false
            ) String keyword,

            @RequestParam(
                    name = "page",
                    defaultValue = "0"
            ) int page,

            @RequestParam(
                    name = "size",
                    defaultValue = "20"
            ) int size

    ) {
        requirePrincipal(principal);

        AdminCommentReadQuery query = AdminCommentReadQuery.ofStatuses(
                AdminContentStatusRequestParser.commentStatuses(parameters),
                keyword,
                page,
                size
        );

        AdminCommentListResult result = commentReadService.getComments(
                principal.getMemberId(),
                principal.getCredentialVersion(),
                postId,
                query
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AdminCommentListResponse.from(result));
    }

    private void requirePrincipal(MemberPrincipal principal) {
        if(principal == null) {
            throw new AdminContentReadException(
                    AdminContentReadException.Code.AUTHENTICATION_REQUIRED
            );
        }
    }
}
