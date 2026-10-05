package io.github.takgeun.iyum.auth.infrastructure;

import io.github.takgeun.iyum.auth.domain.EmailVerificationToken;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Testcontainers
@Transactional
public class EmailVerificationTokenRepositoryTest {

    private static final String TOKEN_HASH = "a".repeat(64);

    private static final Instant CREATED_AT = Instant.parse("2026-10-06T00:00:00Z");

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
    EmailVerificationTokenRepository tokenRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    void 해시로_토큰을_조회하고_사용_시각을_저장한다() {
        Long memberId = createMember();

        tokenRepository.saveAndFlush(newToken(memberId));
        entityManager.clear();

        EmailVerificationToken found = tokenRepository
                .findByTokenHash(TOKEN_HASH)
                .orElseThrow();

        assertThat(found.getMemberId()).isEqualTo(memberId);
        assertThat(found.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(found.getExpiresAt())
                .isEqualTo(CREATED_AT.plusSeconds(1800));

        Instant usedAt = CREATED_AT.plusSeconds(10);

        found.use(usedAt);
        tokenRepository.flush();
        entityManager.clear();

        EmailVerificationToken reloaded = tokenRepository
                .findByTokenHash(TOKEN_HASH)
                .orElseThrow();

        assertThat(reloaded.getUsedAt()).isEqualTo(usedAt);
        assertThat(reloaded.isUsable(usedAt)).isFalse();
    }

    @Test
    void 같은_토큰_해시는_중복_저장할_수_없다() {
        Long memberId = createMember();

        tokenRepository.saveAndFlush(newToken(memberId));

        /**
         * PostgreSQL: 중복 값으로 인한 UNIQUE 제약조건 위반
         * Hibernate: org.hibernate.exception.ConstraintViolationException으로 전달
         * Spring: DataIntegrityViolationException으로 변환하여 호출자에게 전달
         *
         * DataIntegrityViolationException은 이런 데이터 무결성 제약 위반을 표현하는 Spring 예외
         */
        assertThatThrownBy(() ->
                tokenRepository.saveAndFlush(
                        newToken(memberId)
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 존재하지_않는_회원의_토큰은_저장할_수_없다() {

        /**
         * PostgreSQL: 외래 키 제약조건 위반
         * Hibernate: org.hibernate.exception.ConstraintViolationException으로 전달
         * Spring: DataIntegrityViolationException으로 변환하여 호출자에게 전달
         *
         * DataIntegrityViolationException은 이런 데이터 무결성 제약 위반을 표현하는 Spring 예외
         */
        assertThatThrownBy(() ->
                tokenRepository.saveAndFlush(
                        newToken(Long.MAX_VALUE)
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 오래된_버전으로_토큰_상태를_덮어쓸_수_없다() {
        Long memberId = createMember();

        Long tokenId = tokenRepository
                .saveAndFlush(newToken(memberId))
                .getId();

        entityManager.clear();

        // 변경 전 버전을 가진 객체를 따로 보관합니다.
        EmailVerificationToken oldVersion = tokenRepository
                .findById(tokenId)
                .orElseThrow();

        entityManager.detach(oldVersion);

        // 다른 처리가 먼저 토큰을 사용한 상황을 재현합니다.
        EmailVerificationToken currentVersion = tokenRepository
                .findById(tokenId)
                .orElseThrow();

        currentVersion.use(CREATED_AT.plusSeconds(10));
        tokenRepository.flush();    // currentVersion은 관리 중인 객체이므로 flush()할 때 변경 사항이 DB에 반영된다.-> 버전 업
        entityManager.clear();

        // 오래된 객체로 사용 기록을 덮어쓰려 하면 거절됩니다.
        oldVersion.revoke(CREATED_AT.plusSeconds(20));

        /**
         * @Version
         * private Long version;
         * 에 의해 Hibernate는 내가 조회한 이후 다른 변경이 있었는지 확인하고,
         * 오래된 데이터로 최신 데이터를 덮어쓰는 것을 막는다.
         *
         * 최초 버전 0이라고 가정하고 토큰을 처음 저장
         * -> 버전 0인 객체를 따로 보관
         * -> 같은 토큰을 다시 조회해 먼저 사용 처리 (이 때 Hibernate가 버전을 0에서 1로 올린다.)
         * -> 오래된 객체를 취소 처리한 뒤 저장 시도
         *      -> revoke()는 우선 메모리에 있는 오래된 객체를 변경
         *      -> 이후 saveAndFlush()가 기존 엔티티를 병합하는 과정에서 Hibernate가 버전 비교
         *      -> 저장하려는 객체는 버전 0인데, DB는 이미 버전 1이네. -> 이전 상태로 덮어쓸 수 없다.
         * -> 낙관적 잠금 예외 발생
         * -> Spring이 이를 ObjectOptimisticLockingFailureException 계열의 예외로 변환해서 전달
         */
        assertThatThrownBy(() ->
                tokenRepository.saveAndFlush(oldVersion)
        ).isInstanceOf(
                ObjectOptimisticLockingFailureException.class   // 낙관적 잠금(Optimistic Locking)
        );
    }

    private Long createMember() {
        Member member = Member.createPending(
                "token-test@example.com",
                "test-only-password-hash",
                "TokenUser"
        );

        return memberRepository
                .saveAndFlush(member)
                .getId();
    }

    private EmailVerificationToken newToken(Long memberId) {
        return EmailVerificationToken.issue(
                memberId,
                TOKEN_HASH,
                CREATED_AT,
                CREATED_AT.plusSeconds(1800)
        );
    }
}
