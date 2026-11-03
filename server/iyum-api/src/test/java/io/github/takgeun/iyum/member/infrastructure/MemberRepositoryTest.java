package io.github.takgeun.iyum.member.infrastructure;

import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import jakarta.persistence.EntityManager;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.config.import=",    // 로컬 .env 가져오기 설정을 테스트에서 해제
        "spring.jpa.hibernate.ddl-auto=validate",   // 테스트에서도 Flyway가 만든 테이블 사용
        "spring.flyway.enabled=true",
})
@Testcontainers
@Transactional      // 각 테스트 종료 후 데이터 변경 롤백될 것
public class MemberRepositoryTest {

    private static final String TEST_HASH = "test-only-password-hash";

    private static final Instant CREATED_TIME = Instant.parse("2026-10-02T10:00:00Z");

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    // 테스트 컨테이너의 DB 접속 정보를 주입
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean(name = "auditingDateTimeProvider")
    DateTimeProvider dateTimeProvider;

    @BeforeEach
    void setUpTime() {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(CREATED_TIME));
    }

    @Test
    void 회원을_저장하고_DB에서_다시_조회한다() {
        // .saveAndFlush : SQL을 실행해 DB 제약조건까지 확인
        Member member = memberRepository.saveAndFlush(
                Member.createPending(
                        "User@Example.com",
                        TEST_HASH,
                        "Takkeun"
                )
        );

        Long memberId = member.getId();

        // 1차 캐시가 아니라 DB에서 다시 읽도록 한다. (1차 캐시를 지우고 DB 재조회)
        entityManager.clear();

        Member found = memberRepository.findByEmail("user@example.com").orElseThrow();

        assertThat(found.getEmail()).isEqualTo("user@example.com");
        assertThat(found.getNickname()).isEqualTo("Takkeun");
        assertThat(found.getStatus()).isEqualTo(MemberStatus.PENDING);
        assertThat(found.getRole()).isEqualTo(MemberRole.USER);
        assertThat(found.getCredentialVersion()).isZero();
        assertThat(found.getCreatedAt()).isEqualTo(CREATED_TIME);
        assertThat(found.getUpdatedAt()).isEqualTo(CREATED_TIME);

        assertThat(
                memberRepository.findByEmail("user@example.com")
        ).isPresent();

        assertThat(
                memberRepository.existsByNicknameIgnoreCase("takkeun")
        ).isTrue();
    }

    @Test
    void 활성화하면_상태와_수정시각이_DB에_반영한다() {
        Member member = memberRepository.saveAndFlush(
                Member.createPending(
                        "activate@example.com",
                        TEST_HASH,
                        "활성화회원"
                )
        );

        Long memberId = member.getId();
        entityManager.clear();

        Instant updatedTime = CREATED_TIME.plusSeconds(60);

        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(updatedTime));

        Member found = memberRepository.findById(memberId).orElseThrow();
        found.activeAfterEmailVerification();

        // 관리 중인 엔티티의 변경을 DB에 반영
        memberRepository.flush();
        entityManager.clear();

        Member reloaded = memberRepository.findById(memberId).orElseThrow();

        assertThat(reloaded.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(reloaded.getCreatedAt()).isEqualTo(CREATED_TIME);
        assertThat(reloaded.getUpdatedAt()).isEqualTo(updatedTime);
    }

    @Test
    void 동일한_이메일은_DB에서_중복을_차단한다() {
        memberRepository.saveAndFlush(
                Member.createPending(
                        "duplicate@example.com",
                        TEST_HASH,
                        "첫번째회원"
                )
        );

        Member duplicate = Member.createPending(
                " DUPLICATE@EXAMPLE.COM ",
                TEST_HASH,
                "두번째회원"
        );

        assertThatThrownBy(() ->
                memberRepository.saveAndFlush(duplicate)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 닉네임은_대소문자가_달라도_DB에서_중복을_차단한다() {
        memberRepository.saveAndFlush(
                Member.createPending(
                        "first@example.com",
                        TEST_HASH,
                        "Takkeun"
                )
        );

        Member duplicate = Member.createPending(
                "second@example.com",
                TEST_HASH,
                "takkeun"
        );

        assertThatThrownBy(() ->
                memberRepository.saveAndFlush(duplicate)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 개인정보를_제거한_탈퇴_회원은_ID를_유지한다() {
        Member member = memberRepository.saveAndFlush(
                Member.createPending(
                        "withdraw@example.com",
                        TEST_HASH,
                        "탈퇴테스트"
                )
        );

        Long memberId = member.getId();

        jdbcTemplate.update("""
                UPDATE members
                SET status = 'DELETED',
                    email = NULL,
                    password_hash = NULL,
                    nickname = NULL,
                    withdrawn_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, memberId);

        entityManager.clear();

        Member found = memberRepository.findById(memberId)
                .orElseThrow();

        assertThat(found.getId()).isEqualTo(memberId);
        assertThat(found.getStatus()).isEqualTo(MemberStatus.DELETED);
        assertThat(found.getEmail()).isNull();
        assertThat(found.getPasswordHash()).isNull();
        assertThat(found.getNickname()).isNull();
        assertThat(found.getWithdrawnAt());
    }

    @Test
    void 활성_회원의_이메일은_null로_변경할_수_없다() {
        Member member = Member.createPending(
                "active@example.com",
                TEST_HASH,
                "활성테스트"
        );
        member.activeAfterEmailVerification();
        memberRepository.saveAndFlush(member);

        // 활성 회원의 필수값 제약 유지 확인
        assertThatThrownBy(() ->
                jdbcTemplate.update("""
                    UPDATE members
                    SET email = NULL
                    WHERE id = ?
                    """, member.getId())
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 개인정보가_남아_있으면_삭제_완료_상태로_변경할_수_없다() {
        Member member = memberRepository.saveAndFlush(
                Member.createPending(
                        "remaining@example.com",
                        TEST_HASH,
                        "정보유지테스트"
                )
        );

        assertThatThrownBy(() ->
                jdbcTemplate.update("""
                    UPDATE members
                    SET status = 'DELETED',
                        withdrawn_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, member.getId())
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 탈퇴한_이메일과_닉네임으로_새로운_회원이_가입할_수_있다() {
        Member original = Member.createPending(
                "reuse@example.com",
                TEST_HASH,
                "재사용닉네임"
        );
        original.activeAfterEmailVerification();
        memberRepository.saveAndFlush(original);

        Long originalId = original.getId();
        Instant withdrawnAt = CREATED_TIME.plusSeconds(60);

        original.withdraw(withdrawnAt);
        memberRepository.flush();
        entityManager.clear();

        Member withdrawn = memberRepository.findById(originalId)
                .orElseThrow();

        assertThat(withdrawn.getStatus()).isEqualTo(MemberStatus.DELETED);
        assertThat(withdrawn.getEmail()).isNull();
        assertThat(withdrawn.getPasswordHash()).isNull();
        assertThat(withdrawn.getNickname()).isNull();
        assertThat(withdrawn.getWithdrawnAt()).isEqualTo(withdrawnAt);

        assertThat(memberRepository.findByEmail("reuse@example.com"))
                .isEmpty();

        assertThat(
                memberRepository.existsByNicknameIgnoreCase("재사용닉네임")
        ).isFalse();

        Member rejoined = memberRepository.saveAndFlush(
                Member.createPending(
                        "reuse@example.com",
                        TEST_HASH,
                        "재사용닉네임"
                )
        );

        assertThat(rejoined.getId()).isNotEqualTo(originalId);
        assertThat(rejoined.getStatus()).isEqualTo(MemberStatus.PENDING);
        assertThat(rejoined.getWithdrawnAt()).isNull();
    }

    @Test
    void 개인정보가_null인_탈퇴_회원_여러명을_저장할_수_있다() {
        Member first = Member.createPending(
                "first-withdraw@example.com",
                TEST_HASH,
                "첫탈퇴회원"
        );
        Member second = Member.createPending(
                "second-withdraw@example.com",
                TEST_HASH,
                "둘째탈퇴회원"
        );

        first.activeAfterEmailVerification();
        second.activeAfterEmailVerification();

        memberRepository.saveAndFlush(first);
        memberRepository.saveAndFlush(second);

        Long firstId = first.getId();
        Long secondId = second.getId();

        first.withdraw(CREATED_TIME.plusSeconds(60));
        second.withdraw(CREATED_TIME.plusSeconds(60));

        memberRepository.flush();
        entityManager.clear();

        Member foundFirst = memberRepository.findById(firstId).orElseThrow();
        Member foundSecond = memberRepository.findById(secondId).orElseThrow();

        assertThat(foundFirst.getStatus()).isEqualTo(MemberStatus.DELETED);
        assertThat(foundSecond.getStatus()).isEqualTo(MemberStatus.DELETED);
        assertThat(foundFirst.getEmail()).isNull();
        assertThat(foundSecond.getEmail()).isNull();
        assertThat(foundFirst.getNickname()).isNull();
        assertThat(foundSecond.getNickname()).isNull();
    }

    @Test
    void 활성_상태와_인증_버전이_모두_일치해야_세션을_허용한다() {
        Member member = Member.createPending(
                "session-version@example.com",
                TEST_HASH,
                "세션버전회원"
        );

        memberRepository.saveAndFlush(member);

        Long memberId = member.getId();

        // 버전이 맞아도 PENDING 회원이면 허용하지 않는다.
        assertThat(
                memberRepository.existsByIdAndStatusAndCredentialVersion(
                        memberId, MemberStatus.ACTIVE, 0L
                )
        ).isFalse();

        member.activeAfterEmailVerification();
        memberRepository.flush();

        assertThat(
                memberRepository.existsByIdAndStatusAndCredentialVersion(
                        memberId, MemberStatus.ACTIVE, 0L
                )
        ).isTrue();

        member.resetPassword("new-test-password-hash");
        memberRepository.flush();

        // 비밀번호 재설정 전 버전은 더 이상 유효하지 않다.
        assertThat(
                memberRepository.existsByIdAndStatusAndCredentialVersion(
                        memberId, MemberStatus.ACTIVE, 0L
                )
        ).isFalse();

        // 최신 버전은 유효하다.
        assertThat(
                memberRepository.existsByIdAndStatusAndCredentialVersion(
                        memberId, MemberStatus.ACTIVE, 1L
                )
        ).isTrue();
    }

    @Test
    void 상태와_인증버전과_권한이_모두_일치해야_세션을_허용한다() {
        Member member = Member.createPending(
                "session-role@example.com",
                TEST_HASH,
                "권한검증회원"
        );

        memberRepository.saveAndFlush(member);

        Long memberId = member.getId();

        // 권한과 버전이 맞아도 이메일 인증 대기 상태라면 거절한다.
        assertThat(
                memberRepository
                        .existsByIdAndStatusAndCredentialVersionAndRole(
                                memberId,
                                MemberStatus.ACTIVE,
                                0L,
                                MemberRole.USER
                        )
        ).isFalse();

        member.activeAfterEmailVerification();
        memberRepository.flush();

        // ACTIVE + 버전 0 + USER가 모두 일치한다.
        assertThat(
                memberRepository
                        .existsByIdAndStatusAndCredentialVersionAndRole(
                                memberId,
                                MemberStatus.ACTIVE,
                                0L,
                                MemberRole.USER
                        )
        ).isTrue();

        // 현재 DB 권한은 USER이므로 ADMIN 세션 조건은 일치하지 않는다.
        assertThat(
                memberRepository
                        .existsByIdAndStatusAndCredentialVersionAndRole(
                                memberId,
                                MemberStatus.ACTIVE,
                                0L,
                                MemberRole.ADMIN
                        )
        ).isFalse();

        // 상태와 권한이 맞아도 인증 버전이 다르면 거절한다.
        assertThat(
                memberRepository
                        .existsByIdAndStatusAndCredentialVersionAndRole(
                                memberId,
                                MemberStatus.ACTIVE,
                                1L,
                                MemberRole.USER
                        )
        ).isFalse();
    }

    @Test
    void DB_권한이_변경되면_이전_권한의_세션_조건은_일치하지_않는다() {
        Member member = Member.createPending(
                "role-change@example.com",
                TEST_HASH,
                "권한변경회원"
        );

        member.activeAfterEmailVerification();
        memberRepository.saveAndFlush(member);

        Long memberId = member.getId();

        // DB에서 USER → ADMIN으로 승격한다.
        // 테스트 준비용 SQL이며 권한 변경 API를 만드는 것은 아니다.
        jdbcTemplate.update("""
            UPDATE members
            SET role = 'ADMIN'
            WHERE id = ?
            """, memberId);

        entityManager.clear();

        // 이전 USER 권한으로 로그인한 세션 조건은 더 이상 맞지 않는다.
        assertThat(
                memberRepository
                        .existsByIdAndStatusAndCredentialVersionAndRole(
                                memberId,
                                MemberStatus.ACTIVE,
                                0L,
                                MemberRole.USER
                        )
        ).isFalse();

        // 새 ADMIN 권한으로 로그인했다면 조건에 일치한다.
        assertThat(
                memberRepository
                        .existsByIdAndStatusAndCredentialVersionAndRole(
                                memberId,
                                MemberStatus.ACTIVE,
                                0L,
                                MemberRole.ADMIN
                        )
        ).isTrue();

        // 인증 버전을 그대로 두고 ADMIN → USER로 강등한다.
        jdbcTemplate.update("""
            UPDATE members
            SET role = 'USER'
            WHERE id = ?
            """, memberId);

        entityManager.clear();

        // 기존 ADMIN 세션 조건은 거절한다.
        assertThat(
                memberRepository
                        .existsByIdAndStatusAndCredentialVersionAndRole(
                                memberId,
                                MemberStatus.ACTIVE,
                                0L,
                                MemberRole.ADMIN
                        )
        ).isFalse();

        // 새 USER 권한으로 로그인했다면 조건에 일치한다.
        assertThat(
                memberRepository
                        .existsByIdAndStatusAndCredentialVersionAndRole(
                                memberId,
                                MemberStatus.ACTIVE,
                                0L,
                                MemberRole.USER
                        )
        ).isTrue();
    }
}
