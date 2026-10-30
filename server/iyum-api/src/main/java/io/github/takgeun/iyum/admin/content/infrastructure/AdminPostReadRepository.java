package io.github.takgeun.iyum.admin.content.infrastructure;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.github.takgeun.iyum.admin.content.application.AdminPostReadQuery;
import io.github.takgeun.iyum.admin.content.application.AdminPostStatus;
import io.github.takgeun.iyum.community.post.domain.Post;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static io.github.takgeun.iyum.community.post.domain.QPost.post;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true) // 모든 쓰기 작업을 무조건 차단하는 보안 장치는 아님
public class AdminPostReadRepository {

    // QueryDSL로 조회 쿼리를 만드는 도구
    // 여기서부터 사용하는 QPost.post는 실제 게시글 데이터가 아님.
    // Post 엔티티를 바탕으로 생성한 쿼리 작성용 객체임
    // post.title, post.createdAt 등 이렇게 Java 코드로 참조할 수 있게 해준다.
    private final JPAQueryFactory queryFactory;

    /**
     * 페이지와 정렬 정보 만들기
     *
     * query는 관리자가 선택한 검색 조건을 담은 객체(페이지 번호·페이지 크기·상태·검색어)
     */
    public Page<Post> findPosts(AdminPostReadQuery query) {

        // 페이지 정보 생성
        // 다만 주의할 점은 PageRequest의 정렬 설정은 QueryDSL 쿼리에 자동 적용되지 않음.
        // 실제 조회 정렬은 아래 .orderBy()로 직접 지정해야 한다.
        Pageable pageable = PageRequest.of(
                query.page(),
                query.size(),
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        // 검색 조건을 한 번 만들어두기
        // BooleanExpression: 참 또는 거짓을 판단하는 쿼리 조건을 표현하는 객체
        // Java의 boolean처럼 지금 즉시 판단하는 값은 아님
        // 예를 들어 post.deletedAt.isNull() 표현식은
        // DB에서 실행할 다음 조건을 표현한다.
        // deleted_at IS NULL
        BooleanExpression statusCondition =
                statusCondition(query.status());

        BooleanExpression keywordCondition =
                titleContains(query.keyword());

        // 조건에 맞는 전체 게시글 개수 조회
        Long count = queryFactory
                .select(post.count())
                .from(post)
                // 상태 조건과 검색어 조건 적용
                // where에 여러 개 전달하면 AND로 결합된다. (null인 조건은 제외)
                // 따라서 상태가 ALL이고, 검색어는 null이면 두 조건 모두 제외되어 상태·제목 제한 없이 개수를 세게 된다.
                .where(
                        statusCondition,
                        keywordCondition
                )
                // 단일 결과 반환
                .fetchOne();

        // 조회 결과가 null일 경우 0으로 처리하는 방어 코드
        // 다만 GROUP BY가 없는 일반적인 COUNT 조회는 검색 결과가 없어도
        // 보통 null 대신 0 반환하긴 함.
        long total = count == null ? 0L : count;

        // 요청한 페이지에 데이터가 없다면 일찍 반환한다.
        // offset은 앞에서 건너뛸 게시글 개수를 의미한다.
        // offset = 페이지 번호 * 페이지 크기
        if(pageable.getOffset() >= total) {
            return new PageImpl<>(
                    List.of(),  // 실제_게시글_목록
                    pageable,   // 페이지_요청_정보
                    total       // 전체_검색_결과_개수
            );
        }

        // 실제 게시글 목록과 작성자 조회
        List<Post> content = queryFactory
                .selectFrom(post)
                .join(post.author).fetchJoin()
                .where(
                        statusCondition,
                        keywordCondition
                )
                .orderBy(
                        post.createdAt.desc(),
                        post.id.desc()
                )
                // 앞에서 몇 개를 건너뛰고 최대 몇 개를 가져올지 지정
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                // 쿼리를 실행하고 결과를 List<Post>로 반환
                // 조회 결과가 없으면 빈 목록 반환
                .fetch();

        // 게시글 목록과 페이지 정보, 전체 개수를 묶어서 호출자에게 전달
        return new PageImpl<>(
                content,
                pageable,
                total
        );
    }

    // 관리자 상세 조회
    public Optional<Post> findPostById(Long postId) {
        Post result = queryFactory
                .selectFrom(post)
                .join(post.author).fetchJoin()
                .where(post.id.eq(postId))
                .fetchOne();

        // fetchOne()은 결과가 없으면 null 반환하고
        // 이것을 Optional.empty()로 변환한다.
        return Optional.ofNullable(result);
    }

    // 상태별 조건 분기
    @Nullable
    private BooleanExpression statusCondition(
            AdminPostStatus status
    ) {
        return switch (status) {
            case ALL -> null;   // 상태 검색 조건을 추가하지 않는다는 의미 (오류가 아님)

            case VISIBLE -> post.deletedAt.isNull()
                    .and(post.hiddenAt.isNull());

            case HIDDEN -> post.deletedAt.isNull()
                    .and(post.hiddenAt.isNotNull());

            case DELETED -> post.deletedAt.isNotNull();
        };
    }

    // 제목 부분 검색
    @Nullable
    private BooleanExpression titleContains(
            @Nullable String keyword
    ) {
        // 검색어가 없으면 제목 조건을 추가하지 않는다.
        if(keyword == null) {
            return null;
        }

        // 두 가지 작업을 한다.
        // 1. 검색어를 소문자로 바꾼다.
        // 2. LIKE 검색에서 특별한 의미를 가진 문자를 일반 문자로 처리한다.
        // 여기서 !는 이스케이프 문자로 사용한다.
        String escaped = keyword
                .toLowerCase(Locale.ROOT)   // 서버의 기본 언어 설정에 영향을 받지 않는 방식으로 소문자 변환 가능
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");

        return post.title
                .lower()
                .like("%" + escaped + "%", '!');
    }
}