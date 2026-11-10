package io.github.takgeun.iyum.admin.member.infrastructure;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.github.takgeun.iyum.admin.member.application.AdminMemberReadQuery;
import io.github.takgeun.iyum.admin.member.application.AdminMemberSort;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static io.github.takgeun.iyum.member.domain.QMember.member;

/**
 * 관리자 회원 조회용
 * findMembers(query): 검색·필터·정렬·페이지 조건으로 회원 목록 조회
 * findMemberById(memberId): 상태에 관계 없이 회원 단건 조회
 *
 * 관리자 권한 검증은 서비스와 Security에서 처리한다.
 * 단건 조회에 전달할 ID의 유효성도 API·서비스 계층에서 검증할 것.
 */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMemberReadRepository {

    private final JPAQueryFactory queryFactory;

    // 이 메서드 반환값과 같이 엔티티가 Repository와 서비스 사이에서 사용되는 것은 괜찮음.
    // 다만 컨트롤러에서는 반드시 응답 DTO로 변환해서 반환해야겠지..
    public Page<Member> findMembers(AdminMemberReadQuery query) {
        Pageable pageable = PageRequest.of(
                query.page(),
                query.size(),
                pageSort(query.sort())
        );

        // 검색·상태·권한 조건을 AND로 결합
        // null인 조건은 QueryDSL에서 제외한다.
        // 이 조건은 목록과 COUNT 쿼리에 동일하게 사용하므로
        // 검색 결과 개수와 페이지 계산이 일치한다.
        BooleanExpression[] conditions = {
                searchCondition(query),
                statusCondition(query.status()),
                roleCondition(query.role())
        };

        // 목록 조회와 동일한 조건으로 전체 개수를 계산한다.
        Long count = queryFactory
                .select(member.count())
                .from(member)
                .where(conditions)
                .fetchOne();

        long total = count == null ? 0L : count;

        // 검색 결과가 없거나 요청한 페이지가 범위 밖인 경우
        // 전체 검색 결과 개수는 그대로 유지한다.
        if (pageable.getOffset() >= total) {
            return new PageImpl<>(
                    List.of(),
                    pageable,
                    total
            );
        }

        List<Member> content = queryFactory
                .selectFrom(member)
                .where(conditions)
                // 전체 결과를 정렬한 다음
                // 페이지를 적용한다.
                // PageRequest에 정렬을 넣는 것만으로 QueryDSL 쿼리가 정렬되지 않음.
                // 따라서 실제 .orderBy()도 별도로 지정해줘야 한다.
                .orderBy(memberOrder(query.sort()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return new PageImpl<>(
                content,
                pageable,
                total
        );
    }

    /**
     * 관리자 상세 조회
     * ACTIVE 등의 상태 조건을 추가하지 않는다.
     */
    public Optional<Member> findMemberById(Long memberId) {
        Member result = queryFactory
                .selectFrom(member)
                .where(member.id.eq(memberId))
                .fetchOne();

        return Optional.ofNullable(result);
    }

    /**
     * 반환할 Page에 포함되는 정렬 정보
     * QueryDSL의 실제 ORDER BY는 memberOrder()에서 지정
     */
    private Sort pageSort(AdminMemberSort sort) {
        return switch (sort) {
            case LATEST -> Sort.by(
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("id")
            );
            case OLDEST -> Sort.by(
                    Sort.Order.asc("createdAt"),
                    Sort.Order.asc("id")
            );
        };
    }

    @Nullable
    private BooleanExpression searchCondition(
            AdminMemberReadQuery query
    ) {
        if (query.keyword() == null) {
            return null;
        }

        // AdminMemberReadQuery가 검색 유형과 검색어를
        // 검증·정규화한 상태에서 전달한다.
        return switch (query.searchType()) {
            case MEMBER_ID ->
                // 문자열로 들어오는 keyword의 memberId를 Long 타입 변환 후 비교해야함.
                    member.id.eq(query.memberIdKeyword());

            case NICKNAME -> nicknameContains(query.keyword());

            case EMAIL -> member.email.eq(query.keyword());
        };
    }

    @Nullable
    private BooleanExpression statusCondition(
            @Nullable MemberStatus status
    ) {
        if (status == null) {
            return null;
        }

        return member.status.eq(status);
    }

    @Nullable
    private BooleanExpression roleCondition(
            @Nullable MemberRole role
    ) {
        if (role == null) {
            return null;
        }

        return member.role.eq(role);
    }

    /**
     * 닉네임에 검색어가 포함되어 있는지 검사하는 QueryDSL 조건을 만든다.
     * 영문 대소문자를 구분하지 않고, 검색어에 있는 %, _, !도 문자 그대로 찾도록 처리
     */
    private BooleanExpression nicknameContains(String keyword) {
        // !를 LIKE 이스케이프 문자로 사용한다.
        // 이스케이프 문자 자체를 먼저 치환해야 한다.
        // 참고로 SQL Like에서 아래 문자는 특별한 의미를 지닌다.
        // %: 길이가 0 이상인 임의의 문자열
        // _: 임의의 문자 한 개
        String escaped = keyword
                .toLowerCase(Locale.ROOT)   // 서버의 기본 언어 설정에 의존하지 않도록 지정
                // !를 가장 먼저 바꿔야 한다.
                // 반대로 하면 아래와 같은 현상 발생
                // 시작 (검색어에 _ 입력 가정)
                // "_" 검색
                // -> _를 !_로 변경 -> "!_"
                // -> !를 !!로 변경 -> 방금 이스케이프용으로 붙인 !까지 바뀜!
                // -> "!!_"
                // -> 최종 검색 조건 LIKE '%!!_%' ESCAPE '!'
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");

        return member.nickname
                .lower()
                .like("%" + escaped + "%", '!');
    }

    /**
     * memberOrder()는 선택한 정렬 방식에 맞춰
     * QueryDSL이 실제 DB 조회에 사용할 정렬 조건을 만드는 메서드
     */
    // OrderSpecifier: "어떤 필드를 어느 방향으로 정렬할 것인가"를 담는 객체
    // <?>: 정렬 대상의 타입을 특정 하나로 제한하지 않는다.
    private OrderSpecifier<?>[] memberOrder(AdminMemberSort sort) {
        return switch (sort) {
            case LATEST -> new OrderSpecifier<?>[] {
                    member.createdAt.desc(),
                    member.id.desc()
            };

            case OLDEST -> new OrderSpecifier<?>[] {
                    member.createdAt.asc(),
                    member.id.asc()
            };
        };
    }
}
