package io.github.takgeun.iyum.admin.content.api;

import io.github.takgeun.iyum.admin.content.api.dto.AdminCommentDetailResponse;
import io.github.takgeun.iyum.admin.content.application.AdminCommentDetailResult;
import io.github.takgeun.iyum.admin.content.application.AdminCommentReadService;
import io.github.takgeun.iyum.admin.content.application.AdminContentReadException;
import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영 기록 등에서 댓글 ID로 현재 댓글 정보를 조회하는 API
 * <p>
 * 기존 게시글별 댓글 목록 조회 컨트롤러와 분리한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(
        value = "/api/v1/admin/comments",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class AdminCommentDetailReadController {

    private final AdminCommentReadService commentReadService;

    @GetMapping("/{commentId}")
    public ResponseEntity<AdminCommentDetailResponse> getComment(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable("commentId") Long commentId
    ) {
        requirePrincipal(principal);

        // 세션의 회원 ID와 인증 버전을 전달한다.
        // 현재 회원 상태·인증 버전·관리자 권한은 서비스에서 검증한다.
        AdminCommentDetailResult result = commentReadService.getComment(
                principal.getMemberId(),
                principal.getCredentialVersion(),
                commentId
        );

        /**
         * {
         *   "commentId": 142,
         *   "postId": 104,
         *   "postStatus": "HIDDEN",
         *   "content": "현재 저장된 댓글 본문입니다.",
         *   "author": {
         *     "memberId": 8,
         *     "nickname": "댓글작성회원",
         *     "withdrawn": false
         *   },
         *   "status": "VISIBLE",
         *   "createdAt": "2026-11-06T01:00:00Z",
         *   "updatedAt": "2026-11-06T02:00:00Z",
         *   "contentUpdatedAt": "2026-11-06T02:00:00Z",
         *   "hiddenAt": null,
         *   "deletedAt": null
         * }
         */
        // 관리자 조회 결과가 브라우저나 중간 캐시에 저장되지 않도록 한다.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AdminCommentDetailResponse.from(result));
    }

    private void requirePrincipal(MemberPrincipal principal) {
        if(principal == null) {
            throw new AdminContentReadException(
                    AdminContentReadException.Code.AUTHENTICATION_REQUIRED
            );
        }
    }
}
