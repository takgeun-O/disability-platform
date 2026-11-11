package io.github.takgeun.iyum.admin.member.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** { "reason": "운영 정책 위반으로 이용을 정지합니다." } */
public record AdminMemberModerationRequest(
        @Schema(
                        description =
                                """
                                처리 사유. 필수입니다.
                                null 또는 Java String.isBlank()이면 거부합니다. String.strip()으로 앞뒤 Character.isWhitespace 공백을 제거한 뒤
                                1~500 Unicode 코드 포인트입니다. UTF-16 길이/화면 글자 수 제한이 아닙니다.
                                NBSP는 이 공백 정의에 포함되지 않으며 내부 공백은 합치지 않습니다. reasonType은 사용하지 않습니다.
                                이미 요청한 상태인 경우에도 사유를 검증합니다.
                                """,
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        example = "운영 정책 위반으로 이용을 정지합니다.")
                String reason) {}
