package io.github.takgeun.iyum.community.comment.application;

public record CommentUpdateCommand(
        // 회원 ID는 로그인 정보에서
        // 게시글·댓글 ID는 URL에서 받아 서비스에 별도로 전달
        String content
) {
}
