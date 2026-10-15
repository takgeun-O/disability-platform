package io.github.takgeun.iyum.community.post.application;

import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;

public record PostUpdateCommand(
        PostType type,
        PostTopic topic,
        String title,
        String content
) {

    // 여기서 작성자 ID는 넣지 않는다.
    // 컨트롤러에서 로그인 세션의 회원ID를 가져와서 서비스로 전달할 것
}
