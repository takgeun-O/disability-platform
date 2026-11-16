package io.github.takgeun.iyum.admin.report.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AdminReportCurrentTargetStatusTest {

    private static final Instant HIDDEN_AT =
            Instant.parse("2026-11-16T09:00:00Z");

    private static final Instant DELETED_AT =
            Instant.parse("2026-11-16T10:00:00Z");

    @ParameterizedTest
    @CsvSource({
            "false, false, false, NORMAL",
            "false, true,  false, HIDDEN",
            "false, false, true,  DELETED",
            "false, true,  true,  DELETED",
            "true,  false, false, NORMAL",
            "true,  true,  false, HIDDEN",
            "true,  false, true,  DELETED",
            "true,  true,  true,  DELETED"
    })
    void 게시글과_댓글의_현재_상태를_판정하며_삭제를_우선한다(
            boolean comment,
            boolean hidden,
            boolean deleted,
            AdminReportCurrentTargetStatus expected
    ) {
        AdminReportCurrentTargetResult currentTarget =
                target(
                        comment,
                        hidden,
                        deleted
                );

        AdminReportCurrentTargetStatus actual =
                AdminReportCurrentTargetStatus.from(
                        currentTarget
                );

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void 현재_대상을_조회할_수_없으면_null을_반환한다() {
        AdminReportCurrentTargetStatus actual =
                AdminReportCurrentTargetStatus.from(null);

        assertThat(actual).isNull();
    }

    private AdminReportCurrentTargetResult target(
            boolean comment,
            boolean hidden,
            boolean deleted
    ) {
        return new AdminReportCurrentTargetResult(
                166L,
                comment ? 104L : null,
                new AdminReportTargetAuthorResult(
                        38L,
                        "테스트회원",
                        false
                ),
                comment ? null : "테스트 게시글",
                "테스트 본문",
                hidden ? HIDDEN_AT : null,
                deleted ? DELETED_AT : null
        );
    }
}