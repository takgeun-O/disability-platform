package io.github.takgeun.iyum.community.comment.domain;

import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.member.domain.Member;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 순수 객체 테스트
// createdAt, updatedAt의 자동 기록이나 실제 DB 매핑은 검증하지 않음.
class CommentTest {

    private static final Instant FIRST_EDITED_AT =
            Instant.parse("2026-11-05T01:00:00Z");

    private static final Instant SECOND_EDITED_AT =
            Instant.parse("2026-11-05T02:00:00Z");


    @Test
    void 댓글을_생성하면_게시글과_작성자와_내용을_보존한다() {
        Post post = createPost();
        Member author = createCommentAuthor();
        String content = "  첫 번째 줄\n\n두 번째 줄  ";

        Comment comment = Comment.create(post, author, content);

        assertThat(comment.getPost()).isSameAs(post);
        assertThat(comment.getAuthor()).isSameAs(author);
        assertThat(comment.getContent()).isEqualTo(content);
        assertThat(comment.getDeletedAt()).isNull();
        assertThat(comment.getContentUpdatedAt()).isNull();
    }

    @Test
    void 게시글이_없으면_생성할_수_없다() {
        Member author = createCommentAuthor();

        assertThatThrownBy(() ->
                Comment.create(null, author, "댓글 내용")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("게시글은 필수입니다.");
    }

    @Test
    void 작성자가_없으면_생성할_수_없다() {
        Post post = createPost();

        assertThatThrownBy(() ->
                Comment.create(post, null, "댓글 내용")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("작성자는 필수입니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void 내용이_없거나_공백뿐이면_생성할_수_없다(String content) {
        Post post = createPost();
        Member author = createCommentAuthor();

        assertThatThrownBy(() ->
                Comment.create(post, author, content)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("댓글 내용은 필수입니다.");
    }

    @Test
    void 최대_길이를_초과하면_생성할_수_없다() {
        Post post = createPost();
        Member author = createCommentAuthor();
        String content = "가".repeat(2_001);

        assertThatThrownBy(() ->
                Comment.create(post, author, content)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("댓글 내용은 2,000자 이하여야 합니다.");
    }

    @Test
    void 내용을_수정해도_게시글과_작성자는_유지한다() {
        Post post = createPost();
        Member author = createCommentAuthor();
        Comment comment = Comment.create(post, author, "기존 내용");
        String updatedContent = "  수정한 내용\n다음 줄  ";

        comment.update(updatedContent, FIRST_EDITED_AT);

        assertThat(comment.getContent()).isEqualTo(updatedContent);
        assertThat(comment.getPost()).isSameAs(post);
        assertThat(comment.getAuthor()).isSameAs(author);
        assertThat(comment.getDeletedAt()).isNull();

        assertThat(comment.getContentUpdatedAt())
                .isEqualTo(FIRST_EDITED_AT);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void 필수값_검증에_실패하면_기존_내용을_유지한다(String content) {
        Comment comment = createComment();

        assertThatThrownBy(() -> comment.update(content, FIRST_EDITED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("댓글 내용은 필수입니다.");

        assertThat(comment.getContent()).isEqualTo("기존 내용");
        assertThat(comment.getContentUpdatedAt()).isNull();
    }

    @Test
    void 길이_검증에_실패하면_기존_내용을_유지한다() {
        Comment comment = createComment();
        String content = "가".repeat(2_001);

        assertThatThrownBy(
                () -> comment.update(content, FIRST_EDITED_AT)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("댓글 내용은 2,000자 이하여야 합니다.");

        assertThat(comment.getContent()).isEqualTo("기존 내용");
        assertThat(comment.getContentUpdatedAt()).isNull();
    }

    @Test
    void 삭제하면_삭제_시각을_기록하고_내용은_유지한다() {
        Comment comment = createComment();
        Instant deletedAt = Instant.parse("2026-10-17T03:00:00Z");

        comment.delete(deletedAt);

        assertThat(comment.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(comment.getContent()).isEqualTo("기존 내용");
        assertThat(comment.getContentUpdatedAt()).isNull();
    }

    @Test
    void 삭제_시각이_없으면_삭제할_수_없다() {
        Comment comment = createComment();

        assertThatThrownBy(() -> comment.delete(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("삭제 시각은 필수입니다.");

        assertThat(comment.getDeletedAt()).isNull();
        assertThat(comment.getContentUpdatedAt()).isNull();
    }

    @Test
    void 이미_삭제된_댓글은_최초_삭제_시각을_유지한다() {
        Comment comment = createComment();
        Instant firstDeletedAt =
                Instant.parse("2026-10-17T03:00:00Z");
        Instant secondDeletedAt =
                Instant.parse("2026-10-17T04:00:00Z");

        comment.delete(firstDeletedAt);
        comment.delete(secondDeletedAt);

        assertThat(comment.getDeletedAt()).isEqualTo(firstDeletedAt);
        assertThat(comment.getContentUpdatedAt()).isNull();
    }

    @Test
    void 최초부터_동일한_본문이면_본문_수정_시각을_기록하지_않는다() {
        Comment comment = createComment();

        comment.update("기존 내용", FIRST_EDITED_AT);

        assertThat(comment.getContent()).isEqualTo("기존 내용");
        assertThat(comment.getContentUpdatedAt()).isNull();
    }

    @Test
    void 수정한_본문을_다시_전달하면_기존_본문_수정_시각을_유지한다() {
        Comment comment = createComment();
        comment.update("수정한 내용", FIRST_EDITED_AT);

        comment.update("수정한 내용", SECOND_EDITED_AT);

        assertThat(comment.getContent()).isEqualTo("수정한 내용");
        assertThat(comment.getContentUpdatedAt())
                .isEqualTo(FIRST_EDITED_AT);
    }

    @Test
    void 본문을_다시_변경하면_마지막_본문_수정_시각을_갱신한다() {
        Comment comment = createComment();
        comment.update("첫 번째 수정", FIRST_EDITED_AT);

        comment.update("두 번째 수정", SECOND_EDITED_AT);

        assertThat(comment.getContent()).isEqualTo("두 번째 수정");
        assertThat(comment.getContentUpdatedAt())
                .isEqualTo(SECOND_EDITED_AT);
    }

    @Test
    void 본문_수정_시각이_없으면_본문과_기존_수정_시각을_유지한다() {
        Comment comment = createComment();
        comment.update("첫 번째 수정", FIRST_EDITED_AT);

        assertThatThrownBy(() ->
                comment.update("두 번째 수정", null)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("본문 수정 시각은 필수입니다.");

        assertThat(comment.getContent()).isEqualTo("첫 번째 수정");
        assertThat(comment.getContentUpdatedAt())
                .isEqualTo(FIRST_EDITED_AT);
    }

    @Test
    void 잘못된_본문이면_이미_기록된_본문_수정_시각도_유지한다() {
        Comment comment = createComment();
        comment.update("첫 번째 수정", FIRST_EDITED_AT);

        assertThatThrownBy(() ->
                comment.update("   ", SECOND_EDITED_AT)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("댓글 내용은 필수입니다.");

        assertThat(comment.getContent()).isEqualTo("첫 번째 수정");
        assertThat(comment.getContentUpdatedAt())
                .isEqualTo(FIRST_EDITED_AT);
    }

    @Test
    void 숨김_복원_삭제는_본문_수정_시각을_변경하지_않는다() {
        Comment comment = createComment();
        comment.update("수정한 내용", FIRST_EDITED_AT);

        comment.hide(SECOND_EDITED_AT);
        assertThat(comment.getContentUpdatedAt())
                .isEqualTo(FIRST_EDITED_AT);

        comment.restore();
        assertThat(comment.getContentUpdatedAt())
                .isEqualTo(FIRST_EDITED_AT);

        comment.delete(SECOND_EDITED_AT);
        assertThat(comment.getContentUpdatedAt())
                .isEqualTo(FIRST_EDITED_AT);

        assertThat(comment.getContent()).isEqualTo("수정한 내용");
    }

    private Comment createComment() {
        return Comment.create(
                createPost(),
                createCommentAuthor(),
                "기존 내용"
        );
    }

    private Post createPost() {
        Member postAuthor = createActiveMember(
                "post-writer@example.com",
                "게시글회원"
        );

        return Post.create(
                postAuthor,
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                "보청기 질문",
                "게시글 본문"
        );
    }

    private Member createCommentAuthor() {
        return createActiveMember(
                "comment-writer@example.com",
                "댓글회원"
        );
    }

    private Member createActiveMember(String email, String nickname) {
        Member member = Member.createPending(
                email,
                "test-only-password-hash",
                nickname
        );

        member.activeAfterEmailVerification();

        return member;
    }
}