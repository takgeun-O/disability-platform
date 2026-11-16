package io.github.takgeun.iyum.community.report.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentReportHandlingTest {

    private static final Long REPORTER_ID = 10L;
    private static final Long HANDLER_ID = 40L;

    private static final Instant CREATED_AT =
            Instant.parse("2026-11-12T10:00:00Z");

    private static final Instant HANDLED_AT =
            Instant.parse("2026-11-13T01:00:00Z");

    private static final String TITLE =
            "  신고 당시 제목  ";

    private static final String CONTENT =
            "  신고 당시 본문\n\n두  번째 문장\t ";

    private static final ContentReportStatus[] TERMINAL_STATUSES = {
            ContentReportStatus.RESOLVED,
            ContentReportStatus.DISMISSED
    };

    @ParameterizedTest
    @MethodSource("targets")
    void 접수_상태에는_처리_정보가_없다(boolean comment) {
        ContentReport report = newReport(comment);

        assertThat(report.getStatus())
                .isEqualTo(ContentReportStatus.RECEIVED);

        assertThat(report.getHandledByMemberId()).isNull();
        assertThat(report.getHandledAt()).isNull();
        assertThat(report.getHandlingReason()).isNull();
    }

    @ParameterizedTest
    @MethodSource("targetsAndOutcomes")
    void 접수_신고를_처리하고_스냅샷을_보존한다(
            boolean comment,
            ContentReportStatus outcome
    ) {
        ContentReport report = newReport(comment);

        handle(
                report,
                outcome,
                HANDLER_ID,
                " \t검토 완료\n필요한 조치를 마쳤습니다.\u00A0\u3000",
                HANDLED_AT
        );

        assertThat(report.getStatus()).isEqualTo(outcome);
        assertThat(report.getHandledByMemberId())
                .isEqualTo(HANDLER_ID);
        assertThat(report.getHandledAt())
                .isEqualTo(HANDLED_AT);
        assertThat(report.getHandlingReason())
                .isEqualTo("검토 완료\n필요한 조치를 마쳤습니다.");

        assertThat(report.getReporterMemberId())
                .isEqualTo(REPORTER_ID);
        assertThat(report.getReasonType())
                .isEqualTo(ContentReportReasonType.OTHER);
        assertThat(report.getReasonDetail())
                .isEqualTo("신고자가 작성한 사유");
        assertThat(report.getContentSnapshot())
                .isEqualTo(CONTENT);
        assertThat(report.getCreatedAt())
                .isEqualTo(CREATED_AT);

        if (comment) {
            assertThat(report.getPostId()).isNull();
            assertThat(report.getCommentId()).isEqualTo(144L);
            assertThat(report.getTitleSnapshot()).isNull();
        } else {
            assertThat(report.getPostId()).isEqualTo(104L);
            assertThat(report.getCommentId()).isNull();
            assertThat(report.getTitleSnapshot()).isEqualTo(TITLE);
        }
    }

    @ParameterizedTest
    @MethodSource("terminalTransitions")
    void 이미_처리된_신고는_같은_결과로도_다시_처리할_수_없다(
            ContentReportStatus first,
            ContentReportStatus second
    ) {
        ContentReport report = newReport(false);

        handle(
                report,
                first,
                HANDLER_ID,
                "최초 처리 사유",
                HANDLED_AT
        );

        assertThatThrownBy(() -> handle(
                report,
                second,
                41L,
                "변경하려는 사유",
                HANDLED_AT.plusSeconds(60)
        ))
                .isInstanceOf(ContentReportAlreadyHandledException.class);

        assertThat(report.getStatus()).isEqualTo(first);
        assertThat(report.getHandledByMemberId())
                .isEqualTo(HANDLER_ID);
        assertThat(report.getHandledAt())
                .isEqualTo(HANDLED_AT);
        assertThat(report.getHandlingReason())
                .isEqualTo("최초 처리 사유");
    }

    @ParameterizedTest
    @MethodSource("invalidInputs")
    void 잘못된_입력은_상태와_처리_정보를_변경하지_않는다(
            ContentReportStatus outcome,
            Long handlerId,
            String reason,
            Instant handledAt
    ) {
        ContentReport report = newReport(false);

        assertThatThrownBy(() -> handle(
                report,
                outcome,
                handlerId,
                reason,
                handledAt
        ))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(report.getStatus())
                .isEqualTo(ContentReportStatus.RECEIVED);
        assertThat(report.getHandledByMemberId()).isNull();
        assertThat(report.getHandledAt()).isNull();
        assertThat(report.getHandlingReason()).isNull();

        assertThat(report.getTitleSnapshot()).isEqualTo(TITLE);
        assertThat(report.getContentSnapshot()).isEqualTo(CONTENT);
        assertThat(report.getReasonDetail())
                .isEqualTo("신고자가 작성한 사유");
    }

    @ParameterizedTest
    @EnumSource(
            value = ContentReportStatus.class,
            names = {"RESOLVED", "DISMISSED"}
    )
    void 정규화_후_500_코드_포인트까지_허용한다(
            ContentReportStatus outcome
    ) {
        ContentReport report = newReport(false);
        String reason = "😀".repeat(500);

        handle(
                report,
                outcome,
                HANDLER_ID,
                " \n" + reason + "\t\u00A0",
                HANDLED_AT
        );

        assertThat(report.getHandlingReason()).isEqualTo(reason);
        assertThat(report.getHandlingReason().codePointCount(
                0,
                report.getHandlingReason().length()
        )).isEqualTo(500);
    }

    @ParameterizedTest
    @EnumSource(
            value = ContentReportStatus.class,
            names = {"RESOLVED", "DISMISSED"}
    )
    void 처리_사유의_내부_공백과_줄바꿈은_유지한다(
            ContentReportStatus outcome
    ) {
        ContentReport report = newReport(false);

        handle(
                report,
                outcome,
                HANDLER_ID,
                "  첫  문장\n\n두 번째\t문장  ",
                HANDLED_AT
        );

        assertThat(report.getHandlingReason())
                .isEqualTo("첫  문장\n\n두 번째\t문장");
    }

    static Stream<Arguments> targets() {
        return Stream.of(
                Arguments.of(false),
                Arguments.of(true)
        );
    }

    static Stream<Arguments> targetsAndOutcomes() {
        return Stream.of(false, true)
                .flatMap(comment ->
                        Arrays.stream(TERMINAL_STATUSES)
                                .map(outcome -> Arguments.of(comment, outcome))
                );
    }

    static Stream<Arguments> terminalTransitions() {
        return Arrays.stream(TERMINAL_STATUSES)
                .flatMap(first ->
                        Arrays.stream(TERMINAL_STATUSES)
                                .map(second -> Arguments.of(first, second))
                );
    }

    static Stream<Arguments> invalidInputs() {
        return Arrays.stream(TERMINAL_STATUSES)
                .flatMap(outcome -> Stream.of(
                        Arguments.of(
                                outcome, null, "사유", HANDLED_AT
                        ),
                        Arguments.of(
                                outcome, 0L, "사유", HANDLED_AT
                        ),
                        Arguments.of(
                                outcome, -1L, "사유", HANDLED_AT
                        ),
                        Arguments.of(
                                outcome, HANDLER_ID, "사유", null
                        ),
                        Arguments.of(
                                outcome, HANDLER_ID, null, HANDLED_AT
                        ),
                        Arguments.of(
                                outcome, HANDLER_ID, "", HANDLED_AT
                        ),
                        Arguments.of(
                                outcome, HANDLER_ID, " \t\r\n", HANDLED_AT
                        ),
                        Arguments.of(
                                outcome, HANDLER_ID,
                                "\u00A0\u2003\u3000", HANDLED_AT
                        ),
                        Arguments.of(
                                outcome, HANDLER_ID,
                                "가".repeat(501), HANDLED_AT
                        ),
                        Arguments.of(
                                outcome, HANDLER_ID,
                                "😀".repeat(501), HANDLED_AT
                        )
                ));
    }

    private static ContentReport newReport(boolean comment) {
        if (comment) {
            return ContentReport.forComment(
                    REPORTER_ID,
                    144L,
                    ContentReportReasonType.OTHER,
                    "  신고자가 작성한 사유  ",
                    CONTENT,
                    CREATED_AT
            );
        }

        return ContentReport.forPost(
                REPORTER_ID,
                104L,
                ContentReportReasonType.OTHER,
                "  신고자가 작성한 사유  ",
                TITLE,
                CONTENT,
                CREATED_AT
        );
    }

    private static void handle(
            ContentReport report,
            ContentReportStatus outcome,
            Long handlerId,
            String reason,
            Instant handledAt
    ) {
        switch (outcome) {
            case RESOLVED ->
                    report.resolve(handlerId, reason, handledAt);
            case DISMISSED ->
                    report.dismiss(handlerId, reason, handledAt);
            default ->
                    throw new IllegalArgumentException(
                            "테스트에서 지원하지 않는 처리 결과입니다."
                    );
        }
    }
}