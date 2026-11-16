package io.github.takgeun.iyum.community.report.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentReportTest {

    private static final Long REPORTER_ID = 1L;
    private static final Long POST_ID = 10L;
    private static final Long COMMENT_ID = 20L;

    private static final Instant NOW =
            Instant.parse("2026-11-12T10:00:00Z");

    @Test
    void 게시글_신고를_접수_상태로_생성한다() {
        ContentReport report = ContentReport.forPost(
                REPORTER_ID,
                POST_ID,
                ContentReportReasonType.SPAM_ADVERTISING,
                "  동일한 광고를 반복 게시했습니다.  ",
                "게시글 제목",
                "게시글 본문",
                NOW
        );

        assertThat(report.getReporterMemberId())
                .isEqualTo(REPORTER_ID);
        assertThat(report.getPostId()).isEqualTo(POST_ID);
        assertThat(report.getCommentId()).isNull();

        assertThat(report.getReasonType())
                .isEqualTo(ContentReportReasonType.SPAM_ADVERTISING);
        assertThat(report.getReasonDetail())
                .isEqualTo("동일한 광고를 반복 게시했습니다.");

        assertThat(report.getTitleSnapshot())
                .isEqualTo("게시글 제목");
        assertThat(report.getContentSnapshot())
                .isEqualTo("게시글 본문");

        assertThat(report.getStatus())
                .isEqualTo(ContentReportStatus.RECEIVED);
        assertThat(report.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void 댓글_신고는_댓글_ID와_본문만_저장한다() {
        ContentReport report = ContentReport.forComment(
                REPORTER_ID,
                COMMENT_ID,
                ContentReportReasonType.ABUSE_HATE,
                null,
                "댓글 본문",
                NOW
        );

        assertThat(report.getReporterMemberId())
                .isEqualTo(REPORTER_ID);
        assertThat(report.getPostId()).isNull();
        assertThat(report.getCommentId()).isEqualTo(COMMENT_ID);

        assertThat(report.getReasonType())
                .isEqualTo(ContentReportReasonType.ABUSE_HATE);
        assertThat(report.getReasonDetail()).isNull();

        assertThat(report.getTitleSnapshot()).isNull();
        assertThat(report.getContentSnapshot())
                .isEqualTo("댓글 본문");

        assertThat(report.getStatus())
                .isEqualTo(ContentReportStatus.RECEIVED);
        assertThat(report.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void 스냅샷의_앞뒤_공백과_내부_줄바꿈을_보존한다() {
        String title = "  접수 당시 제목  ";
        String content = " \n첫 번째 문장\n\n두  번째 문장\t ";

        ContentReport postReport = ContentReport.forPost(
                REPORTER_ID,
                POST_ID,
                ContentReportReasonType.OTHER,
                "  검토가 필요합니다.  ",
                title,
                content,
                NOW
        );

        ContentReport commentReport = ContentReport.forComment(
                REPORTER_ID,
                COMMENT_ID,
                ContentReportReasonType.OTHER,
                "  검토가 필요합니다.  ",
                content,
                NOW
        );

        assertThat(postReport.getTitleSnapshot()).isEqualTo(title);
        assertThat(postReport.getContentSnapshot()).isEqualTo(content);
        assertThat(commentReport.getContentSnapshot()).isEqualTo(content);

        // 정규화 대상은 신고 상세 설명이다.
        assertThat(postReport.getReasonDetail())
                .isEqualTo("검토가 필요합니다.");
        assertThat(commentReport.getReasonDetail())
                .isEqualTo("검토가 필요합니다.");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void 유효하지_않은_신고자_ID는_거부한다(Long reporterId) {
        assertThatThrownBy(() -> ContentReport.forPost(
                reporterId,
                POST_ID,
                ContentReportReasonType.SPAM_ADVERTISING,
                null,
                "제목",
                "본문",
                NOW
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> ContentReport.forComment(
                reporterId,
                COMMENT_ID,
                ContentReportReasonType.SPAM_ADVERTISING,
                null,
                "본문",
                NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void 유효하지_않은_대상_ID는_거부한다(Long targetId) {
        assertThatThrownBy(() -> ContentReport.forPost(
                REPORTER_ID,
                targetId,
                ContentReportReasonType.SPAM_ADVERTISING,
                null,
                "제목",
                "본문",
                NOW
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> ContentReport.forComment(
                REPORTER_ID,
                targetId,
                ContentReportReasonType.SPAM_ADVERTISING,
                null,
                "본문",
                NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t\r\n", "\u2003"})
    void 게시글_신고에는_제목_스냅샷이_필수다(String title) {
        assertThatThrownBy(() -> ContentReport.forPost(
                REPORTER_ID,
                POST_ID,
                ContentReportReasonType.SPAM_ADVERTISING,
                null,
                title,
                "본문",
                NOW
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("게시글 제목 스냅샷은 필수입니다.");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t\r\n", "\u2003"})
    void 게시글과_댓글_신고에는_본문_스냅샷이_필수다(
            String content
    ) {
        assertThatThrownBy(() -> ContentReport.forPost(
                REPORTER_ID,
                POST_ID,
                ContentReportReasonType.SPAM_ADVERTISING,
                null,
                "제목",
                content,
                NOW
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("본문 스냅샷은 필수입니다.");

        assertThatThrownBy(() -> ContentReport.forComment(
                REPORTER_ID,
                COMMENT_ID,
                ContentReportReasonType.SPAM_ADVERTISING,
                null,
                content,
                NOW
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("본문 스냅샷은 필수입니다.");
    }

    @Test
    void 접수_시각은_필수다() {
        assertThatThrownBy(() -> ContentReport.forPost(
                REPORTER_ID,
                POST_ID,
                ContentReportReasonType.SPAM_ADVERTISING,
                null,
                "제목",
                "본문",
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("신고 접수 시각은 필수입니다.");
    }

    @Test
    void 생성_시_신고_사유_유형을_검증한다() {
        assertThatThrownBy(() -> ContentReport.forPost(
                REPORTER_ID,
                POST_ID,
                null,
                "상세 설명",
                "제목",
                "본문",
                NOW
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("신고 사유 유형은 필수입니다.");
    }

    @Test
    void 생성_시_기타_사유의_상세_설명을_검증한다() {
        assertThatThrownBy(() -> ContentReport.forComment(
                REPORTER_ID,
                COMMENT_ID,
                ContentReportReasonType.OTHER,
                " \t ",
                "본문",
                NOW
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("기타 사유는 상세 설명이 필요합니다.");
    }

    @Test
    void 생성_시_상세_설명_길이를_검증한다() {
        assertThatThrownBy(() -> ContentReport.forPost(
                REPORTER_ID,
                POST_ID,
                ContentReportReasonType.SPAM_ADVERTISING,
                "가".repeat(501),
                "제목",
                "본문",
                NOW
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("신고 상세 설명은 500자 이하여야 합니다.");
    }
}