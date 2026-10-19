package io.github.takgeun.iyum.member.api;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.global.error.ApiErrorResponse;
import io.github.takgeun.iyum.member.api.dto.MemberActivitySummaryResponse;
import io.github.takgeun.iyum.member.application.MemberActivitySummaryResult;
import io.github.takgeun.iyum.member.application.MemberActivitySummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// 게시글·댓글 수 등 활동 정보 조회 API를 따로 마련한 이유
// 성능상 이득을 보려는 게 목적이 아니라, 나중에 다른 곳에서 활동 관련 데이터 조회가 필요할 경우
// 재활용하기 위함.
@RestController
@RequiredArgsConstructor
@Tag(name = "회원")
public class MemberActivityController {

    private final MemberActivitySummaryService memberActivitySummaryService;

    @Operation(
            summary = "내 활동 요약 조회",
            description = """
                    현재 로그인한 회원이 작성한 게시글 수와 댓글 수를 조회합니다.
                    삭제된 게시글과 댓글은 집계에서 제외합니다.
                    삭제된 게시글에 달린 댓글도 제외합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation =
                                            MemberActivitySummaryResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인이 필요하거나 회원을 확인할 수 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "활성 상태가 아닌 회원",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            )
    })
    @GetMapping(
            value = "/api/v1/members/me/activity-summary",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<MemberActivitySummaryResponse> getSummary(
            @Parameter(hidden = true)
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        Long memberId = principal == null
                ? null
                : principal.getMemberId();

        // principal.getMemberId()만 서비스에 전달하고 회원의 현재 활성 상태는 서비스가 DB에서 확인
        MemberActivitySummaryResult result =
                memberActivitySummaryService.getSummary(memberId);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())   // 개인별 응답이니 기존 프로필 조회처럼 no-store 적용
                .body(MemberActivitySummaryResponse.from(result));
    }
}
