package io.github.takgeun.iyum.admin.member.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.takgeun.iyum.admin.member.application.AdminMemberItemResult;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import jakarta.annotation.Nullable;

import java.time.Instant;

/**
 * 목록 항목 응답 DTO
 */
// 이메일이 없어도 응답에 필드를 남겨야 한다. -> JsonInclude.Include.ALWAYS
// 사실 프로젝트 전역에서 별도 설정을 안했으면
// 기본적으로 DTO에서 null 필드도 포함한다.
// 굳이 이 설정을 한 이유는
// "전역에서 null을 제외하도록 설정했더라도 이 DTO에서는 null 필드도 포함하겠다"
// 라는 의도를 명시하기 위함임.
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "관리자 회원 목록 항목. 인증 비밀값은 제공하지 않음")
public record AdminMemberItemResponse(
        @Schema(description = "회원 식별자", requiredMode = Schema.RequiredMode.REQUIRED, example = "42")
        Long memberId,
        @Schema(description = "표시 이름. WITHDRAWN과 DELETED는 탈퇴한 회원", requiredMode = Schema.RequiredMode.REQUIRED, example = "이움회원")
        String nickname,
        @Schema(description = "현재 저장된 이메일. DELETED는 null, WITHDRAWN은 저장된 값을 반환. null이어도 필드 포함", requiredMode = Schema.RequiredMode.REQUIRED, example = "member@example.com", types = {"string", "null"}, nullable = true)
        @Nullable String email,
        @Schema(description = "회원 상태. WITHDRAWN은 탈퇴·정리 대기(예약 상태), DELETED는 탈퇴 완료(회원 행 유지, 물리 삭제 아님). 모든 상태를 관리자 조회에 포함", requiredMode = Schema.RequiredMode.REQUIRED, example = "ACTIVE")
        MemberStatus status,
        @Schema(description = "현재 역할", requiredMode = Schema.RequiredMode.REQUIRED, example = "USER")
        MemberRole role,
        @Schema(description = "가입 시각. UTC ISO 8601", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-11-10T01:00:00Z")
        Instant createdAt
) {

    public static AdminMemberItemResponse from(
            AdminMemberItemResult result
    ) {
        return new AdminMemberItemResponse(
                result.memberId(),
                result.nickname(),
                result.email(),
                result.status(),
                result.role(),
                result.createdAt()
        );
    }
}
