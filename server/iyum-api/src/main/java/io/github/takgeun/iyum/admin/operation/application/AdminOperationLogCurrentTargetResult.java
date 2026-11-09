package io.github.takgeun.iyum.admin.operation.application;

import jakarta.annotation.Nullable;

/**
 * 운영 기록 대상의 현재 저장된 내용 요약.
 * 운영 조치 당시의 본문 스냅샷이 아니다.
 *
 * 게시글 조회 성공 -> 작성자·제목·본문 미리보기 제공
 * 댓글 조회 성공 -> 작성자·본문 미리보기 제공 (title은 null)
 * 숨김·소프트 삭제 대상 -> 조회 가능한 현재 내용 제공
 * 대상 자체가 없음 -> 상위 객체의 currentTarget 전체를 null로 설정 (빈 본문이나 title: null로 대상 자체가 없음을 판단해서는 안된다.)
 */
public record AdminOperationLogCurrentTargetResult(
        AdminOperationLogTargetAuthorResult author,
        @Nullable String title,
        String contentPreview
) {
}
