package io.github.takgeun.iyum.auth.application;

import io.github.takgeun.iyum.auth.infrastructure.EmailVerificationTokenRepository;
import io.github.takgeun.iyum.member.domain.*;
import io.github.takgeun.iyum.member.infrastructure.MemberAgreementRepository;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Testcontainers
public class SignupServiceIntegrationTest {

    private static final String RAW_PASSWORD = "Password123!";

    /**
     * 테스트에 사용할 실제 PostgreSQL DB를 Docker 컨테이너로 준비하고
     * 같은 테스트 클래스 안에서 공유하도록 선언
     * 이렇게 하면 가짜 DB를 흉내내는 mock이 아니라 실제 PostgreSQL이 실행되는 방식이 된다.
     */
    @Container  // Testcontainers가 시작·종료를 관리할 대상으로 지정
    static PostgreSQLContainer postgres =       // static 으로 공유 : DB를 켜는 데 시간이 걸리기 때문에 한 번 켜서 여러 테스트에서 사용하기 위함
            // 사용할 이미지 등 컨테이너 설정을 담은 자바 객체 생성
            // "postgres:17-alpine" : PostgreSQL 17의 Alpine Linux 기반 Docker 이미지
            new PostgreSQLContainer("postgres:17-alpine");
    @Autowired
    @MockitoSpyBean
    private MemberAgreementRepository memberAgreementRepository;

    @Autowired
    EmailVerificationTokenRepository tokenRepository;

    /**
     * Testcontainers가 실행한 PostgreSQL의 접속 정보를 Spring에 전달하는 코드.
     */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                postgres::getJdbcUrl    // 값이 필요하면 해당 메서드를 호출해서 가져오도록 두 번째 인자는 문자열이 아니라 Supplier로 받음.
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
    SignupService signupService;

    @Autowired
    PasswordEncoder passwordEncoder;

    // Spring에 등록된 실제 빈의 동작을 유지하면서 호출 여부를 확인하거나 특정 메서드의 동작만 바꿀 수 있게 하는 테스트용 애노테이션
    // 즉, "실제 약관 동의 리포지토리를 사용하되, 테스트에서 일부 동작을 조절하겠다"라는 뜻
    @MockitoSpyBean
    MemberRepository memberRepository;

    /**
     * 각 테스트를 실행하기 전에 약관 동의 데이터와 회원 데이터를 모두 삭제해서 DB를 비어있는 상태로 준비
     */
    @BeforeEach
    void cleanDatabase() {
        // deleteAllInBatch() : 한 번의 삭제 쿼리로 전체 삭제 (Spring Data JPA가 제공하는 메소드, 테이블 구조는 유지하고 저장된 모든 행 삭제)
        // 회원을 참조하는 토큰을 먼저 삭제해야 회원 삭제 가능
        tokenRepository.deleteAllInBatch();

        // 회원을 참조하는 자식 데이터 먼저 삭제
        memberAgreementRepository.deleteAllInBatch();

        // 그 다음 부모인 회원 데이터 삭제
        memberRepository.deleteAllInBatch();
    }

