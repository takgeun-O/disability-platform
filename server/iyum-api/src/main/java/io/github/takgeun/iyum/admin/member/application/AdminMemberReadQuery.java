package io.github.takgeun.iyum.admin.member.application;

import io.github.takgeun.iyum.member.domain.MemberInputPolicy;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import jakarta.annotation.Nullable;

/**
 * 조회 조건과 검증 구현
 * <p>
 * 검색어 길이
 * - 회원 ID: 숫자로만 구성된 양의 Long, 최대 19자리
 * - 닉네임: 앞뒤 공백 제거 후 최대 100 코드 포인트
 * - 이메일: 기존 이메일 정규화 규칙 적용, 최대 254자
 * 닉네임 검색에 회원가입용 닉네임 검증을 사용하면 안된다는 점 숙지하기.
 * (검색에서는 한 글자나 _ 같은 일부 문자열도 입력할 수 있어야 하기 때문)
 * <p>
 * 참고) 회원 목록·상세 응답 DTO 형태
 * {
 * "items": [
 * {
 * "memberId": 104,
 * "nickname": "이움사용자",
 * "email": "member@example.com",
 * "status": "ACTIVE",
 * "role": "USER",
 * "createdAt": "2026-11-10T01:00:00Z"
 * }
 * ],
 * "page": 0,
 * "size": 20,
 * "totalElements": 1,
 * "totalPages": 1,
 * "hasNext": false,
 * "hasPrevious": false
 * }
 */
public record AdminMemberReadQuery(
        int page,
        int size,
        // MEMBER_ID, NICKNAME, EMAIL
        @Nullable AdminMemberSearchType searchType,
        @Nullable String keyword,
        @Nullable MemberStatus status,
        @Nullable MemberRole role,
        AdminMemberSort sort
) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;

    public static final int MAX_NICKNAME_KEYWORD_CODE_POINTS = 100;
    public static final int MAX_MEMBER_ID_DIGITS = 19;

    // record의 compact constructor
    // record는 선언부에 이미 구성 요소가 있으므로
    // 컴팩트 생성자에서는 매개변수 목록 생략 가능
    public AdminMemberReadQuery {
        validatePage(page, size);

        if (keyword != null) {
            // 참고)
            // keyword는 String 타입(불변 객체)여서
            // strip()이 기존 문자열 자체를 수정하지 않음.
            // 공백을 제거한 문자열을 반환하고
            // 생성자 내부의 keyword가 그 문자열을 가리키도록 바꾸는 것.
            // (원래 입력은 유지하면서 조회 조건 객체에는 검색에 사용할 정리된 값을 담는 방식)
            keyword = keyword.strip();

            if (keyword.isEmpty()) {
                keyword = null;
            }
        }

        if (keyword == null) {
            // 검색 유형만 선택하고 검색어를 입력하지 않은 경우
            // 검색 조건 자체를 적용하지 않는다.
            searchType = null;
        } else {
            if (searchType == null) {
                throw new AdminMemberReadException(
                        AdminMemberReadException.Code.INVALID_REQUEST_PARAMETER
                );
            }

            keyword = normalizeKeyword(searchType, keyword);
        }

        if(sort == null) {
            sort = AdminMemberSort.LATEST;
        }
    }

    public long offset() {
        return (long) page * size;
    }

    /**
     * 회원 ID 검색일 때만 숫자로 변환한 ID를 반환한다.
     * 생성자에서 이미 검증했으므로 여기서는 유효한 값만 변환한다.
     *
     * 이 메서드는 리포지토리에서 회원 ID 검색 조건을 만들 때
     * 문자열 검색어를 Long 타입으로 꺼내기 위한 메서드임.
     */
    @Nullable
    public Long memberIdKeyword() {
        // 검색어가 회원 ID 타입일 때만 아래 로직 수행
        if(searchType != AdminMemberSearchType.MEMBER_ID) {
            return null;
        }

        return Long.valueOf(keyword);
    }

    private static String normalizeKeyword(
            AdminMemberSearchType searchType,
            String keyword
    ) {
        return switch (searchType) {
            case MEMBER_ID -> normalizeMemberId(keyword);
            case NICKNAME -> normalizeNicknameKeyword(keyword);
            case EMAIL -> normalizeEmailKeyword(keyword);
        };
    }

    private static String normalizeEmailKeyword(String keyword) {
        try {
            // 기존 가입·로그인에서 사용하는 정규화 규칙을 재사용한다.
            return MemberInputPolicy.normalizeEmail(keyword);
        } catch (IllegalArgumentException exception) {
            throw invalidKeyword();
        }
    }

    private static String normalizeNicknameKeyword(String keyword) {
        int codePointCount = keyword.codePointCount(
                0,
                keyword.length()
        );

        if(codePointCount > MAX_NICKNAME_KEYWORD_CODE_POINTS) {
            throw invalidKeyword();
        }

        return keyword;
    }

    private static String normalizeMemberId(String keyword) {
        if (keyword.length() > MAX_MEMBER_ID_DIGITS
                || !keyword.matches("[0-9]+")) {
            throw invalidKeyword();
        }

        long memberId;

        try {
            // 문자열을 정수형 long값으로 변환
            // 검색어를 문자열로 받으니까 변환이 필요한 것
            memberId = Long.parseLong(keyword);
        } catch (NumberFormatException exception) {
            throw invalidKeyword();
        }

        if(memberId <= 0) {
            throw invalidKeyword();
        }

        // 예: "00104" -> "104"
        return Long.toString(memberId);
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new AdminMemberReadException(
                    AdminMemberReadException.Code.INVALID_PAGE_REQUEST
            );
        }

        // int 곱셈 오버플로우 방지
        long offset = (long) page * size;

        if (offset > Integer.MAX_VALUE) {
            throw new AdminMemberReadException(
                    AdminMemberReadException.Code.INVALID_PAGE_REQUEST
            );
        }
    }

    private static AdminMemberReadException invalidKeyword() {
        return new AdminMemberReadException(
                AdminMemberReadException.Code.INVALID_SEARCH_KEYWORD
        );
    }
}
