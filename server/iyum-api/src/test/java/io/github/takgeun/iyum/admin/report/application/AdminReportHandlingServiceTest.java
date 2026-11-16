package io.github.takgeun.iyum.admin.report.application;

import io.github.takgeun.iyum.community.report.domain.ContentReport;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import io.github.takgeun.iyum.community.report.infrastructure.ContentReportRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminReportHandlingServiceTest {

    private static final Long ACTOR_ID = 40L;
    private static final long CREDENTIAL_VERSION = 3L;

    private static final Long REPORT_ID = 100L;
    private static final Long REPORTER_ID = 20L;
    private static final Long POST_ID = 104L;

    private static final Instant CREATED_AT =
            Instant.parse("2026-11-12T10:00:00Z");

    private static final Instant HANDLED_AT =
            Instant.parse("2026-11-13T01:00:00Z");

    private static final String TITLE_SNAPSHOT =
            "신고 당시 게시글 제목";

    private static final String CONTENT_SNAPSHOT =
            " \n신고 당시 본문\n두 번째 문장\t ";

    @Mock
    MemberRepository memberRepository;

    @Mock
    ContentReportRepository reportRepository;

    @Mock
    Clock clock;

    @Mock
    Member actor;

    private AdminReportHandlingService service;

    @BeforeEach
    void setUp() {
        service = new AdminReportHandlingService(
                memberRepository,
                reportRepository,
                clock
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 접수된_신고를_처리하고_처리_정보를_반환한다(
            boolean resolve
    ) {
        ContentReport report = receivedReport();

        givenAuthorizedAdmin();
        givenReport(report);
        when(clock.instant()).thenReturn(HANDLED_AT);

        AdminReportHandlingResult result = handle(
                resolve,
                REPORT_ID,
                " \t 검토를 완료했습니다. \n"
        );

        ContentReportStatus expectedStatus = resolve
                ? ContentReportStatus.RESOLVED
                : ContentReportStatus.DISMISSED;

        assertThat(report.getStatus()).isEqualTo(expectedStatus);
        assertThat(report.getHandledByMemberId()).isEqualTo(ACTOR_ID);
        assertThat(report.getHandledAt()).isEqualTo(HANDLED_AT);
        assertThat(report.getHandlingReason())
                .isEqualTo("검토를 완료했습니다.");

        assertThat(result.reportId()).isEqualTo(REPORT_ID);
        assertThat(result.status()).isEqualTo(expectedStatus);
        assertThat(result.handledByMemberId()).isEqualTo(ACTOR_ID);
        assertThat(result.handledAt()).isEqualTo(HANDLED_AT);
        assertThat(result.handlingReason())
                .isEqualTo("검토를 완료했습니다.");

        assertOriginalReportFields(report);

        // 관리자 잠금 조회 → 신고 잠금 조회 → 처리 시각 획득.
        InOrder order = inOrder(
                memberRepository,
                reportRepository,
                clock
        );

        order.verify(memberRepository).findByIdForUpdate(ACTOR_ID);
        order.verify(reportRepository).findByIdForUpdate(REPORT_ID);
        order.verify(clock).instant();

        // 변경 감지를 사용하므로 save()를 별도로 호출하지 않는다.
        verifyNoMoreInteractions(
                memberRepository,
                reportRepository,
                clock
        );
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                    "true,  NULL, 3",
                    "false, NULL, 3",
                    "true,  0,    3",
                    "false, 0,    3",
                    "true,  -1,   3",
                    "false, -1,   3",
                    "true,  40,  -1",
                    "false, 40,  -1"
            },
            nullValues = "NULL"
    )
    void 잘못된_인증_정보는_DB_조회_전에_거부한다(
            boolean resolve,
            Long actorMemberId,
            long credentialVersion
    ) {
        assertFailure(
                AdminReportHandlingException.Code.AUTHENTICATION_REQUIRED,
                () -> handle(
                        resolve,
                        actorMemberId,
                        credentialVersion,
                        REPORT_ID,
                        "검토 완료"
                )
        );

        verifyNoInteractions(
                memberRepository,
                reportRepository,
                clock
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 요청자_회원이_없으면_인증_오류다(
            boolean resolve
    ) {
        when(memberRepository.findByIdForUpdate(ACTOR_ID))
                .thenReturn(Optional.empty());

        assertFailure(
                AdminReportHandlingException.Code.AUTHENTICATION_REQUIRED,
                () -> handle(resolve, REPORT_ID, "검토 완료")
        );

        verifyNoInteractions(reportRepository, clock);
    }

    @ParameterizedTest
    @EnumSource(
            value = MemberStatus.class,
            names = "ACTIVE",
            mode = EnumSource.Mode.EXCLUDE
    )
    void 정상_상태가_아닌_요청자는_처리할_수_없다(
            MemberStatus memberStatus
    ) {
        when(memberRepository.findByIdForUpdate(ACTOR_ID))
                .thenReturn(Optional.of(actor));
        when(actor.getStatus()).thenReturn(memberStatus);

        for (boolean resolve : new boolean[]{true, false}) {
            assertFailure(
                    AdminReportHandlingException.Code.AUTHENTICATION_REQUIRED,
                    () -> handle(resolve, REPORT_ID, "검토 완료")
            );
        }

        verifyNoInteractions(reportRepository, clock);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 인증_버전이_다르면_처리할_수_없다(
            boolean resolve
    ) {
        when(memberRepository.findByIdForUpdate(ACTOR_ID))
                .thenReturn(Optional.of(actor));
        when(actor.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(actor.getCredentialVersion())
                .thenReturn(CREDENTIAL_VERSION + 1);

        assertFailure(
                AdminReportHandlingException.Code.AUTHENTICATION_REQUIRED,
                () -> handle(resolve, REPORT_ID, "검토 완료")
        );

        verifyNoInteractions(reportRepository, clock);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 일반_회원은_관리자_권한_오류다(
            boolean resolve
    ) {
        when(memberRepository.findByIdForUpdate(ACTOR_ID))
                .thenReturn(Optional.of(actor));
        when(actor.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(actor.getCredentialVersion())
                .thenReturn(CREDENTIAL_VERSION);
        when(actor.getRole()).thenReturn(MemberRole.USER);

        assertFailure(
                AdminReportHandlingException.Code.ADMIN_REQUIRED,
                () -> handle(resolve, REPORT_ID, "검토 완료")
        );

        verifyNoInteractions(reportRepository, clock);
    }

    @Test
    void 신고_ID와_사유가_잘못되어도_요청자_검증이_우선이다() {
        when(memberRepository.findByIdForUpdate(ACTOR_ID))
                .thenReturn(Optional.of(actor));
        when(actor.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(actor.getCredentialVersion())
                .thenReturn(CREDENTIAL_VERSION);
        when(actor.getRole()).thenReturn(MemberRole.USER);

        for (boolean resolve : new boolean[]{true, false}) {
            assertFailure(
                    AdminReportHandlingException.Code.ADMIN_REQUIRED,
                    () -> handle(resolve, null, null)
            );
        }

        verifyNoInteractions(reportRepository, clock);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void 잘못된_신고_ID는_신고_조회_전에_거부한다(
            Long reportId
    ) {
        givenAuthorizedAdmin();

        for (boolean resolve : new boolean[]{true, false}) {
            assertFailure(
                    AdminReportHandlingException.Code.REPORT_NOT_FOUND,
                    () -> handle(resolve, reportId, "검토 완료")
            );
        }

        verifyNoInteractions(reportRepository, clock);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 신고가_없으면_찾을_수_없음_오류다(
            boolean resolve
    ) {
        givenAuthorizedAdmin();

        when(reportRepository.findByIdForUpdate(REPORT_ID))
                .thenReturn(Optional.empty());

        assertFailure(
                AdminReportHandlingException.Code.REPORT_NOT_FOUND,
                () -> handle(resolve, REPORT_ID, "검토 완료")
        );

        verifyNoInteractions(clock);
    }

    @ParameterizedTest
    @CsvSource({
            "true,  RESOLVED",
            "false, RESOLVED",
            "true,  DISMISSED",
            "false, DISMISSED"
    })
    void 종결된_신고는_같은_처리와_다른_처리를_모두_거부한다(
            boolean resolve,
            ContentReportStatus existingStatus
    ) {
        ContentReport report = receivedReport();

        Long originalHandlerId = 99L;
        Instant originalHandledAt = CREATED_AT.plusSeconds(60);
        String originalReason = "기존 처리 사유";

        if (existingStatus == ContentReportStatus.RESOLVED) {
            report.resolve(
                    originalHandlerId,
                    originalReason,
                    originalHandledAt
            );
        } else {
            report.dismiss(
                    originalHandlerId,
                    originalReason,
                    originalHandledAt
            );
        }

        givenAuthorizedAdmin();
        givenReport(report);

        assertFailure(
                AdminReportHandlingException.Code.REPORT_ALREADY_HANDLED,
                () -> handle(resolve, REPORT_ID, "새로운 처리 사유")
        );

        assertThat(report.getStatus()).isEqualTo(existingStatus);
        assertThat(report.getHandledByMemberId())
                .isEqualTo(originalHandlerId);
        assertThat(report.getHandledAt())
                .isEqualTo(originalHandledAt);
        assertThat(report.getHandlingReason())
                .isEqualTo(originalReason);

        assertOriginalReportFields(report);
        verifyNoInteractions(clock);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 종결된_신고는_사유_검증보다_이미_처리됨_오류가_우선이다(
            boolean resolve
    ) {
        ContentReport report = receivedReport();
        report.resolve(
                99L,
                "기존 처리 사유",
                CREATED_AT.plusSeconds(60)
        );

        givenAuthorizedAdmin();
        givenReport(report);

        assertFailure(
                AdminReportHandlingException.Code.REPORT_ALREADY_HANDLED,
                () -> handle(resolve, REPORT_ID, null)
        );

        verifyNoInteractions(clock);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "\t\r\n",
            "\u00A0\u2003\u3000"
    })
    void 비어_있는_처리_사유는_거부하고_신고를_변경하지_않는다(
            String handlingReason
    ) {
        ContentReport report = receivedReport();

        givenAuthorizedAdmin();
        givenReport(report);

        for (boolean resolve : new boolean[]{true, false}) {
            assertFailure(
                    AdminReportHandlingException.Code.INVALID_HANDLING_REASON,
                    () -> handle(resolve, REPORT_ID, handlingReason)
            );

            assertReceivedWithoutHandling(report);
        }

        verifyNoInteractions(clock);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 처리_사유가_501_코드_포인트이면_거부한다(
            boolean resolve
    ) {
        ContentReport report = receivedReport();

        givenAuthorizedAdmin();
        givenReport(report);

        assertFailure(
                AdminReportHandlingException.Code.INVALID_HANDLING_REASON,
                () -> handle(
                        resolve,
                        REPORT_ID,
                        "가".repeat(501)
                )
        );

        assertReceivedWithoutHandling(report);
        verifyNoInteractions(clock);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 처리_사유는_앞뒤_공백을_제거한_500_코드_포인트까지_허용한다(
            boolean resolve
    ) {
        ContentReport report = receivedReport();

        givenAuthorizedAdmin();
        givenReport(report);
        when(clock.instant()).thenReturn(HANDLED_AT);

        // 보조 평면 문자: UTF-16 길이와 코드 포인트 수가 다르다.
        String reason = "\uD83D\uDE00".repeat(500);

        AdminReportHandlingResult result = handle(
                resolve,
                REPORT_ID,
                "\u3000" + reason + "\u00A0"
        );

        assertThat(result.handlingReason()).isEqualTo(reason);
        assertThat(report.getHandlingReason()).isEqualTo(reason);

        String savedReason = report.getHandlingReason();

        assertThat(
                savedReason.codePointCount(0, savedReason.length())
        ).isEqualTo(500);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 처리_사유의_내부_줄바꿈과_공백은_유지한다(
            boolean resolve
    ) {
        ContentReport report = receivedReport();

        givenAuthorizedAdmin();
        givenReport(report);
        when(clock.instant()).thenReturn(HANDLED_AT);

        AdminReportHandlingResult result = handle(
                resolve,
                REPORT_ID,
                " \n첫 번째 사유\n두  번째 사유\t "
        );

        assertThat(result.handlingReason())
                .isEqualTo("첫 번째 사유\n두  번째 사유");
        assertThat(report.getHandlingReason())
                .isEqualTo("첫 번째 사유\n두  번째 사유");
    }

    private void givenAuthorizedAdmin() {
        when(memberRepository.findByIdForUpdate(ACTOR_ID))
                .thenReturn(Optional.of(actor));
        when(actor.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(actor.getCredentialVersion())
                .thenReturn(CREDENTIAL_VERSION);
        when(actor.getRole()).thenReturn(MemberRole.ADMIN);
    }

    private void givenReport(ContentReport report) {
        when(reportRepository.findByIdForUpdate(REPORT_ID))
                .thenReturn(Optional.of(report));
    }

    private ContentReport receivedReport() {
        ContentReport report = ContentReport.forPost(
                REPORTER_ID,
                POST_ID,
                ContentReportReasonType.OTHER,
                "접수 당시 신고 사유",
                TITLE_SNAPSHOT,
                CONTENT_SNAPSHOT,
                CREATED_AT
        );

        ReflectionTestUtils.setField(report, "id", REPORT_ID);

        return report;
    }

    private AdminReportHandlingResult handle(
            boolean resolve,
            Long reportId,
            String handlingReason
    ) {
        return handle(
                resolve,
                ACTOR_ID,
                CREDENTIAL_VERSION,
                reportId,
                handlingReason
        );
    }

    private AdminReportHandlingResult handle(
            boolean resolve,
            Long actorMemberId,
            long credentialVersion,
            Long reportId,
            String handlingReason
    ) {
        if (resolve) {
            return service.resolve(
                    actorMemberId,
                    credentialVersion,
                    reportId,
                    handlingReason
            );
        }

        return service.dismiss(
                actorMemberId,
                credentialVersion,
                reportId,
                handlingReason
        );
    }

    private void assertReceivedWithoutHandling(
            ContentReport report
    ) {
        assertThat(report.getStatus())
                .isEqualTo(ContentReportStatus.RECEIVED);
        assertThat(report.getHandledByMemberId()).isNull();
        assertThat(report.getHandledAt()).isNull();
        assertThat(report.getHandlingReason()).isNull();

        assertOriginalReportFields(report);
    }

    private void assertOriginalReportFields(
            ContentReport report
    ) {
        assertThat(report.getId()).isEqualTo(REPORT_ID);
        assertThat(report.getReporterMemberId())
                .isEqualTo(REPORTER_ID);
        assertThat(report.getPostId()).isEqualTo(POST_ID);
        assertThat(report.getCommentId()).isNull();
        assertThat(report.getReasonType())
                .isEqualTo(ContentReportReasonType.OTHER);
        assertThat(report.getReasonDetail())
                .isEqualTo("접수 당시 신고 사유");
        assertThat(report.getTitleSnapshot())
                .isEqualTo(TITLE_SNAPSHOT);
        assertThat(report.getContentSnapshot())
                .isEqualTo(CONTENT_SNAPSHOT);
        assertThat(report.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    private void assertFailure(
            AdminReportHandlingException.Code expectedCode,
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action
    ) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(
                        AdminReportHandlingException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(expectedCode)
                );
    }
}