    @Test
    void savesPendingMemberAndRequiredAgreements() {
        SignupCommand command = validCommand(
                " First@Example.com ",
                " FirstUser "
        );

        SignupResult result = signupService.signup(command);

        assertThat(result.memberId()).isNotNull();
        assertThat(result.status()).isEqualTo(MemberStatus.PENDING);

        Member savedMember = memberRepository.findById(result.memberId())
                .orElseThrow();

        assertThat(savedMember.getEmail())
                .isEqualTo("first@example.com");
        assertThat(savedMember.getNickname())
                .isEqualTo("FirstUser");
        assertThat(savedMember.getStatus())
                .isEqualTo(MemberStatus.PENDING);
        assertThat(savedMember.getRole())
                .isEqualTo(MemberRole.USER);

        assertThat(savedMember.getPasswordHash())
                .isNotEqualTo(RAW_PASSWORD);
        assertThat(passwordEncoder.matches(
                RAW_PASSWORD,
                savedMember.getPasswordHash()
        )).isTrue();

        List<MemberAgreement> agreements = memberAgreementRepository
                .findAllByMemberIdOrderByIdAsc(result.memberId());

        assertThat(agreements)
                .extracting(
                        MemberAgreement::getTermsCode,
                        MemberAgreement::getTermsVersion
                )
                .containsExactlyInAnyOrder(
                        tuple(TermsCode.SERVICE_TERMS, "dev-v1"),
                        tuple(TermsCode.PRIVACY_COLLECTION_USE, "dev-v1")
                );

        assertThat(agreements).allSatisfy(agreement -> {
            assertThat(agreement.getMemberId())
                    .isEqualTo(result.memberId());
            assertThat(agreement.getAgreedAt()).isNotNull();
        });

        assertThat(agreements.get(0).getAgreedAt())
                .isEqualTo(agreements.get(1).getAgreedAt());

        assertThat(memberRepository.count()).isEqualTo(1);
        assertThat(memberAgreementRepository.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({
            "first@example.com, OtherUser, EMAIL_ALREADY_EXISTS",
            "second@example.com, FIRSTUSER, NICKNAME_ALREADY_EXISTS"
    })
    void rejectsDuplicationWithoutChangingExistingMember(
            String email,
            String nickname,
            SignupConflictException.Code expectedCode
    ) {
        SignupResult first = signupService.signup(
                validCommand("first@example.com", "FirstUser")
        );

        String originalPasswordHash = memberRepository
                .findById(first.memberId())
                .orElseThrow()
                .getPasswordHash();

        assertThatThrownBy(() ->
                signupService.signup(validCommand(email, nickname))
        ).isInstanceOfSatisfying(
                SignupConflictException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(expectedCode)
        );

        Member existingMember = memberRepository
                .findById(first.memberId())
                .orElseThrow();

        assertThat(existingMember.getPasswordHash())
                .isEqualTo(originalPasswordHash);
        assertThat(existingMember.getNickname())
                .isEqualTo("FirstUser");
        assertThat(existingMember.getStatus())
                .isEqualTo(MemberStatus.PENDING);

        assertThat(memberRepository.count()).isEqualTo(1);
        assertThat(memberAgreementRepository.count()).isEqualTo(2);
    }

    @Test
    void rejectsMissingRequiredAgreementWithoutSavingMember() {
        SignupCommand command = new SignupCommand(
                "first@example.com",
                RAW_PASSWORD,
                "FirstUser",
                List.of(
                        new SignupAgreement(
                                TermsCode.SERVICE_TERMS,
                                "dev-v1",
                                true
                        )
                )
        );

        assertThatThrownBy(() ->
                signupService.signup(command)
        ).isInstanceOfSatisfying(
                SignupTermsException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                SignupTermsException.Code
                                        .REQUIRED_AGREEMENT_MISSING
                        )
        );

        assertThat(memberRepository.count()).isZero();
        assertThat(memberAgreementRepository.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "first@example.com, OtherUser, EMAIL_ALREADY_EXISTS",
            "second@example.com, FIRSTUSER, NICKNAME_ALREADY_EXISTS"
    })
    void translatesDatabaseDuplicateWhenPrecheckMissesIt(
            String email,
            String nickname,
            SignupConflictException.Code expectedCode
    ) {
        signupService.signup(
                validCommand("first@example.com", "FirstUser")
        );

        // 사전 조회에서 중복을 발견하지 못한 상황을 재현합니다.
        doReturn(false)
                .when(memberRepository)
                .existsByEmail(email);

        doReturn(false)
                .when(memberRepository)
                .existsByNicknameIgnoreCase(nickname);

        assertThatThrownBy(() ->
                signupService.signup(validCommand(email, nickname))
        )
                .isInstanceOfSatisfying(
                        SignupConflictException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(expectedCode)
                )
                .hasCauseInstanceOf(DataIntegrityViolationException.class);

        assertThat(memberRepository.count()).isEqualTo(1);
        assertThat(memberAgreementRepository.count()).isEqualTo(2);
    }

    @Test
    void rollsBackMemberAndAgreementWhenAgreementSavingFails() {
        doAnswer(invocation -> {
            List<MemberAgreement> agreements =
                    invocation.getArgument(0);

            // 약관 한 건을 실제 DB에 반영한 후 실패시킵니다.
            memberAgreementRepository.saveAndFlush(
                    agreements.getFirst()
            );

            throw new IllegalStateException("약관 저장 실패 테스트");
        })
                .when(memberAgreementRepository)
                .saveAllAndFlush(anyList());

        assertThatThrownBy(() ->
                signupService.signup(
                        validCommand("first@example.com", "FirstUser")
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("약관 저장 실패 테스트");

        assertThat(memberRepository.count()).isZero();
        assertThat(memberAgreementRepository.count()).isZero();
    }

    private SignupCommand validCommand(String email, String nickname) {
        return new SignupCommand(
                email,
                RAW_PASSWORD,
                nickname,
                List.of(
                        new SignupAgreement(
                                TermsCode.SERVICE_TERMS,
                                "dev-v1",
                                true
                        ),
                        new SignupAgreement(
                                TermsCode.PRIVACY_COLLECTION_USE,
                                "dev-v1",
                                true
                        )
                )
        );
    }
}
