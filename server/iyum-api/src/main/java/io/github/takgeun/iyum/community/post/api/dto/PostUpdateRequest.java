package io.github.takgeun.iyum.community.post.api.dto;

import io.github.takgeun.iyum.community.post.api.validation.ValidPostUpdateRequest;
import io.github.takgeun.iyum.community.post.application.PostUpdateCommand;
import io.github.takgeun.iyum.community.post.domain.PostInputPolicy;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(
        description = "게시글 수정 요청. "
                + "수정할 유형·주제·제목·본문을 모두 전달합니다."
)
@ValidPostUpdateRequest
public record PostUpdateRequest(
        @NotNull(message = "글 유형은 필수입니다.")
        @Schema(
                description = "게시글 유형",
                example = "REVIEW",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        PostType type,

        @NotNull(message = "주제는 필수입니다.")
        @Schema(
                description = "게시글 주제",
                example = "COCHLEAR_IMPLANT",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        PostTopic topic,

        @Schema(
                description = "앞뒤 공백 제거 후 1~120자. "
                        + "유니코드 코드 포인트 기준으로 계산합니다.",
                example = "인공와우 사용 후기",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String title,

        @Schema(
                description = "공백만 있는 본문은 허용하지 않습니다. "
                        + "유니코드 코드 포인트 기준 최대 10,000자이며 "
                        + "공백과 줄바꿈을 보존합니다.",
                example = "사용 경험을 추가하여 내용을 수정했습니다.",
                minLength = 1,
                maxLength = PostInputPolicy.CONTENT_MAX_LENGTH,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String content
) {

    public PostUpdateRequest {
        // 제목의 앞뒤 공백만 정리한다.
        // 유효성 검사는 검증기가 담당한다.
        if(title != null) {
            title = title.strip();
        }
    }

    public PostUpdateCommand toCommand() {
        return new PostUpdateCommand(
                type,
                topic,
                title,
                content
        );
    }

}
