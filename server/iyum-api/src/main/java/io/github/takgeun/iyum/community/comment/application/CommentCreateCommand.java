package io.github.takgeun.iyum.community.comment.application;

// 서비스 입력 객체 생성
// 댓글 작성에 필요한 본문을 서비스로 전달
// 이후 서비스 호출은 다음 형태로 구성
//commentCreateService.create(
//        principal.getMemberId(),
//        postId,
//        request.toCommand()
//);
// 각 값의 출처가 달라서 구분해 전달할 것
// 작성자 ID : 로그인 세션
// 게시글 ID : 요청 URL
// 댓글 본문 : 요청 JSON
public record CommentCreateCommand(
        String content
) {
}
