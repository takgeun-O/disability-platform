package io.github.takgeun.iyum.admin.member.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.takgeun.iyum.admin.member.application.AdminMemberDetailResult;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import jakarta.annotation.Nullable;

import java.time.Instant;

/**
 * 상세 응답 DTO
 * 비밀번호 해시·인증 버전·토큰은 당연히 결과 객체와 응답 DTO 어느 쪽에도 포함하지 않는다.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "관리자 회원 상세. nullable 필드도 항상 응답에 포함")
public record AdminMemberDetailResponse(
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
        Instant createdAt,
        @Schema(description = "회원 정보 최종 변경 시각. 최근 로그인 시각이 아님. UTC ISO 8601", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-11-10T02:00:00Z")
        Instant updatedAt,
        @Schema(description = "탈퇴 시각. 기록이 없으면 필드를 생략하지 않고 null. UTC ISO 8601", requiredMode = Schema.RequiredMode.REQUIRED, types = {"string", "null"}, nullable = true)
        @Nullable Instant withdrawnAt
) {

    public static AdminMemberDetailResponse from(
            AdminMemberDetailResult result
    ) {
        return new AdminMemberDetailResponse(
                result.memberId(),
                result.nickname(),
                result.email(),
                result.status(),
                result.role(),
                result.createdAt(),
                result.updatedAt(),
                result.withdrawnAt()
        );
    }
}
