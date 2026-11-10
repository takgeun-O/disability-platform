package io.github.takgeun.iyum.admin.member.application;

import io.github.takgeun.iyum.admin.member.infrastructure.AdminMemberReadRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMemberReadServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long TARGET_ID = 104L;
    private static final long CREDENTIAL_VERSION = 3L;

    private static final Instant CREATED_AT =
            Instant.parse("2026-11-01T10:00:00Z");

    private static final Instant WITHDRAWN_AT =
            CREATED_AT.plusSeconds(120);

    private static final Instant UPDATED_AT =
            CREATED_AT.plusSeconds(180);

    @Mock
    MemberRepository memberRepository;

    @Mock
    AdminMemberReadRepository memberReadRepository;

    @InjectMocks
    AdminMemberReadService service;

    @ParameterizedTest
    @MethodSource("invalidAuthenticationInputs")
    void 잘못된_인증_입력은_DB_조회_전에_거절한다(
            Long actorMemberId,
            long credentialVersion
    ) {
        assertRejectedForBoth(
                actorMemberId,
                credentialVersion,
                AdminMemberReadException.Code.AUTHENTICATION_REQUIRED
        );

        verifyNoInteractions(memberRepository);
    }

    static Stream<Arguments> invalidAuthenticationInputs() {
        return Stream.of(
                Arguments.of((Long) null, 0L),
                Arguments.of(0L, 0L),
                Arguments.of(-1L, 0L),
                Arguments.of(ADMIN_ID, -1L)
        );
    }

    @Test
    void 요청자가_존재하지_않으면_인증을_요구한다() {
        when(memberRepository.findById(ADMIN_ID))
                .thenReturn(Optional.empty());

        assertRejectedForBoth(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                AdminMemberReadException.Code.AUTHENTICATION_REQUIRED
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = MemberStatus.class,
            names = "ACTIVE",
            mode = EnumSource.Mode.EXCLUDE
    )
    void 비활성_관리자는_회원_정보를_조회할_수_없다(
            MemberStatus status
    ) {
        Member actor = member(
                ADMIN_ID,
                status,
                MemberRole.ADMIN,
                CREDENTIAL_VERSION
        );

        when(memberRepository.findById(ADMIN_ID))
                .thenReturn(Optional.of(actor));

        assertRejectedForBoth(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                AdminMemberReadException.Code.AUTHENTICATION_REQUIRED
        );
    }

    @Test
    void 인증_버전이_다르면_관리자도_조회할_수_없다() {
        givenActiveAdmin();

        assertRejectedForBoth(
                ADMIN_ID,
                CREDENTIAL_VERSION - 1,
                AdminMemberReadException.Code.AUTHENTICATION_REQUIRED
        );
    }

    @Test
    void 유효한_일반_회원은_관리자_권한_오류로_거절한다() {
        Member actor = member(
                ADMIN_ID,
                MemberStatus.ACTIVE,
                MemberRole.USER,
                CREDENTIAL_VERSION
        );

        when(memberRepository.findById(ADMIN_ID))
                .thenReturn(Optional.of(actor));

        assertRejectedForBoth(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                AdminMemberReadException.Code.ADMIN_REQUIRED
        );
    }

    @Test
    void 목록을_결과_객체로_변환하면서_페이지_정보를_유지한다() {
        givenActiveAdmin();

        Member target = member(
                TARGET_ID,
                MemberStatus.ACTIVE,
                MemberRole.USER,
                0L
        );

        AdminMemberReadQuery query = new AdminMemberReadQuery(
                1,
                1,
                null,
                null,
                null,
                null,
                AdminMemberSort.OLDEST
        );

        Pageable pageable = PageRequest.of(
                1,
                1,
                Sort.by(
                        Sort.Order.asc("createdAt"),
                        Sort.Order.asc("id")
                )
        );

        when(memberReadRepository.findMembers(query))
                .thenReturn(new PageImpl<>(
                        List.of(target),
                        pageable,
                        3
                ));

        Page<AdminMemberItemResult> result = service.getMembers(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                query
        );

        assertThat(result.getContent()).hasSize(1);

        AdminMemberItemResult item = result.getContent().getFirst();

        assertThat(item.memberId()).isEqualTo(TARGET_ID);
        assertThat(item.nickname()).isEqualTo("회원104");
        assertThat(item.email()).isEqualTo("member104@example.com");
        assertThat(item.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(item.role()).isEqualTo(MemberRole.USER);
        assertThat(item.createdAt()).isEqualTo(CREATED_AT);

        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(1);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.getSort()).isEqualTo(pageable.getSort());
        assertThat(result.hasNext()).isTrue();
        assertThat(result.hasPrevious()).isTrue();

        verify(memberReadRepository).findMembers(query);
    }

    @Test
    void 범위_밖_빈_페이지의_전체_개수를_유지한다() {
        givenActiveAdmin();

        AdminMemberReadQuery query = new AdminMemberReadQuery(
                5, 20, null, null, null, null, null
        );

        when(memberReadRepository.findMembers(query))
                .thenReturn(new PageImpl<Member>(
                        List.of(),
                        PageRequest.of(5, 20),
                        3
                ));

        Page<AdminMemberItemResult> result = service.getMembers(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                query
        );

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getNumber()).isEqualTo(5);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void 탈퇴_회원의_목록_표시_정보를_변환한다() {
        givenActiveAdmin();

        Member target = member(
                TARGET_ID,
                MemberStatus.DELETED,
                MemberRole.USER,
                0L
        );

        AdminMemberReadQuery query = defaultQuery();

        when(memberReadRepository.findMembers(query))
                .thenReturn(new PageImpl<>(
                        List.of(target),
                        PageRequest.of(0, 20),
                        1
                ));

        Page<AdminMemberItemResult> result = service.getMembers(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                query
        );

        AdminMemberItemResult item = result.getContent().getFirst();

        assertThat(item.memberId()).isEqualTo(TARGET_ID);
        assertThat(item.nickname()).isEqualTo("탈퇴한 회원");
        assertThat(item.email()).isNull();
        assertThat(item.status()).isEqualTo(MemberStatus.DELETED);
    }

    @ParameterizedTest
    @EnumSource(MemberStatus.class)
    void 관리자는_대상_상태와_무관하게_상세를_조회한다(
            MemberStatus status
    ) {
        givenActiveAdmin();

        Member target = member(
                TARGET_ID,
                status,
                MemberRole.USER,
                0L
        );

        when(memberReadRepository.findMemberById(TARGET_ID))
                .thenReturn(Optional.of(target));

        AdminMemberDetailResult result = service.getMember(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                TARGET_ID
        );

        boolean withdrawn =
                status == MemberStatus.WITHDRAWN
                        || status == MemberStatus.DELETED;

        assertThat(result.memberId()).isEqualTo(TARGET_ID);
        assertThat(result.status()).isEqualTo(status);
        assertThat(result.role()).isEqualTo(MemberRole.USER);
        assertThat(result.nickname()).isEqualTo(
                withdrawn ? "탈퇴한 회원" : "회원104"
        );
        assertThat(result.email()).isEqualTo(
                status == MemberStatus.DELETED
                        ? null
                        : "member104@example.com"
        );
        assertThat(result.createdAt()).isEqualTo(CREATED_AT);
        assertThat(result.updatedAt()).isEqualTo(UPDATED_AT);
        assertThat(result.withdrawnAt()).isEqualTo(
                withdrawn ? WITHDRAWN_AT : null
        );

        verify(memberReadRepository).findMemberById(TARGET_ID);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void 잘못된_대상_ID는_조회하지_않고_찾을_수_없음으로_처리한다(
            Long targetMemberId
    ) {
        givenActiveAdmin();

        assertThatThrownBy(() -> service.getMember(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                targetMemberId
        ))
                .isInstanceOfSatisfying(
                        AdminMemberReadException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        AdminMemberReadException.Code
                                                .MEMBER_NOT_FOUND
                                )
                );

        verifyNoInteractions(memberReadRepository);
    }

    @Test
    void 대상_ID가_잘못되어도_요청자_인증부터_확인한다() {
        assertThatThrownBy(() -> service.getMember(
                null,
                0L,
                -1L
        ))
                .isInstanceOfSatisfying(
                        AdminMemberReadException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        AdminMemberReadException.Code
                                                .AUTHENTICATION_REQUIRED
                                )
                );

        verifyNoInteractions(
                memberRepository,
                memberReadRepository
        );
    }

    @Test
    void 존재하지_않는_대상은_찾을_수_없음으로_처리한다() {
        givenActiveAdmin();

        when(memberReadRepository.findMemberById(TARGET_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMember(
                ADMIN_ID,
                CREDENTIAL_VERSION,
                TARGET_ID
        ))
                .isInstanceOfSatisfying(
                        AdminMemberReadException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        AdminMemberReadException.Code
                                                .MEMBER_NOT_FOUND
                                )
                );

        verify(memberReadRepository).findMemberById(TARGET_ID);
    }

    private void assertRejectedForBoth(
            Long actorMemberId,
            long credentialVersion,
            AdminMemberReadException.Code expectedCode
    ) {
        assertThatThrownBy(() -> service.getMembers(
                actorMemberId,
                credentialVersion,
                defaultQuery()
        ))
                .isInstanceOfSatisfying(
                        AdminMemberReadException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(expectedCode)
                );

        assertThatThrownBy(() -> service.getMember(
                actorMemberId,
                credentialVersion,
                TARGET_ID
        ))
                .isInstanceOfSatisfying(
                        AdminMemberReadException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(expectedCode)
                );

        // 권한 검증 실패 시 목록·상세 조회를 모두 막아야 한다.
        verifyNoInteractions(memberReadRepository);
    }

    private void givenActiveAdmin() {
        Member actor = member(
                ADMIN_ID,
                MemberStatus.ACTIVE,
                MemberRole.ADMIN,
                CREDENTIAL_VERSION
        );

        when(memberRepository.findById(ADMIN_ID))
                .thenReturn(Optional.of(actor));
    }

    private AdminMemberReadQuery defaultQuery() {
        return new AdminMemberReadQuery(
                0, 20, null, null, null, null, null
        );
    }

    private Member member(
            Long memberId,
            MemberStatus status,
            MemberRole role,
            long credentialVersion
    ) {
        Member member = Member.createPending(
                "member" + memberId + "@example.com",
                "test-only-password-hash",
                "회원" + memberId
        );

        if (status != MemberStatus.PENDING) {
            member.activeAfterEmailVerification();
        }

        if (status == MemberStatus.DELETED) {
            member.withdraw(WITHDRAWN_AT);
        }

        // DB 없이 서비스 동작을 검증하기 위한 테스트 데이터 구성.
        ReflectionTestUtils.setField(member, "id", memberId);
        ReflectionTestUtils.setField(member, "status", status);
        ReflectionTestUtils.setField(member, "role", role);
        ReflectionTestUtils.setField(
                member,
                "credentialVersion",
                credentialVersion
        );
        ReflectionTestUtils.setField(member, "createdAt", CREATED_AT);
        ReflectionTestUtils.setField(member, "updatedAt", UPDATED_AT);

        if (status == MemberStatus.WITHDRAWN) {
            ReflectionTestUtils.setField(
                    member,
                    "withdrawnAt",
                    WITHDRAWN_AT
            );
        }

        return member;
    }
}