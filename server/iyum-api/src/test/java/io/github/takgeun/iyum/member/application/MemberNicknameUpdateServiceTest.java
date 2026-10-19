package io.github.takgeun.iyum.member.application;

import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true"
})
@Testcontainers
class MemberNicknameUpdateServiceTest {

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
    MemberNicknameUpdateService nicknameUpdateService;

    // 기본적으로 실제 Repository를 실행한다.
    // 일부 테스트에서만 사전 검사나 flush의 동작을 바꾼다.
    @MockitoSpyBean
    MemberRepository memberRepository;

    private Long memberId;
    private Long otherMemberId;
    private Long pendingMemberId;

    @BeforeEach
    void setUp() {
        // 이 테스트 클래스의 전용 컨테이너 DB.
        // 게시글·댓글·약관 등 연관 데이터는 만들지 않는다.
        memberRepository.deleteAllInBatch();

        memberId = saveMember(
                "nickname-owner@example.com",
                "Takkeun",
                true
        );

        otherMemberId = saveMember(
                "nickname-other@example.com",
                "OtherMember",
                true
        );

        pendingMemberId = saveMember(
                "nickname-pending@example.com",
                "인증대기회원",
                false
        );
    }

    @Test
    void 닉네임을_정규화하여_변경하고_DB에_저장한다() {
        Member before = findMember(memberId);

        MemberProfileResult result =
                nicknameUpdateService.updateNickname(
                        memberId,
                        "  새로운_닉네임  "
                );

        assertThat(result.memberId()).isEqualTo(memberId);
        assertThat(result.email()).isEqualTo(before.getEmail());
        assertThat(result.nickname()).isEqualTo("새로운_닉네임");
        assertThat(result.createdAt()).isEqualTo(before.getCreatedAt());

        Member after = findMember(memberId);

        assertThat(after.getNickname()).isEqualTo("새로운_닉네임");
        assertThat(after.getEmail()).isEqualTo(before.getEmail());
        assertThat(after.getPasswordHash())
                .isEqualTo(before.getPasswordHash());
        assertThat(after.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(after.getRole()).isEqualTo(before.getRole());
        assertThat(after.getCreatedAt()).isEqualTo(before.getCreatedAt());

        assertThat(findMember(otherMemberId).getNickname())
                .isEqualTo("OtherMember");
    }

    @Test
    void 현재와_같은_닉네임이면_수정시각도_변경하지_않는다() {
        Member before = findMember(memberId);

        MemberProfileResult result =
                nicknameUpdateService.updateNickname(
                        memberId,
                        " Takkeun "
                );

        Member after = findMember(memberId);

        assertThat(result.nickname()).isEqualTo("Takkeun");
        assertThat(after.getNickname()).isEqualTo("Takkeun");
        assertThat(after.getUpdatedAt()).isEqualTo(before.getUpdatedAt());
    }

    @Test
    void 자신의_닉네임은_영문_대소문자만_변경할_수_있다() {
        MemberProfileResult result =
                nicknameUpdateService.updateNickname(
                        memberId,
                        "takkeun"
                );

        assertThat(result.nickname()).isEqualTo("takkeun");
        assertThat(findMember(memberId).getNickname())
                .isEqualTo("takkeun");
    }

    @Test
    void 다른_회원의_닉네임은_대소문자가_달라도_거절한다() {
        assertFailure(
                memberId,
                "othermember",
                MemberNicknameUpdateException.Code.NICKNAME_ALREADY_EXISTS
        );

        assertThat(findMember(memberId).getNickname())
                .isEqualTo("Takkeun");
        assertThat(findMember(otherMemberId).getNickname())
                .isEqualTo("OtherMember");
    }

    @Test
    void 회원_ID가_null이면_거절한다() {
        assertFailure(
                null,
                "새로운닉네임",
                MemberNicknameUpdateException.Code.MEMBER_NOT_FOUND
        );
    }

    @Test
    void 존재하지_않는_회원은_변경할_수_없다() {
        memberRepository.deleteById(memberId);

        assertFailure(
                memberId,
                "새로운닉네임",
                MemberNicknameUpdateException.Code.MEMBER_NOT_FOUND
        );
    }

    @Test
    void 인증_대기_회원은_닉네임을_변경할_수_없다() {
        assertFailure(
                pendingMemberId,
                "새로운닉네임",
                MemberNicknameUpdateException.Code.MEMBER_NOT_ACTIVE
        );

        assertThat(findMember(pendingMemberId).getNickname())
                .isEqualTo("인증대기회원");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "가",
            "abcdefghijklmnopqrstu",
            "닉 네임",
            "닉네임!",
            "ADMIN"
    })
    void 잘못된_닉네임은_거절하고_DB의_기존값을_유지한다(
            String nickname
    ) {
        assertFailure(
                memberId,
                nickname,
                MemberNicknameUpdateException.Code.INVALID_NICKNAME
        );

        assertThat(findMember(memberId).getNickname())
                .isEqualTo("Takkeun");
    }

    @Test
    void 사전검사를_통과해도_DB_중복을_변환하고_롤백한다() {
        Member before = findMember(memberId);

        // 사전 검사에서는 중복을 발견하지 못한 상황을 재현한다.
        // 저장 시에는 실제 PostgreSQL 고유 인덱스가 중복을 차단한다.
        doReturn(false)
                .when(memberRepository)
                .existsByNicknameIgnoreCaseExcludingMemberId(
                        "othermember",
                        memberId
                );

        assertFailure(
                memberId,
                "othermember",
                MemberNicknameUpdateException.Code.NICKNAME_ALREADY_EXISTS
        );

        Member after = findMember(memberId);

        assertThat(after.getNickname()).isEqualTo("Takkeun");
        assertThat(after.getUpdatedAt()).isEqualTo(before.getUpdatedAt());
        assertThat(findMember(otherMemberId).getNickname())
                .isEqualTo("OtherMember");
    }

    @Test
    void 닉네임_중복이_아닌_DB_오류는_그대로_전달하고_롤백한다() {
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException(
                        "test-only unrelated integrity failure"
                );

        doThrow(failure)
                .when(memberRepository)
                .flush();

        assertThatThrownBy(() ->
                nicknameUpdateService.updateNickname(
                        memberId,
                        "새로운닉네임"
                )
        ).isSameAs(failure);

        assertThat(findMember(memberId).getNickname())
                .isEqualTo("Takkeun");
    }

    private Long saveMember(
            String email,
            String nickname,
            boolean active
    ) {
        Member member = Member.createPending(
                email,
                "test-only-password-hash",
                nickname
        );

        if (active) {
            member.activeAfterEmailVerification();
        }

        return memberRepository.saveAndFlush(member).getId();
    }

    private Member findMember(Long id) {
        return memberRepository.findById(id).orElseThrow();
    }

    private void assertFailure(
            Long id,
            String nickname,
            MemberNicknameUpdateException.Code expectedCode
    ) {
        assertThatThrownBy(() ->
                nicknameUpdateService.updateNickname(id, nickname)
        ).isInstanceOfSatisfying(
                MemberNicknameUpdateException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(expectedCode)
        );
    }
}