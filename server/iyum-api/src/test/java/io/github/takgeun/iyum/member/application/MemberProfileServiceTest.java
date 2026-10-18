package io.github.takgeun.iyum.member.application;

import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true"
})
@Testcontainers
class MemberProfileServiceTest {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                POSTGRES::getJdbcUrl
        );
        registry.add(
                "spring.datasource.username",
                POSTGRES::getUsername
        );
        registry.add(
                "spring.datasource.password",
                POSTGRES::getPassword
        );
    }

    @Autowired
    MemberProfileService memberProfileService;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Long activeMemberId;
    private Long pendingMemberId;

    @BeforeEach
    void setUp() {
        // 이 클래스의 전용 컨테이너 DB에서만 실행된다.
        // 이 테스트에서는 게시글·댓글·약관 데이터를 만들지 않는다.
        memberRepository.deleteAllInBatch();

        Member activeMember = Member.createPending(
                "profile-active@example.com",
                "test-only-password-hash",
                "프로필회원"
        );
        activeMember.activeAfterEmailVerification();

        activeMemberId = memberRepository
                .saveAndFlush(activeMember)
                .getId();

        Member pendingMember = Member.createPending(
                "profile-pending@example.com",
                "test-only-password-hash",
                "인증대기회원"
        );

        pendingMemberId = memberRepository
                .saveAndFlush(pendingMember)
                .getId();
    }

    @Test
    void 활성_회원의_프로필을_조회한다() {
        MemberProfileResult result =
                memberProfileService.getProfile(activeMemberId);

        assertThat(result.memberId())
                .isEqualTo(activeMemberId);
        assertThat(result.email())
                .isEqualTo("profile-active@example.com");
        assertThat(result.nickname())
                .isEqualTo("프로필회원");
        assertThat(result.createdAt())
                .isNotNull();
    }

    @Test
    void 회원_ID가_null이면_회원_없음으로_처리한다() {
        assertProfileFailure(
                null,
                MemberProfileException.Code.MEMBER_NOT_FOUND
        );
    }

    @Test
    void 존재하지_않는_회원은_조회할_수_없다() {
        // 실제로 저장된 회원을 제거해 없는 ID를 만든다.
        memberRepository.deleteById(activeMemberId);

        assertProfileFailure(
                activeMemberId,
                MemberProfileException.Code.MEMBER_NOT_FOUND
        );
    }

    @Test
    void 인증_대기_회원은_프로필을_조회할_수_없다() {
        assertProfileFailure(
                pendingMemberId,
                MemberProfileException.Code.MEMBER_NOT_ACTIVE
        );
    }

    @Test
    void 재조회하면_DB에서_변경된_닉네임을_반환한다() {
        MemberProfileResult before =
                memberProfileService.getProfile(activeMemberId);

        assertThat(before.nickname())
                .isEqualTo("프로필회원");

        int updated = jdbcTemplate.update(
                """
                UPDATE members
                SET nickname = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                "변경된닉네임",
                activeMemberId
        );

        assertThat(updated).isEqualTo(1);

        MemberProfileResult after =
                memberProfileService.getProfile(activeMemberId);

        assertThat(after.memberId())
                .isEqualTo(activeMemberId);
        assertThat(after.nickname())
                .isEqualTo("변경된닉네임");
        assertThat(after.createdAt())
                .isEqualTo(before.createdAt());
    }

    @Test
    void 재조회하면_DB의_현재_회원_상태를_확인한다() {
        // 최초 조회는 ACTIVE이므로 성공한다.
        memberProfileService.getProfile(activeMemberId);

        // 현재 상태를 다시 읽는지 확인하기 위한 테스트 데이터 변경.
        // 실제 서비스에 ACTIVE → PENDING 기능을 추가하는 것은 아니다.
        int updated = jdbcTemplate.update(
                """
                UPDATE members
                SET status = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                "PENDING",
                activeMemberId
        );

        assertThat(updated).isEqualTo(1);

        assertProfileFailure(
                activeMemberId,
                MemberProfileException.Code.MEMBER_NOT_ACTIVE
        );
    }

    private void assertProfileFailure(
            Long memberId,
            MemberProfileException.Code expectedCode
    ) {
        assertThatThrownBy(() ->
                memberProfileService.getProfile(memberId)
        ).isInstanceOfSatisfying(
                MemberProfileException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(expectedCode)
        );
    }
}