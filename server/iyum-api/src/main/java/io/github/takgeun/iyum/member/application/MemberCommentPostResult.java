package io.github.takgeun.iyum.member.application;

import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import jakarta.annotation.Nullable;

// 원본 게시글 정보를 담을 결과 객체
// 댓글이 달린 게시글의 제목을 표시하고
// 해당 게시글로 이동하는 데 필요한 정보
// 게시글 본문이나 작성자 정보까지 가져와서 응답에 담지 않음.
public record MemberCommentPostResult(
        Long postId,
        @Nullable String title,
        @Nullable PostType type,
        @Nullable PostTopic topic
) {

    public static MemberCommentPostResult from(Post post) {
        if(post.getDeletedAt() != null) {
            throw new IllegalArgumentException(
                    "삭제된 게시글은 내 댓글의 원본 정보로 반환할 수 없습니다."
            );
        }

        if(post.getHiddenAt() != null) {
            return idOnly(post.getId());
        }

        return new MemberCommentPostResult(
                post.getId(),
                post.getTitle(),
                post.getType(),
                post.getTopic()
        );
    }

    // 숨김 항목에서 원본 게시글의 제목 등을 제외하면서 삭제에 필요한 ID를 남기는 용도
    public static MemberCommentPostResult idOnly(Long postId) {
        return new MemberCommentPostResult(
                postId,
                null,
                null,
                null
        );
    }
}
