package io.github.takgeun.iyum.community.post.application;

import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;

public record PostCreateCommand(
        // 작성자 ID는 컨트롤러가 로그인 세션에서 가져온 ID를 서비스의 별도 인자로 전달하도록 할 것
        PostType type,
        PostTopic topic,
        String title,
        String content
) {
}
