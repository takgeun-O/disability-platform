package io.github.takgeun.iyum.community.post.application;

import jakarta.annotation.Nullable;

/**
 * 검색어가 발견된 위치를 중심으로
 * 앞뒤 문맥을 최대 40코드포인트씩 잘라서 검색 결과 미리보기를 만드는 코드
 * 단, 검색어를 직접 찾거나 색으로 강조하는 역할은 아님
 * 이미 찾은 검색어 위치를 받아서 주변 내용을 발췌(Excerpt)한다.
 */
final class PostSearchExcerpt {

    private static final int CONTEXT_CODE_POINTS = 40;
    private static final String ELLIPSIS = "…";

    private PostSearchExcerpt() {
    }

    /**
     * 원문에서 [matchStart, matchEnd) 구간과 그 앞뒤 문맥을 반환한다.
     * 두 인덱스는 Java String의 UTF-16 인덱스이며,
     * 문맥의 길이는 유니코드 코드포인트 단위로 계산한다.
     * <p>
     * 입력값: 원문과 검색어가 발견된 구간
     * String content = "새 보청기 후기";
     * //                  01234567
     * <p>
     * // "보청기"의 범위는 [2, 5)
     * content.substring(2, 5); // "보청기"
     */
    static String aroundMatch(

            // 게시글 본문 원문
            String content,

            // 일치하는 구간의 시작 인덱스 포함
            int matchStart,

            // 일치하는 구간의 끝 인덱스 미포함
            int matchEnd
    ) {
        // 검색어 앞뒤로 얼마나 가져올지 계산한다.
        // 원문 시작부터 검색어 직전까지의 코드포인트 수 계산
        // 앞에 100개가 있으면 -> 40개만 가져옴
        // 앞에 10개가 있으면 -> 10개 전부 가져옴
        int beforeCount = Math.min(
                CONTEXT_CODE_POINTS,
                content.codePointCount(0, matchStart)
        );

        // 검색어 끝부터 원문 끝까지 중 최대 40개를 가져온다.
        int afterCount = Math.min(
                CONTEXT_CODE_POINTS,
                content.codePointCount(matchEnd, content.length())
        );

        // 실제로 잘라낼 시작·끝 인덱스를 계산한다.
        // offsetByCodePoints()는 특정 인덱스에서 코드포인트 단위로 이동한 뒤
        // 그 위치의 UTF-16 인덱스를 반환한다.
        int excerptStart =
                // 검색어 시작에서 앞쪽으로 이동
                content.offsetByCodePoints(matchStart, -beforeCount);
        int excerptEnd =
                // 검색어 끝에서 뒤쪽으로 이동
                content.offsetByCodePoints(matchEnd, afterCount);

        // 생략된 부분이 있으면 ELLIPSIS 추가
        String prefix = excerptStart > 0 ? ELLIPSIS : "";
        String suffix = excerptEnd < content.length() ? ELLIPSIS : "";

        // 따라서 발췌 내용은
        // 앞 문맥 최대 40개 + 검색어 전체 + 뒤 문맥 최대 40개
        return prefix
                + content.substring(excerptStart, excerptEnd)
                + suffix;
    }

    /**
     * DB의 문자 위치를 aroundMatch()가 받는 Java 인덱스로 변환하는 역할
     */
    @Nullable
    static String fromBodyMatchPosition(
            String content,
            int bodyMatchPosition,
            int keywordCodePointCount
    ) {
        // 예를 들어 본문이 😀CAFÉ이고 검색어가 café라면 다음처럼 동작
        // bodyMatchPosition == 2 : DB에서 C는 두 번째 문자
        if (bodyMatchPosition == 0) {
            return null;
        }

        // content.offsetByCodePoints(0, 1) == 2 : 이모지를 한 개 건너뛴 Java UTF-16 인덱스
        int matchStart = content.offsetByCodePoints(
                0,
                // offsetByCodePoints()를 쓰는 이유
                // bodyMatchPosition - 1을 곧바로 substring()에 넣으면 인덱스가 1이 되어 이모지 중간을 가리킨다.
                bodyMatchPosition - 1
        );

        // content.offsetByCodePoints(2, 4) == 6 : 원문 CAFÉ의 끝 인덱스
        int matchEnd = content.offsetByCodePoints(
                matchStart,
                keywordCodePointCount
        );

        return aroundMatch(
                content,
                matchStart,
                matchEnd
        );
    }
}
