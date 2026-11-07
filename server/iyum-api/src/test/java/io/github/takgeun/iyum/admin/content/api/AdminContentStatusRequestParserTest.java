package io.github.takgeun.iyum.admin.content.api;

import io.github.takgeun.iyum.admin.content.application.AdminCommentStatus;
import io.github.takgeun.iyum.admin.content.application.AdminContentReadException;
import io.github.takgeun.iyum.admin.content.application.AdminPostStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminContentStatusRequestParserTest {

    @Test
    void 상태를_생략하면_전체_상태를_사용한다() {
        assertAllStatuses(new LinkedMultiValueMap<>());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ALL"})
    void 기존_단일_상태의_빈_값과_ALL은_전체_조회다(String value) {
        MultiValueMap<String, String> parameters =
                new LinkedMultiValueMap<>();
        parameters.add("status", value);

        assertAllStatuses(parameters);
    }

    @Test
    void 기존_단일_상태를_유지한다() {
        MultiValueMap<String, String> parameters =
                new LinkedMultiValueMap<>();
        parameters.add("status", "HIDDEN");

        assertThat(
                AdminContentStatusRequestParser.postStatuses(parameters)
        ).containsExactly(AdminPostStatus.HIDDEN);

        assertThat(
                AdminContentStatusRequestParser.commentStatuses(parameters)
        ).containsExactly(AdminCommentStatus.HIDDEN);
    }

    @Test
    void 다중_상태의_중복을_제거한다() {
        MultiValueMap<String, String> parameters =
                new LinkedMultiValueMap<>();
        parameters.add("statuses", "VISIBLE");
        parameters.add("statuses", "HIDDEN");
        parameters.add("statuses", "VISIBLE");

        assertThat(
                AdminContentStatusRequestParser.postStatuses(parameters)
        ).containsExactlyInAnyOrder(
                AdminPostStatus.VISIBLE,
                AdminPostStatus.HIDDEN
        );

        assertThat(
                AdminContentStatusRequestParser.commentStatuses(parameters)
        ).containsExactlyInAnyOrder(
                AdminCommentStatus.VISIBLE,
                AdminCommentStatus.HIDDEN
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            " ",
            "ALL",
            "hidden",
            "UNKNOWN",
            " HIDDEN ",
            "VISIBLE,HIDDEN"
    })
    void 잘못된_다중_상태는_거부한다(String value) {
        MultiValueMap<String, String> parameters =
                new LinkedMultiValueMap<>();
        parameters.add("statuses", value);

        assertBothInvalid(parameters);
    }

    @Test
    void 유효한_다중_상태에_빈_값이_섞여도_거부한다() {
        MultiValueMap<String, String> parameters =
                new LinkedMultiValueMap<>();
        parameters.add("statuses", "VISIBLE");
        parameters.add("statuses", "");

        assertBothInvalid(parameters);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ALL", "HIDDEN"})
    void 단일과_다중_파라미터를_동시에_전달하면_거부한다(
            String singleValue
    ) {
        MultiValueMap<String, String> parameters =
                new LinkedMultiValueMap<>();
        parameters.add("status", singleValue);
        parameters.add("statuses", "VISIBLE");

        assertBothInvalid(parameters);
    }

    @ParameterizedTest
    @ValueSource(strings = {"hidden", "UNKNOWN"})
    void 잘못된_기존_단일_상태도_거부한다(String value) {
        MultiValueMap<String, String> parameters =
                new LinkedMultiValueMap<>();
        parameters.add("status", value);

        assertBothInvalid(parameters);
    }

    @Test
    void 기존_단일_파라미터를_반복하면_거부한다() {
        MultiValueMap<String, String> parameters =
                new LinkedMultiValueMap<>();
        parameters.add("status", "VISIBLE");
        parameters.add("status", "HIDDEN");

        assertBothInvalid(parameters);
    }

    private void assertAllStatuses(
            MultiValueMap<String, String> parameters
    ) {
        assertThat(
                AdminContentStatusRequestParser.postStatuses(parameters)
        ).containsExactlyInAnyOrder(
                AdminPostStatus.VISIBLE,
                AdminPostStatus.HIDDEN,
                AdminPostStatus.DELETED
        );

        assertThat(
                AdminContentStatusRequestParser.commentStatuses(parameters)
        ).containsExactlyInAnyOrder(
                AdminCommentStatus.VISIBLE,
                AdminCommentStatus.HIDDEN,
                AdminCommentStatus.DELETED
        );
    }

    private void assertBothInvalid(
            MultiValueMap<String, String> parameters
    ) {
        assertInvalid(
                () -> AdminContentStatusRequestParser
                        .postStatuses(parameters)
        );

        assertInvalid(
                () -> AdminContentStatusRequestParser
                        .commentStatuses(parameters)
        );
    }

    private void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        AdminContentReadException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        AdminContentReadException.Code
                                                .INVALID_REQUEST_PARAMETER
                                )
                );
    }
}