package io.github.takgeun.iyum.community.post.application;

public record PostCreateResult(
        // 컨트롤러가 이 결과를 기존 PostCreateResponse로 변환
        Long postId
) {
}
