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
}
