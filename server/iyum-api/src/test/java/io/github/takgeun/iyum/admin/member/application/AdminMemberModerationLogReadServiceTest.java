package io.github.takgeun.iyum.admin.member.application;

import io.github.takgeun.iyum.admin.member.domain.MemberModerationAction;
import io.github.takgeun.iyum.admin.member.domain.MemberModerationLog;
import io.github.takgeun.iyum.admin.member.infrastructure.MemberModerationLogRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminMemberModerationLogReadServiceTest {

    private static final Long ACTOR_ID = 40L;
    private static final Long TARGET_ID = 20L;

    private static final long CREDENTIAL_VERSION = 3L;

    private static final Instant CREATED_AT =
            Instant.parse("2026-11-12T00:00:00Z");

    @Mock
    MemberRepository memberRepository;

    @Mock
    MemberModerationLogRepository logRepository;

    @InjectMocks
    AdminMemberModerationLogReadService service;

    @Test
    void 관리자에게_저장된_이력과_페이지_정보를_반환한다() {
        givenAdmin();

        when(memberRepository.existsById(TARGET_ID))
                .thenReturn(true);

        MemberModerationLog restored = log(
                102L,
                MemberModerationAction.LIFT_SUSPENSION,
                "재검토 후 이용 정지 해제",
                CREATED_AT.plusSeconds(60)
        );

        MemberModerationLog suspended = log(
                101L,
                MemberModerationAction.SUSPEND,
                "반복적인 이용 규칙 위반",
                CREATED_AT
        );

        PageRequest pageable = latestPage(0, 2);

        when(logRepository.findByTargetMemberId(
                TARGET_ID,
                pageable
        )).thenReturn(
                new PageImpl<>(
                        List.of(restored, suspended),
                        pageable,
                        5L
                )
        );

        Page<AdminMemberModerationLogResult> result = service.getLogs(
                ACTOR_ID,
                CREDENTIAL_VERSION,
                TARGET_ID,
                0,
                2
        );

        assertThat(result.getContent()).containsExactly(
                new AdminMemberModerationLogResult(
                        102L,
                        ACTOR_ID,
                        TARGET_ID,
                        MemberModerationAction.LIFT_SUSPENSION,
                        MemberStatus.SUSPENDED,
                        MemberStatus.ACTIVE,
                        "재검토 후 이용 정지 해제",
                        CREATED_AT.plusSeconds(60)
                ),
                new AdminMemberModerationLogResult(
                        101L,
                        ACTOR_ID,
                        TARGET_ID,
                        MemberModerationAction.SUSPEND,
                        MemberStatus.ACTIVE,
                        MemberStatus.SUSPENDED,
                        "반복적인 이용 규칙 위반",
                        CREATED_AT
                )
        );

        assertThat(result.getNumber()).isZero();
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getTotalElements()).isEqualTo(5L);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.hasPrevious()).isFalse();

        verify(memberRepository).findById(ACTOR_ID);
        verify(memberRepository).existsById(TARGET_ID);

        // 처리 시각과 ID를 모두 내림차순으로 전달하는지도 검증한다.
        verify(logRepository).findByTargetMemberId(
                TARGET_ID,
                pageable
        );

        // 조회 과정에서 추가 조회나 저장을 하지 않는다.
        verifyNoMoreInteractions(memberRepository, logRepository);
    }

    @Test
    void 존재하는_회원에게_이력이_없으면_빈_페이지를_반환한다() {
        givenAdmin();

        when(memberRepository.existsById(TARGET_ID))
                .thenReturn(true);

        PageRequest pageable = latestPage(0, 20);

        when(logRepository.findByTargetMemberId(
                TARGET_ID,
                pageable
        )).thenReturn(Page.empty(pageable));

        Page<AdminMemberModerationLogResult> result = service.getLogs(
                ACTOR_ID,
                CREDENTIAL_VERSION,
                TARGET_ID,
                0,
                20
        );

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getTotalPages()).isZero();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.hasPrevious()).isFalse();
    }

    @Test
    void 범위_밖_페이지의_전체_개수와_페이지_수를_유지한다() {
        givenAdmin();

        when(memberRepository.existsById(TARGET_ID))
                .thenReturn(true);

        PageRequest pageable = latestPage(5, 2);

        when(logRepository.findByTargetMemberId(
                TARGET_ID,
                pageable
        )).thenReturn(
                new PageImpl<>(
                        List.<MemberModerationLog>of(),
                        pageable,
                        3L
                )
        );

        Page<AdminMemberModerationLogResult> result = service.getLogs(
                ACTOR_ID,
                CREDENTIAL_VERSION,
                TARGET_ID,
                5,
                2
        );

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getNumber()).isEqualTo(5);
        assertThat(result.getTotalElements()).isEqualTo(3L);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.hasPrevious()).isTrue();
    }

    @Test
    void 관리자는_자기_자신의_이력도_조회할_수_있다() {
        givenAdmin();

        when(memberRepository.existsById(ACTOR_ID))
                .thenReturn(true);

        PageRequest pageable = latestPage(0, 20);

        when(logRepository.findByTargetMemberId(
                ACTOR_ID,
                pageable
        )).thenReturn(Page.empty(pageable));

        Page<AdminMemberModerationLogResult> result = service.getLogs(
                ACTOR_ID,
                CREDENTIAL_VERSION,
                ACTOR_ID,
                0,
                20
        );

        assertThat(result.getContent()).isEmpty();

        verify(logRepository).findByTargetMemberId(
                ACTOR_ID,
                pageable
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void 요청자_ID가_유효하지_않으면_조회하지_않는다(
            Long actorMemberId
    ) {
        /*
         * 대상 ID와 페이지도 잘못 전달하지만,
         * 인증 오류가 우선해야 한다.
         */
        assertFailure(
                AdminMemberReadException.Code.AUTHENTICATION_REQUIRED,
                actorMemberId,
                CREDENTIAL_VERSION,
                null,
                -1,
                0
        );

        verifyNoInteractions(memberRepository, logRepository);
    }

    @Test
    void 음수_인증_버전은_회원_조회_전에_거부한다() {
        assertFailure(
                AdminMemberReadException.Code.AUTHENTICATION_REQUIRED,
                ACTOR_ID,
                -1L,
                TARGET_ID,
                0,
                20
        );

        verifyNoInteractions(memberRepository, logRepository);
    }

    @Test
    void 요청자_회원이_없으면_인증_오류를_반환한다() {
        when(memberRepository.findById(ACTOR_ID))
                .thenReturn(Optional.empty());

        assertFailure(
                AdminMemberReadException.Code.AUTHENTICATION_REQUIRED,
                ACTOR_ID,
                CREDENTIAL_VERSION,
                TARGET_ID,
                0,
                20
        );

        verifyOnlyActorRead();
    }

    @ParameterizedTest
    @EnumSource(
            value = MemberStatus.class,
            names = "ACTIVE",
            mode = EnumSource.Mode.EXCLUDE
    )
    void 정상_상태가_아닌_요청자는_이력을_조회할_수_없다(
            MemberStatus memberStatus
    ) {
        Member actor = mock(Member.class);

        when(memberRepository.findById(ACTOR_ID))
                .thenReturn(Optional.of(actor));
        when(actor.getStatus()).thenReturn(memberStatus);

        assertFailure(
                AdminMemberReadException.Code.AUTHENTICATION_REQUIRED,
                ACTOR_ID,
                CREDENTIAL_VERSION,
                TARGET_ID,
                0,
                20
        );

        verifyOnlyActorRead();
    }

    @ParameterizedTest
    @ValueSource(longs = {2L, 4L})
    void DB와_세션의_인증_버전이_다르면_조회할_수_없다(
            long storedVersion
    ) {
        Member actor = mock(Member.class);

        when(memberRepository.findById(ACTOR_ID))
                .thenReturn(Optional.of(actor));
        when(actor.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(actor.getCredentialVersion()).thenReturn(storedVersion);

        assertFailure(
                AdminMemberReadException.Code.AUTHENTICATION_REQUIRED,
                ACTOR_ID,
                CREDENTIAL_VERSION,
                TARGET_ID,
                0,
                20
        );

        verifyOnlyActorRead();
    }

    @Test
    void 일반_회원은_대상_회원_존재_여부를_조회하기_전에_거부한다() {
        givenAuthenticatedActor(MemberRole.USER);

        assertFailure(
                AdminMemberReadException.Code.ADMIN_REQUIRED,
                ACTOR_ID,
                CREDENTIAL_VERSION,
                TARGET_ID,
                0,
                20
        );

        verifyOnlyActorRead();
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 20",
            "0, 0",
            "0, -1",
            "0, 51",
            "2147483647, 2",
            "42949673, 50"
    })
    void 잘못된_페이지_범위는_이력_조회_전에_거부한다(
            int page,
            int size
    ) {
        givenAdmin();

        assertFailure(
                AdminMemberReadException.Code.INVALID_PAGE_REQUEST,
                ACTOR_ID,
                CREDENTIAL_VERSION,
                TARGET_ID,
                page,
                size
        );

        verifyOnlyActorRead();
    }

    @ParameterizedTest
    @CsvSource({
            "0, 1",
            "0, 50",
            "2147483647, 1",
            "42949672, 50"
    })
    void 허용된_페이지_경계값은_조회에_전달한다(
            int page,
            int size
    ) {
        givenAdmin();

        when(memberRepository.existsById(TARGET_ID))
                .thenReturn(true);

        PageRequest pageable = latestPage(page, size);

        when(logRepository.findByTargetMemberId(
                TARGET_ID,
                pageable
        )).thenReturn(Page.empty(pageable));

        Page<AdminMemberModerationLogResult> result = service.getLogs(
                ACTOR_ID,
                CREDENTIAL_VERSION,
                TARGET_ID,
                page,
                size
        );

        assertThat(result.getNumber()).isEqualTo(page);
        assertThat(result.getSize()).isEqualTo(size);

        verify(logRepository).findByTargetMemberId(
                TARGET_ID,
                pageable
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void 유효하지_않은_대상_ID는_회원_없음으로_처리한다(
            Long targetMemberId
    ) {
        givenAdmin();

        assertFailure(
                AdminMemberReadException.Code.MEMBER_NOT_FOUND,
                ACTOR_ID,
                CREDENTIAL_VERSION,
                targetMemberId,
                0,
                20
        );

        verifyOnlyActorRead();
    }

    @Test
    void 대상_회원이_없으면_빈_이력_대신_회원_없음_오류를_반환한다() {
        givenAdmin();

        when(memberRepository.existsById(TARGET_ID))
                .thenReturn(false);

        assertFailure(
                AdminMemberReadException.Code.MEMBER_NOT_FOUND,
                ACTOR_ID,
                CREDENTIAL_VERSION,
                TARGET_ID,
                0,
                20
        );

        verify(memberRepository).findById(ACTOR_ID);
        verify(memberRepository).existsById(TARGET_ID);
        verifyNoMoreInteractions(memberRepository);
        verifyNoInteractions(logRepository);
    }

    private void givenAdmin() {
        givenAuthenticatedActor(MemberRole.ADMIN);
    }

    private void givenAuthenticatedActor(MemberRole role) {
        Member actor = mock(Member.class);

        when(memberRepository.findById(ACTOR_ID))
                .thenReturn(Optional.of(actor));
        when(actor.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(actor.getCredentialVersion())
                .thenReturn(CREDENTIAL_VERSION);
        when(actor.getRole()).thenReturn(role);
    }

    private MemberModerationLog log(
            Long logId,
            MemberModerationAction action,
            String reason,
            Instant createdAt
    ) {
        MemberModerationLog log = MemberModerationLog.create(
                ACTOR_ID,
                TARGET_ID,
                action,
                reason,
                createdAt
        );

        // DB에서 조회된 엔티티를 구성하기 위한 테스트용 ID 설정이다.
        ReflectionTestUtils.setField(log, "id", logId);

        return log;
    }

    private PageRequest latestPage(int page, int size) {
        return PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );
    }

    private void assertFailure(
            AdminMemberReadException.Code expectedCode,
            Long actorMemberId,
            long credentialVersion,
            Long targetMemberId,
            int page,
            int size
    ) {
        assertThatThrownBy(() -> service.getLogs(
                actorMemberId,
                credentialVersion,
                targetMemberId,
                page,
                size
        ))
                .isInstanceOfSatisfying(
                        AdminMemberReadException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(expectedCode)
                );
    }

    private void verifyOnlyActorRead() {
        verify(memberRepository).findById(ACTOR_ID);
        verifyNoMoreInteractions(memberRepository);
        verifyNoInteractions(logRepository);
    }
}