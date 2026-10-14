package io.github.takgeun.iyum.community.post.api.dto;

import io.github.takgeun.iyum.community.post.api.validation.ValidPostCreateRequest;
import io.github.takgeun.iyum.community.post.application.PostCreateCommand;
import io.github.takgeun.iyum.community.post.domain.PostInputPolicy;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "게시글 작성 요청. 작성자는 로그인 세션에서 결정합니다.")
@ValidPostCreateRequest
public record PostCreateRequest(

        @NotNull(message = "글 유형은 필수입니다.")
        @Schema(
                description = "게시글 유형",
                example = "QUESTION",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        PostType type,

        @NotNull(message = "주제는 필수입니다.")
        @Schema(
                description = "게시글 주제",
                example = "HEARING_AID",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        PostTopic topic,

        @Schema(
                description = "앞뒤 공백 제거 후 1~120자. "
                        + "유니코드 코드 포인트 기준으로 계산합니다.",
                example = "보청기 적응 기간은 얼마나 걸리셨나요?",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String title,

        @Schema(
                description = "공백만 있는 본문은 허용하지 않습니다. "
                        + "유니코드 코드 포인트 기준 최대 10,000자이며 "
                        + "공백과 줄바꿈을 보존합니다.",
                example = "처음 착용한 지 일주일 정도 됐습니다.",
                minLength = 1,
                maxLength = PostInputPolicy.CONTENT_MAX_LENGTH,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String content
) {

    public PostCreateCommand toCommand() {
        return new PostCreateCommand(
                type,
                topic,
                title,
                content
        );
    }

    public PostCreateRequest {
        // 입력값 정리만 수행
        // 잘못된 입력의 판단은 검증기가 담당
        if (title != null) {
            title = title.strip();
        }
    }
}
