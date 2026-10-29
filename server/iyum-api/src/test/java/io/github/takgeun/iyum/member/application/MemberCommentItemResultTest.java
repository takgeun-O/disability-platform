package io.github.takgeun.iyum.member.application;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.member.domain.Member;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberCommentItemResultTest {
    private static final Instant CREATED_AT = Instant.parse("2026-10-29T01:02:03.123456Z");
    private static final Instant UPDATED_AT = CREATED_AT.plusSeconds(60);
    private static final String CONTENT = "  댓글 원문\n둘째 줄  ";
    private static final String TITLE = "원본 게시글 제목";
    private static final String POST_CONTENT = "원본 게시글 본문";

    @ParameterizedTest
    @CsvSource({
            "false, false, VISIBLE",
            "false, true, HIDDEN",
            "true, false, PARENT_HIDDEN",
            "true, true, PARENT_HIDDEN"
    })
    void 숨김_조합에_따라_내용을_제한하되_ID와_작성시각_및_원문을_유지한다(
            boolean parentHidden, boolean commentHidden, MemberContentVisibility visibility
    ) {
        Comment comment = comment();
        Post post = comment.getPost();
        if (parentHidden) post.hide(UPDATED_AT);
        if (commentHidden) comment.hide(UPDATED_AT);

        MemberCommentItemResult result = MemberCommentItemResult.from(comment, 7L, true);

        assertThat(result.commentId()).isEqualTo(51L);
        assertThat(result.createdAt()).isEqualTo(CREATED_AT);
        assertThat(result.post()).isNotNull();
        assertThat(result.post().postId()).isEqualTo(31L);
        assertThat(result.visibility()).isEqualTo(visibility);
        if (visibility == MemberContentVisibility.VISIBLE) {
            assertThat(result.content()).isEqualTo(CONTENT);
            assertThat(result.updatedAt()).isEqualTo(UPDATED_AT);
            assertThat(result.likeCount()).isEqualTo(7L);
            assertThat(result.liked()).isTrue();
            assertThat(result.post().title()).isEqualTo(TITLE);
            assertThat(result.post().type()).isEqualTo(PostType.QUESTION);
            assertThat(result.post().topic()).isEqualTo(PostTopic.HEARING_AID);
        } else {
            assertThat(result.content()).isNull();
            assertThat(result.updatedAt()).isNull();
            assertThat(result.likeCount()).isNull();
            assertThat(result.liked()).isNull();
            assertThat(result.post().title()).isNull();
            assertThat(result.post().type()).isNull();
            assertThat(result.post().topic()).isNull();
        }
        assertThat(comment.getContent()).isEqualTo(CONTENT);
        assertThat(comment.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(comment.getUpdatedAt()).isEqualTo(UPDATED_AT);
        assertThat(post.getTitle()).isEqualTo(TITLE);
        assertThat(post.getContent()).isEqualTo(POST_CONTENT);
        assertThat(post.getType()).isEqualTo(PostType.QUESTION);
        assertThat(post.getTopic()).isEqualTo(PostTopic.HEARING_AID);
    }

    @ParameterizedTest
    @CsvSource({"true, false", "false, true", "true, true"})
    void 부모나_댓글이_삭제되면_숨김보다_우선하여_변환을_거절한다(
            boolean parentDeleted, boolean commentDeleted
    ) {
        Comment comment = comment();
        Post post = comment.getPost();
        post.hide(UPDATED_AT);
        comment.hide(UPDATED_AT);
        if (parentDeleted) post.delete(UPDATED_AT.plusSeconds(1));
        if (commentDeleted) comment.delete(UPDATED_AT.plusSeconds(1));

        assertThatThrownBy(() -> MemberCommentItemResult.from(comment, 7L, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("삭제된 댓글 또는 삭제된 게시글의 댓글은 반환할 수 없습니다.");
    }

    private Comment comment() {
        Member member = Member.createPending("fixture@example.com", "test-only-hash", "검증회원");
        member.activeAfterEmailVerification();
        ReflectionTestUtils.setField(member, "id", 7L);
        Post post = Post.create(member, PostType.QUESTION, PostTopic.HEARING_AID, TITLE, POST_CONTENT);
        ReflectionTestUtils.setField(post, "id", 31L);
        Comment comment = Comment.create(post, member, CONTENT);
        ReflectionTestUtils.setField(comment, "id", 51L);
        ReflectionTestUtils.setField(comment, "createdAt", CREATED_AT);
        ReflectionTestUtils.setField(comment, "updatedAt", UPDATED_AT);
        return comment;
    }
}
