package io.github.takgeun.iyum.admin.report.api.dto;

import io.github.takgeun.iyum.admin.report.application.AdminReportCurrentTargetResult;
import io.github.takgeun.iyum.admin.report.application.AdminReportCurrentTargetStatus;
import io.github.takgeun.iyum.admin.report.application.AdminReportItemResult;
import io.github.takgeun.iyum.admin.report.application.AdminReportTargetAuthorResult;
import io.github.takgeun.iyum.admin.report.application.AdminReportTargetType;
import io.github.takgeun.iyum.community.report.domain.ContentReport;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AdminReportItemResponseTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-11-16T08:00:00Z");

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
    void 현재_콘텐츠_상태를_목록_응답에_전달하고_신고_상태는_유지한다(
            boolean comment,
            boolean hidden,
            boolean deleted,
            AdminReportCurrentTargetStatus expected
    ) {
        ContentReport report = report(comment);

        AdminReportCurrentTargetResult currentTarget =
                new AdminReportCurrentTargetResult(
                        166L,
                        comment ? 104L : null,
                        new AdminReportTargetAuthorResult(
                                38L,
                                "현재작성자",
                                false
                        ),
                        comment ? null : "수정된 현재 제목",
                        "수정된 현재 본문",
                        hidden ? HIDDEN_AT : null,
                        deleted ? DELETED_AT : null
                );

        AdminReportItemResult result =
                AdminReportItemResult.from(
                        report,
                        currentTarget
                );

        AdminReportItemResponse response =
                AdminReportItemResponse.from(result);

        assertThat(result.currentTargetStatus())
                .isEqualTo(expected);

        assertThat(response.currentTargetStatus())
                .isEqualTo(expected);

        // 콘텐츠가 숨김·삭제 상태여도 신고를 자동 종결하지 않는다.
        assertThat(report.getStatus())
                .isEqualTo(ContentReportStatus.RECEIVED);

        assertThat(result.status())
                .isEqualTo(ContentReportStatus.RECEIVED);

        assertThat(response.status())
                .isEqualTo(ContentReportStatus.RECEIVED);

        assertThat(response.targetType())
                .isEqualTo(
                        comment
                                ? AdminReportTargetType.COMMENT
                                : AdminReportTargetType.POST
                );

        assertThat(response.targetId())
                .isEqualTo(166L);

        // 현재 본문으로 신고 당시 미리보기를 덮어쓰지 않는다.
        assertThat(response.contentSnapshotPreview())
                .isEqualTo("신고 당시 본문");

        assertThat(response.titleSnapshot())
                .isEqualTo(
                        comment ? null : "신고 당시 제목"
                );

        assertThat(response.currentAuthor())
                .isNotNull();

        assertThat(response.currentAuthor().nickname())
                .isEqualTo("현재작성자");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 현재_대상이_없으면_상태와_작성자는_null이고_신고_정보는_유지한다(
            boolean comment
    ) {
        ContentReport report = report(comment);

        AdminReportItemResult result =
                AdminReportItemResult.from(
                        report,
                        null
                );

        AdminReportItemResponse response =
                AdminReportItemResponse.from(result);

        assertThat(result.currentTargetStatus())
                .isNull();

        assertThat(result.currentAuthor())
                .isNull();

        assertThat(response.currentTargetStatus())
                .isNull();

        assertThat(response.currentAuthor())
                .isNull();

        assertThat(response.status())
                .isEqualTo(ContentReportStatus.RECEIVED);

        assertThat(response.targetId())
                .isEqualTo(166L);

        assertThat(response.contentSnapshotPreview())
                .isEqualTo("신고 당시 본문");
    }

    private ContentReport report(boolean comment) {
        if (comment) {
            return ContentReport.forComment(
                    39L,
                    166L,
                    ContentReportReasonType.OTHER,
                    "검토가 필요합니다.",
                    "신고 당시 본문",
                    CREATED_AT
            );
        }

        return ContentReport.forPost(
                39L,
                166L,
                ContentReportReasonType.OTHER,
                "검토가 필요합니다.",
                "신고 당시 제목",
                "신고 당시 본문",
                CREATED_AT
        );
    }
}