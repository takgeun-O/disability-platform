package io.github.takgeun.iyum.community.report.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentReportHandlingOutcomeTest {

    private static final Long HANDLER_ID = 20L;

    private static final Instant CREATED_AT =
            Instant.parse("2026-11-13T01:00:00Z");

    private static final Instant HANDLED_AT =
            Instant.parse("2026-11-13T02:00:00Z");

    @Test
    void 접수_상태에는_처리_결과가_없다() {
        assertReceived(report());
    }

    @Test
    void 기존_처리_완료는_추가_조치_없음으로_기록한다() {
        ContentReport report = report();

        report.resolve(
                HANDLER_ID,
                "추가 조치가 필요하지 않습니다.",
                HANDLED_AT
        );

        assertThat(report.getStatus())
                .isEqualTo(ContentReportStatus.RESOLVED);
        assertThat(report.getHandlingOutcome())
                .isEqualTo(
                        ContentReportHandlingOutcome.NO_CONTENT_ACTION
                );
    }

    @Test
    void 기각은_추가_조치_없음으로_기록한다() {
        ContentReport report = report();

        report.dismiss(
                HANDLER_ID,
                "운영 정책 위반에 해당하지 않습니다.",
                HANDLED_AT
        );

        assertThat(report.getStatus())
                .isEqualTo(ContentReportStatus.DISMISSED);
        assertThat(report.getHandlingOutcome())
                .isEqualTo(
                        ContentReportHandlingOutcome.NO_CONTENT_ACTION
                );
    }

    @ParameterizedTest
    @EnumSource(
            value = ContentReportHandlingOutcome.class,
            names = {
                    "CONTENT_HIDDEN",
                    "CONTENT_ALREADY_HIDDEN",
                    "NO_CONTENT_ACTION"
            }
    )
    void 허용된_결과로_처리_완료한다(
            ContentReportHandlingOutcome outcome
    ) {
        ContentReport report = report();

        report.resolve(
                HANDLER_ID,
                "  검토 결과를 기록합니다.  ",
                HANDLED_AT,
                outcome
        );

        assertThat(report.getStatus())
                .isEqualTo(ContentReportStatus.RESOLVED);
        assertThat(report.getHandlingOutcome())
                .isEqualTo(outcome);
        assertThat(report.getHandledByMemberId())
                .isEqualTo(HANDLER_ID);
        assertThat(report.getHandledAt())
                .isEqualTo(HANDLED_AT);
        assertThat(report.getHandlingReason())
                .isEqualTo("검토 결과를 기록합니다.");

        assertThat(report.getTitleSnapshot())
                .isEqualTo("신고 당시 제목");
        assertThat(report.getContentSnapshot())
                .isEqualTo("신고 당시 본문");
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(
            value = ContentReportHandlingOutcome.class,
            names = "LEGACY_UNKNOWN"
    )
    void 잘못된_결과는_처리_정보를_변경하기_전에_거부한다(
            ContentReportHandlingOutcome outcome
    ) {
        ContentReport report = report();

        assertThatThrownBy(() -> report.resolve(
                HANDLER_ID,
                "검토 결과",
                HANDLED_AT,
                outcome
        )).isInstanceOf(IllegalArgumentException.class);

        assertReceived(report);
    }

    @Test
    void 종결된_신고의_결과를_덮어쓸_수_없다() {
        ContentReport report = report();

        report.resolve(
                HANDLER_ID,
                "위반 콘텐츠를 숨겼습니다.",
                HANDLED_AT,
                ContentReportHandlingOutcome.CONTENT_HIDDEN
        );

        assertThatThrownBy(() -> report.dismiss(
                30L,
                "다른 판단",
                HANDLED_AT.plusSeconds(60)
        )).isInstanceOf(
                ContentReportAlreadyHandledException.class
        );

        assertThat(report.getStatus())
                .isEqualTo(ContentReportStatus.RESOLVED);
        assertThat(report.getHandlingOutcome())
                .isEqualTo(
                        ContentReportHandlingOutcome.CONTENT_HIDDEN
                );
        assertThat(report.getHandledByMemberId())
                .isEqualTo(HANDLER_ID);
        assertThat(report.getHandledAt())
                .isEqualTo(HANDLED_AT);
        assertThat(report.getHandlingReason())
                .isEqualTo("위반 콘텐츠를 숨겼습니다.");
    }

    private static ContentReport report() {
        return ContentReport.forPost(
                1L,
                10L,
                ContentReportReasonType.SPAM_ADVERTISING,
                null,
                "신고 당시 제목",
                "신고 당시 본문",
                CREATED_AT
        );
    }

    private static void assertReceived(ContentReport report) {
        assertThat(report.getStatus())
                .isEqualTo(ContentReportStatus.RECEIVED);
        assertThat(report.getHandlingOutcome()).isNull();
        assertThat(report.getHandledByMemberId()).isNull();
        assertThat(report.getHandledAt()).isNull();
        assertThat(report.getHandlingReason()).isNull();
    }
}