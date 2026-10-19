package io.github.takgeun.iyum.member.application;

import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;

// 원본 게시글 정보를 담을 결과 객체
// 댓글이 달린 게시글의 제목을 표시하고
// 해당 게시글로 이동하는 데 필요한 정보
// 게시글 본문이나 작성자 정보까지 가져와서 응답에 담지 않음.
public record MemberCommentPostResult(
        Long postId,
        String title,
        PostType type,
        PostTopic topic
) {

    public static MemberCommentPostResult from(Post post) {
        return new MemberCommentPostResult(
                post.getId(),
                post.getTitle(),
                post.getType(),
                post.getTopic()
        );
    }
}
