package io.github.takgeun.iyum.member.infrastructure;

import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberAgreement;
import io.github.takgeun.iyum.member.domain.TermsCode;
import jakarta.persistence.EntityManager;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;

import static io.github.takgeun.iyum.member.domain.TermsCode.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.config.import=",    // 로컬 .env 가져오기 설정을 테스트에서 해제
        "spring.jpa.hibernate.ddl-auto=validate",   // 테스트에서도 Flyway가 만든 테이블 사용
        "spring.flyway.enabled=true",
})
@Testcontainers
@Transactional
class MemberAgreementRepositoryTest {

    /**
     * 회원별 조회 : 두 동의 기록을 정확히 저장하고 다른 회원의 기록은 섞이지 않음
     * 동일 버전 중복 : 동의 시각이 달라도 같은 회원·약관·버전은 거절
     * 다른 버전 저장 : 과거 동의 기록을 유지하면서 새로운 버전 기록 추가
     * 없는 회원 참조 : DB 외래키가 잘못된 회원 ID를 차단
     */

    private static final String TEST_HASH = "test-only-password-hash";

    private static final Instant AGREED_AT =
            Instant.parse("2026-10-04T16:00:00Z");

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

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
    MemberAgreementRepository agreementRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    void 특정_회원의_약관_동의_이력을_저장하고_조회한다() {
        Long memberId = createMember(
                "first@example.com",
                "첫번째회원"
        );

        Long otherMemberId = createMember(
                "second@example.com",
                "두번째회원"
        );

        agreementRepository.saveAllAndFlush(List.of(
                MemberAgreement.recordConsent(
                        memberId,
                        SERVICE_TERMS,
                        "dev-v1",
                        AGREED_AT
                ),
                MemberAgreement.recordConsent(
                        memberId,
                        PRIVACY_COLLECTION_USE,
                        "dev-v1",
                        AGREED_AT
                ),
                MemberAgreement.recordConsent(
                        otherMemberId,
                        SERVICE_TERMS,
                        "dev-v1",
                        AGREED_AT
                )
        ));

        entityManager.clear();

        // 특정 회원의 동의 이력을 ID 오름차순으로 조회
        List<MemberAgreement> found =
                agreementRepository.findAllByMemberIdOrderByIdAsc(memberId);

        assertThat(found).hasSize(2);

        assertThat(found)
                .extracting(
                        MemberAgreement::getTermsCode,
                        MemberAgreement::getTermsVersion
                )
                .containsExactlyInAnyOrder(
                        tuple(SERVICE_TERMS, "dev-v1"),
                        tuple(PRIVACY_COLLECTION_USE, "dev-v1")
                );
    }

    @Test
    void 같은_회원의_동일_약관_동일_버전은_중복_저장할_수_없다() {
        Long memberId = createMember(
                "duplicate@example.com",
                "중복검증회원"
        );

        agreementRepository.saveAndFlush(
                MemberAgreement.recordConsent(
                        memberId,
                        SERVICE_TERMS,
                        "dev-v1",
                        AGREED_AT
                )
        );

        MemberAgreement duplicate = MemberAgreement.recordConsent(
                memberId,
                SERVICE_TERMS,
                "dev-v1",
                AGREED_AT.plusSeconds(60)
        );

        assertThatThrownBy(() ->
                agreementRepository.saveAndFlush(duplicate)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 다른_버전의_동의는_기존_이력을_유지하며_저장할_수_있다() {
        Long memberId = createMember(
                "version@example.com",
                "버전검증회원"
        );

        Instant later = AGREED_AT.plusSeconds(60);

        agreementRepository.saveAllAndFlush(List.of(
                MemberAgreement.recordConsent(
                        memberId,
                        SERVICE_TERMS,
                        "dev-v1",
                        AGREED_AT
                ),
                MemberAgreement.recordConsent(
                        memberId,
                        SERVICE_TERMS,
                        "dev-v2",
                        later
                )
        ));

        entityManager.clear();

        List<MemberAgreement> found =
                agreementRepository.findAllByMemberIdOrderByIdAsc(memberId);

        assertThat(found)
                .extracting(
                        MemberAgreement::getTermsVersion,
                        MemberAgreement::getAgreedAt
                )
                .containsExactlyInAnyOrder(
                        tuple("dev-v1", AGREED_AT),
                        tuple("dev-v2", later)
                );
    }

    @Test
    void 존재하지_않는_회원의_동의는_저장할_수_없다() {
        MemberAgreement agreement = MemberAgreement.recordConsent(
                Long.MAX_VALUE,
                SERVICE_TERMS,
                "dev-v1",
                AGREED_AT
        );

        assertThatThrownBy(() ->
                agreementRepository.saveAndFlush(agreement)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Long createMember(String email, String nickname) {
        Member member = Member.createPending(
                email,
                TEST_HASH,
                nickname
        );

        return memberRepository.saveAndFlush(member).getId();
    }

}