package io.github.takgeun.iyum.community.post.domain;

import io.github.takgeun.iyum.member.domain.Member;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostTest {

    @Test
    void 게시글을_생성하면_제목을_정리하고_본문을_보존한다() {
        Member author = createAuthor();
        String content = "  첫 번째 줄\n\n두 번째 줄  ";

        Post post = Post.create(
                author,
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                "  보청기 질문입니다  ",
                content
        );

        assertThat(post.getAuthor()).isSameAs(author);
        assertThat(post.getType()).isEqualTo(PostType.QUESTION);
        assertThat(post.getTopic()).isEqualTo(PostTopic.HEARING_AID);
        assertThat(post.getTitle()).isEqualTo("보청기 질문입니다");
        assertThat(post.getContent()).isEqualTo(content);
        assertThat(post.getDeletedAt()).isNull();
    }

    @Test
    void 작성자가_없으면_생성할_수_없다() {
        assertThatThrownBy(() ->
                Post.create(
                        null,
                        PostType.GENERAL,
                        PostTopic.DAILY,
                        "제목",
                        "본문"
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("작성자는 필수입니다.");
    }

    @Test
    void 글_유형이나_주제가_없으면_생성할_수_없다() {
        Member author = createAuthor();

        assertThatThrownBy(() ->
                Post.create(
                        author, null, PostTopic.DAILY, "제목", "본문"
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("글 유형은 필수입니다.");

        assertThatThrownBy(() ->
                Post.create(
                        author, PostType.GENERAL, null, "제목", "본문"
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("주제는 필수입니다.");
    }

    @Test
    void 제목이나_본문이_공백뿐이면_생성할_수_없다() {
        Member author = createAuthor();

        assertThatThrownBy(() ->
                Post.create(
                        author,
                        PostType.GENERAL,
                        PostTopic.DAILY,
                        "   ",
                        "본문"
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("제목은 필수입니다.");

        assertThatThrownBy(() ->
                Post.create(
                        author,
                        PostType.GENERAL,
                        PostTopic.DAILY,
                        "제목",
                        "   "
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("본문은 필수입니다.");
    }

    private Member createAuthor() {
        Member author = Member.createPending(
                "writer@example.com",
                "test-only-password-hash",
                "작성회원"
        );

        author.activeAfterEmailVerification();

        return author;
    }
}