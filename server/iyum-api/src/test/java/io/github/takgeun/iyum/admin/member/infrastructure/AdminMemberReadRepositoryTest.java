package io.github.takgeun.iyum.admin.member.infrastructure;

import io.github.takgeun.iyum.admin.member.application.AdminMemberReadQuery;
import io.github.takgeun.iyum.admin.member.application.AdminMemberSearchType;
import io.github.takgeun.iyum.admin.member.application.AdminMemberSort;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true"
})
@Testcontainers
@Transactional
class AdminMemberReadRepositoryTest {

    private static final Instant BASE_TIME =
            Instant.parse("2026-11-01T10:00:00Z");

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                postgres::getJdbcUrl
        );
        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );
        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );
    }

    @Autowired
    AdminMemberReadRepository repository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean(name = "auditingDateTimeProvider")
    DateTimeProvider dateTimeProvider;

    @Test
    void 회원_ID로_정확히_일치하는_회원만_조회한다() {
        Long targetId = saveActiveUser("TargetUser");
        saveActiveUser("OtherUser");

        Page<Member> result = search(
                AdminMemberSearchType.MEMBER_ID,
                " 00" + targetId + " "
        );

        assertIds(result, targetId);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void 이메일을_정규화한_뒤_정확히_일치하는_회원만_조회한다() {
        Long targetId = saveActiveUser("Lookup");
        saveActiveUser("LookupExtra");

        Page<Member> exact = search(
                AdminMemberSearchType.EMAIL,
                "  LOOKUP@EXAMPLE.COM  "
        );

        assertIds(exact, targetId);
        assertThat(exact.getTotalElements()).isEqualTo(1);

        Page<Member> partial = search(
                AdminMemberSearchType.EMAIL,
                "lookup"
        );

        assertThat(partial.getContent()).isEmpty();
        assertThat(partial.getTotalElements()).isZero();
    }

    @Test
    void 닉네임을_대소문자_구분_없이_부분_검색한다() {
        Long targetId = saveActiveUser("IyumReader");
        saveActiveUser("OtherReader");

        Page<Member> result = search(
                AdminMemberSearchType.NICKNAME,
                "  iYuM  "
        );

        assertIds(result, targetId);
        assertThat(result.getTotalElements()).isEqualTo(1);

        // 이메일 도메인에만 있는 문자열은 닉네임 검색 대상이 아니다.
        Page<Member> emailOnly = search(
                AdminMemberSearchType.NICKNAME,
                "example"
        );

        assertThat(emailOnly.getContent()).isEmpty();
        assertThat(emailOnly.getTotalElements()).isZero();
    }

    @Test
    void 닉네임의_밑줄을_와일드카드가_아닌_문자로_검색한다() {
        Long targetId = saveActiveUser("Alpha_Beta");
        saveActiveUser("AlphaXBeta");
        saveActiveUser("AlphaBeta");

        Page<Member> result = search(
                AdminMemberSearchType.NICKNAME,
                "alpha_"
        );

        assertIds(result, targetId);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "!", "!%_"})
    void 특수문자_검색어가_전체_회원을_일치시키지_않는다(
            String keyword
    ) {
        saveActiveUser("Alpha_Beta");
        saveActiveUser("AlphaXBeta");

        Page<Member> result = search(
                AdminMemberSearchType.NICKNAME,
                keyword
        );

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void 검색_상태_권한을_AND로_결합하고_개수에도_적용한다() {
        Long firstId = saveActiveUser("MatchOne");
        Long secondId = saveActiveUser("MatchTwo");

        Long adminId = saveMember(
                "MatchAdmin",
                MemberStatus.ACTIVE,
                MemberRole.ADMIN,
                BASE_TIME
        );

        saveMember(
                "MatchStopped",
                MemberStatus.SUSPENDED,
                MemberRole.USER,
                BASE_TIME
        );

        saveActiveUser("OtherUser");

        AdminMemberReadQuery firstPageQuery =
                new AdminMemberReadQuery(
                        0,
                        1,
                        AdminMemberSearchType.NICKNAME,
                        "match",
                        MemberStatus.ACTIVE,
                        MemberRole.USER,
                        AdminMemberSort.LATEST
                );

        Page<Member> firstPage =
                repository.findMembers(firstPageQuery);

        // 모두 같은 가입 시각이므로 ID 내림차순이다.
        assertIds(firstPage, secondId);
        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.hasNext()).isTrue();

        Page<Member> secondPage = repository.findMembers(
                new AdminMemberReadQuery(
                        1,
                        1,
                        AdminMemberSearchType.NICKNAME,
                        "match",
                        MemberStatus.ACTIVE,
                        MemberRole.USER,
                        AdminMemberSort.LATEST
                )
        );

        assertIds(secondPage, firstId);
        assertThat(secondPage.getTotalElements()).isEqualTo(2);
        assertThat(secondPage.hasNext()).isFalse();

        // 권한 필터만 적용해도 정상적으로 동작해야 한다.
        Page<Member> admins = repository.findMembers(
                new AdminMemberReadQuery(
                        0, 20, null, null,
                        null, MemberRole.ADMIN, null
                )
        );

        assertIds(admins, adminId);
        assertThat(admins.getTotalElements()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(AdminMemberSort.class)
    void 가입_시각과_ID로_정렬한_뒤_페이지를_적용한다(
            AdminMemberSort sort
    ) {
        // ID 순서와 가입 시각 순서를 다르게 만든다.
        Long newestId = saveMember(
                "NewestUser",
                MemberStatus.ACTIVE,
                MemberRole.USER,
                BASE_TIME.plusSeconds(60)
        );

        Long olderFirstId = saveActiveUser("OlderFirst");
        Long olderSecondId = saveActiveUser("OlderSecond");

        List<Long> expected = sort == AdminMemberSort.LATEST
                ? List.of(newestId, olderSecondId, olderFirstId)
                : List.of(olderFirstId, olderSecondId, newestId);

        Page<Member> firstPage = repository.findMembers(
                pageQuery(0, 2, sort)
        );

        assertThat(firstPage.getContent())
                .extracting(Member::getId)
                .containsExactlyElementsOf(expected.subList(0, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.hasPrevious()).isFalse();

        Page<Member> lastPage = repository.findMembers(
                pageQuery(1, 2, sort)
        );

        assertIds(lastPage, expected.get(2));
        assertThat(lastPage.getTotalElements()).isEqualTo(3);
        assertThat(lastPage.hasNext()).isFalse();
        assertThat(lastPage.hasPrevious()).isTrue();

        Page<Member> outsidePage = repository.findMembers(
                pageQuery(2, 2, sort)
        );

        assertThat(outsidePage.getContent()).isEmpty();
        assertThat(outsidePage.getNumber()).isEqualTo(2);
        assertThat(outsidePage.getSize()).isEqualTo(2);
        assertThat(outsidePage.getTotalElements()).isEqualTo(3);
        assertThat(outsidePage.getTotalPages()).isEqualTo(2);
        assertThat(outsidePage.hasNext()).isFalse();
    }

    @Test
    void 전체_상태를_조회하고_각_상태로_필터링할_수_있다() {
        MemberStatus[] statuses = MemberStatus.values();
        List<Long> ids = new ArrayList<>();

        for (int i = 0; i < statuses.length; i++) {
            ids.add(saveMember(
                    "StateUser" + i,
                    statuses[i],
                    MemberRole.USER,
                    BASE_TIME
            ));
        }

        Page<Member> all = repository.findMembers(
                pageQuery(0, 20, AdminMemberSort.LATEST)
        );

        assertThat(all.getContent())
                .extracting(Member::getId)
                .containsExactlyElementsOf(ids.reversed());

        assertThat(all.getTotalElements())
                .isEqualTo(statuses.length);

        for (int i = 0; i < statuses.length; i++) {
            MemberStatus status = statuses[i];
            Long memberId = ids.get(i);

            Page<Member> filtered = repository.findMembers(
                    new AdminMemberReadQuery(
                            0, 20, null, null,
                            status, null, null
                    )
            );

            assertIds(filtered, memberId);
            assertThat(filtered.getTotalElements()).isEqualTo(1);

            // 상세도 ACTIVE 여부와 무관하게 조회한다.
            Member detail = repository.findMemberById(memberId)
                    .orElseThrow();

            assertThat(detail.getId()).isEqualTo(memberId);
            assertThat(detail.getStatus()).isEqualTo(status);
        }
    }

    @Test
    void 탈퇴_회원은_ID로_조회되지만_과거_개인정보로는_검색되지_않는다() {
        Long memberId = saveMember(
                "WithdrawnUser",
                MemberStatus.DELETED,
                MemberRole.USER,
                BASE_TIME
        );

        Member detail = repository.findMemberById(memberId)
                .orElseThrow();

        assertThat(detail.getStatus()).isEqualTo(MemberStatus.DELETED);
        assertThat(detail.getEmail()).isNull();
        assertThat(detail.getNickname()).isNull();
        assertThat(detail.getWithdrawnAt())
                .isEqualTo(BASE_TIME.plusSeconds(120));

        Page<Member> byId = search(
                AdminMemberSearchType.MEMBER_ID,
                memberId.toString()
        );

        assertIds(byId, memberId);
        assertThat(byId.getTotalElements()).isEqualTo(1);

        Page<Member> byOldEmail = search(
                AdminMemberSearchType.EMAIL,
                "withdrawnuser@example.com"
        );

        assertThat(byOldEmail.getContent()).isEmpty();
        assertThat(byOldEmail.getTotalElements()).isZero();

        Page<Member> byOldNickname = search(
                AdminMemberSearchType.NICKNAME,
                "WithdrawnUser"
        );

        assertThat(byOldNickname.getContent()).isEmpty();
        assertThat(byOldNickname.getTotalElements()).isZero();
    }

    @Test
    void 검색_결과가_없으면_빈_페이지를_반환한다() {
        saveActiveUser("ExistingUser");

        Page<Member> result = search(
                AdminMemberSearchType.NICKNAME,
                "NoSuchNickname"
        );

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getTotalPages()).isZero();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.hasPrevious()).isFalse();
    }

    @Test
    void 존재하지_않는_회원의_상세는_빈_Optional을_반환한다() {
        assertThat(repository.findMemberById(Long.MAX_VALUE))
                .isEmpty();
    }

    private Page<Member> search(
            AdminMemberSearchType searchType,
            String keyword
    ) {
        return repository.findMembers(
                new AdminMemberReadQuery(
                        0,
                        20,
                        searchType,
                        keyword,
                        null,
                        null,
                        AdminMemberSort.LATEST
                )
        );
    }

    private AdminMemberReadQuery pageQuery(
            int page,
            int size,
            AdminMemberSort sort
    ) {
        return new AdminMemberReadQuery(
                page,
                size,
                null,
                null,
                null,
                null,
                sort
        );
    }

    private Long saveActiveUser(String nickname) {
        return saveMember(
                nickname,
                MemberStatus.ACTIVE,
                MemberRole.USER,
                BASE_TIME
        );
    }

    /**
     * 테스트에서 사용하는 닉네임은 영문·숫자·밑줄로 구성한다.
     * 이메일은 닉네임에서 생성해 테스트 내에서 구분한다.
     */
    private Long saveMember(
            String nickname,
            MemberStatus status,
            MemberRole role,
            Instant createdAt
    ) {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(createdAt));

        Member member = Member.createPending(
                nickname.toLowerCase(Locale.ROOT) + "@example.com",
                "test-only-password-hash",
                nickname
        );

        if (status != MemberStatus.PENDING) {
            member.activeAfterEmailVerification();
        }

        Instant withdrawnAt =
                status == MemberStatus.WITHDRAWN
                        || status == MemberStatus.DELETED
                        ? createdAt.plusSeconds(120)
                        : null;

        if (status == MemberStatus.DELETED) {
            // 실제 도메인 메서드로 개인정보를 제거한다.
            member.withdraw(withdrawnAt);
        }

        entityManager.persist(member);
        entityManager.flush();

        Long memberId = member.getId();

        // 현재 운영 코드에 없는 상태 전환·권한 변경은
        // 테스트 데이터 구성 목적으로만 SQL을 사용한다.
        // WITHDRAWN은 상태와 탈퇴 시각을 함께 변경해야 한다.
        jdbcTemplate.update(
                """
                update members
                   set status = ?,
                       role = ?,
                       withdrawn_at = ?
                 where id = ?
                """,
                status.name(),
                role.name(),
                withdrawnAt == null
                        ? null
                        : Timestamp.from(withdrawnAt),
                memberId
        );

        // SQL 변경 전의 엔티티가 1차 캐시에 남지 않도록 한다.
        entityManager.clear();

        return memberId;
    }

    private void assertIds(Page<Member> result, Long... expectedIds) {
        assertThat(result.getContent())
                .extracting(Member::getId)
                .containsExactly(expectedIds);
    }
}