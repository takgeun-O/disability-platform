package io.github.takgeun.iyum.member.application;

import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.member.domain.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberPostItemResultTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-29T00:00:00Z");

    private static final Instant HIDDEN_AT =
            Instant.parse("2026-10-30T00:00:00Z");

    private Post post;

    @BeforeEach
    void setUp() {
        Member author = Member.createPending(
                "writer@example.com",
                "test-only-password-hash",
                "테스트작성자"
        );

        author.activeAfterEmailVerification();

        ReflectionTestUtils.setField(author, "id", 7L);

        post = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                "게시글 제목",
                "게시글 본문"
        );

        ReflectionTestUtils.setField(post, "id", 123L);
        ReflectionTestUtils.setField(
                post,
                "createdAt",
                CREATED_AT
        );
    }

    @Test
    void 공개_게시글은_내용과_집계값을_반환한다() {
        MemberPostItemResult result =
                MemberPostItemResult.from(
                        post,
                        3L,
                        5L,
                        true
                );

        assertThat(result.postId()).isEqualTo(123L);
        assertThat(result.type()).isEqualTo(PostType.GENERAL);
        assertThat(result.topic()).isEqualTo(PostTopic.DAILY);
        assertThat(result.title()).isEqualTo("게시글 제목");
        assertThat(result.contentPreview()).isEqualTo("게시글 본문");
        assertThat(result.author()).isNotNull();
        assertThat(result.createdAt()).isEqualTo(CREATED_AT);
        assertThat(result.commentCount()).isEqualTo(3L);
        assertThat(result.likeCount()).isEqualTo(5L);
        assertThat(result.liked()).isTrue();
        assertThat(result.visibility())
                .isEqualTo(MemberContentVisibility.VISIBLE);
    }

    @Test
    void 숨겨진_게시글은_ID와_작성시각과_표시상태만_반환한다() {
        post.hide(HIDDEN_AT);

        MemberPostItemResult result =
                MemberPostItemResult.from(
                        post,
                        3L,
                        5L,
                        true
                );

        assertThat(result.postId()).isEqualTo(123L);
        assertThat(result.createdAt()).isEqualTo(CREATED_AT);
        assertThat(result.visibility())
                .isEqualTo(MemberContentVisibility.HIDDEN);

        assertThat(result.type()).isNull();
        assertThat(result.topic()).isNull();
        assertThat(result.title()).isNull();
        assertThat(result.contentPreview()).isNull();
        assertThat(result.author()).isNull();
        assertThat(result.commentCount()).isNull();
        assertThat(result.likeCount()).isNull();
        assertThat(result.liked()).isNull();

        // 응답에서 제외할 뿐, 원본 데이터는 변경하지 않는다.
        assertThat(post.getTitle()).isEqualTo("게시글 제목");
        assertThat(post.getContent()).isEqualTo("게시글 본문");
        assertThat(post.getHiddenAt()).isEqualTo(HIDDEN_AT);
    }

    @Test
    void 숨겨진_게시글이라도_삭제되었으면_결과_생성을_거절한다() {
        post.hide(HIDDEN_AT);
        post.delete(HIDDEN_AT.plusSeconds(60));

        assertThatThrownBy(() ->
                MemberPostItemResult.from(
                        post,
                        3L,
                        5L,
                        true
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "삭제된 게시글은 내 게시글 목록에 포함할 수 없습니다."
                );
    }
}