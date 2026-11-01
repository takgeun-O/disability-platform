package io.github.takgeun.iyum.admin.content.api;

import io.github.takgeun.iyum.admin.content.api.dto.AdminCommentListResponse;
import io.github.takgeun.iyum.admin.content.application.*;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(
        value = "/api/v1/admin/posts/{postId}/comments",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class AdminCommentReadController {

    private final AdminCommentReadService commentReadService;

    // 정렬은 저장소에서 고정하므로 이 API에서 sort 파라미터를 추가하지 않음.
    @GetMapping
    public ResponseEntity<AdminCommentListResponse> getComments(
            @AuthenticationPrincipal MemberPrincipal principal,

            @PathVariable("postId") Long postId,

            @RequestParam(
                    name = "status",
                    defaultValue = "ALL"
            ) AdminCommentStatus status,

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

        AdminCommentReadQuery query = new AdminCommentReadQuery(
                status,
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
