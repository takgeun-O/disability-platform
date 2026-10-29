package io.github.takgeun.iyum.member.application;

/**
 * 마이페이지에서 본인이 작성한 콘텐츠의 표시 상태.
 *
 * DB에 저장하는 별도 상태가 아니라,
 * 게시글과 댓글의 현재 상태로부터 계산하는 응답용 값.
 */
public enum MemberContentVisibility {

    // 공개 상태
    VISIBLE,

    // 해당 게시글 또는 댓글 자체가 관리자에 의해 숨겨짐
    HIDDEN,

    // 댓글의 부모 게시글이 관리자에 의해 숨겨짐 (이건 게시글엔 안 쓰고 댓글에만 사용)
    PARENT_HIDDEN
}
