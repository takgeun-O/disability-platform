package io.github.takgeun.iyum.admin.content.application;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminContentStatusSelectionTest {

    @Test
    void 기존_단일_조건의_null과_ALL은_전체_상태로_변환한다() {
        for (AdminPostStatus status :
                Arrays.asList(null, AdminPostStatus.ALL)) {

            assertThat(AdminPostStatus.fromSingleStatus(status))
                    .containsExactlyInAnyOrder(
                            AdminPostStatus.VISIBLE,
                            AdminPostStatus.HIDDEN,
                            AdminPostStatus.DELETED
                    );
        }

        for (AdminCommentStatus status :
                Arrays.asList(null, AdminCommentStatus.ALL)) {

            assertThat(AdminCommentStatus.fromSingleStatus(status))
                    .containsExactlyInAnyOrder(
                            AdminCommentStatus.VISIBLE,
                            AdminCommentStatus.HIDDEN,
                            AdminCommentStatus.DELETED
                    );
        }
    }

    @Test
    void 기존_개별_상태는_하나의_상태만_포함한다() {
        for (AdminPostStatus status : List.of(
                AdminPostStatus.VISIBLE,
                AdminPostStatus.HIDDEN,
                AdminPostStatus.DELETED
        )) {
            assertThat(AdminPostStatus.fromSingleStatus(status))
                    .containsExactly(status);
        }

        for (AdminCommentStatus status : List.of(
                AdminCommentStatus.VISIBLE,
                AdminCommentStatus.HIDDEN,
                AdminCommentStatus.DELETED
        )) {
            assertThat(AdminCommentStatus.fromSingleStatus(status))
                    .containsExactly(status);
        }
    }

    @Test
    void 여러_상태를_유지하고_중복은_제거한다() {
        assertThat(AdminPostStatus.normalizeStatuses(List.of(
                AdminPostStatus.VISIBLE,
                AdminPostStatus.HIDDEN,
                AdminPostStatus.VISIBLE
        ))).containsExactlyInAnyOrder(
                AdminPostStatus.VISIBLE,
                AdminPostStatus.HIDDEN
        );

        assertThat(AdminCommentStatus.normalizeStatuses(List.of(
                AdminCommentStatus.HIDDEN,
                AdminCommentStatus.DELETED,
                AdminCommentStatus.HIDDEN
        ))).containsExactlyInAnyOrder(
                AdminCommentStatus.HIDDEN,
                AdminCommentStatus.DELETED
        );
    }

    @Test
    void 세_상태를_모두_선택할_수_있다() {
        assertThat(AdminPostStatus.normalizeStatuses(List.of(
                AdminPostStatus.VISIBLE,
                AdminPostStatus.HIDDEN,
                AdminPostStatus.DELETED
        ))).containsExactlyInAnyOrder(
                AdminPostStatus.VISIBLE,
                AdminPostStatus.HIDDEN,
                AdminPostStatus.DELETED
        );

        assertThat(AdminCommentStatus.normalizeStatuses(List.of(
                AdminCommentStatus.VISIBLE,
                AdminCommentStatus.HIDDEN,
                AdminCommentStatus.DELETED
        ))).containsExactlyInAnyOrder(
                AdminCommentStatus.VISIBLE,
                AdminCommentStatus.HIDDEN,
                AdminCommentStatus.DELETED
        );
    }

    @Test
    void 명시적인_다중_조건은_null과_빈_목록을_거부한다() {
        assertInvalid(() ->
                AdminPostStatus.normalizeStatuses(null));
        assertInvalid(() ->
                AdminPostStatus.normalizeStatuses(List.of()));

        assertInvalid(() ->
                AdminCommentStatus.normalizeStatuses(null));
        assertInvalid(() ->
                AdminCommentStatus.normalizeStatuses(List.of()));
    }

    @Test
    void 다중_조건의_null_원소와_ALL을_거부한다() {
        assertInvalid(() ->
                AdminPostStatus.normalizeStatuses(
                        Arrays.asList(AdminPostStatus.VISIBLE, null)
                ));
        assertInvalid(() ->
                AdminPostStatus.normalizeStatuses(
                        List.of(AdminPostStatus.ALL)
                ));
        assertInvalid(() ->
                AdminPostStatus.normalizeStatuses(
                        List.of(AdminPostStatus.VISIBLE, AdminPostStatus.ALL)
                ));

        assertInvalid(() ->
                AdminCommentStatus.normalizeStatuses(
                        Arrays.asList(AdminCommentStatus.VISIBLE, null)
                ));
        assertInvalid(() ->
                AdminCommentStatus.normalizeStatuses(
                        List.of(AdminCommentStatus.ALL)
                ));
        assertInvalid(() ->
                AdminCommentStatus.normalizeStatuses(
                        List.of(
                                AdminCommentStatus.VISIBLE,
                                AdminCommentStatus.ALL
                        )
                ));
    }

    @Test
    void 원본_목록을_변경해도_정규화된_상태는_유지된다() {
        var postInput = new ArrayList<>(
                List.of(AdminPostStatus.VISIBLE, AdminPostStatus.HIDDEN)
        );
        var commentInput = new ArrayList<>(
                List.of(
                        AdminCommentStatus.VISIBLE,
                        AdminCommentStatus.HIDDEN
                )
        );

        var postStatuses = AdminPostStatus.normalizeStatuses(postInput);
        var commentStatuses =
                AdminCommentStatus.normalizeStatuses(commentInput);

        postInput.clear();
        commentInput.clear();

        assertThat(postStatuses).containsExactlyInAnyOrder(
                AdminPostStatus.VISIBLE,
                AdminPostStatus.HIDDEN
        );
        assertThat(commentStatuses).containsExactlyInAnyOrder(
                AdminCommentStatus.VISIBLE,
                AdminCommentStatus.HIDDEN
        );
    }

    @Test
    void 반환된_상태_집합은_수정할_수_없다() {
        var postStatuses = AdminPostStatus.normalizeStatuses(
                List.of(AdminPostStatus.VISIBLE)
        );
        var commentStatuses = AdminCommentStatus.normalizeStatuses(
                List.of(AdminCommentStatus.VISIBLE)
        );

        assertThatThrownBy(() ->
                postStatuses.add(AdminPostStatus.HIDDEN)
        ).isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() ->
                commentStatuses.add(AdminCommentStatus.HIDDEN)
        ).isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() ->
                AdminPostStatus.fromSingleStatus(null)
                        .remove(AdminPostStatus.VISIBLE)
        ).isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() ->
                AdminCommentStatus.fromSingleStatus(null)
                        .remove(AdminCommentStatus.VISIBLE)
        ).isInstanceOf(UnsupportedOperationException.class);
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