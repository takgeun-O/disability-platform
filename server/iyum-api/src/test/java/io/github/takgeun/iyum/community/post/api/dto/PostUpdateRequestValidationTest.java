package io.github.takgeun.iyum.community.post.api.dto;

import io.github.takgeun.iyum.community.post.application.PostUpdateCommand;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PostUpdateRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void 정상_입력을_허용하고_제목만_정리한다() {
        String content = "  첫 번째 줄\n\n두 번째 줄  ";

        PostCreateRequest request = request(
                "  보청기 질문입니다  ",
                content
        );

        assertThat(invalidFields(request)).isEmpty();
        assertThat(request.title()).isEqualTo("보청기 질문입니다");
        assertThat(request.content()).isEqualTo(content);
    }

    @Test
    void 글_유형이_없으면_type_필드에_오류가_생긴다() {
        PostCreateRequest request = new PostCreateRequest(
                null,
                PostTopic.HEARING_AID,
                "제목",
                "본문"
        );

        assertThat(invalidFields(request))
                .containsExactly("type");
    }

    @Test
    void 주제가_없으면_topic_필드에_오류가_생긴다() {
        PostCreateRequest request = new PostCreateRequest(
                PostType.QUESTION,
                null,
                "제목",
                "본문"
        );

        assertThat(invalidFields(request))
                .containsExactly("topic");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "　"})
    void 제목이_없거나_공백뿐이면_title_필드에_오류가_생긴다(
            String title
    ) {
        assertThat(invalidFields(request(title, "본문")))
                .containsExactly("title");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "　"})
    void 본문이_없거나_공백뿐이면_content_필드에_오류가_생긴다(
            String content
    ) {
        assertThat(invalidFields(request("제목", content)))
                .containsExactly("content");
    }

    @ParameterizedTest
    @ValueSource(strings = {"가", "😀"})
    void 최대_길이의_제목과_본문을_허용한다(String character) {
        String title = character.repeat(120);
        String content = character.repeat(10_000);

        PostCreateRequest request = request(
                "  " + title + "  ",
                content
        );

        assertThat(invalidFields(request)).isEmpty();
        assertThat(request.title()).isEqualTo(title);
        assertThat(request.content()).isEqualTo(content);
    }

    @ParameterizedTest
    @ValueSource(strings = {"가", "😀"})
    void 제목이_121자이면_title_필드에_오류가_생긴다(
            String character
    ) {
        PostCreateRequest request = request(
                character.repeat(121),
                "본문"
        );

        assertThat(invalidFields(request))
                .containsExactly("title");
    }

    @ParameterizedTest
    @ValueSource(strings = {"가", "😀"})
    void 본문이_10001자이면_content_필드에_오류가_생긴다(
            String character
    ) {
        PostCreateRequest request = request(
                "제목",
                character.repeat(10_001)
        );

        assertThat(invalidFields(request))
                .containsExactly("content");
    }

    @Test
    void 여러_입력이_잘못되면_각_필드의_오류를_반환한다() {

        PostCreateRequest request = new PostCreateRequest(
                null,
                null,
                "   ",
                "   "
        );

        assertThat(invalidFields(request))
                .containsExactlyInAnyOrder(
                        "type",
                        "topic",
                        "title",
                        "content"
                );
    }

    @Test
    void 수정_요청을_서비스_Command로_변환한다() {
        String content = "  첫 번째 줄\n\n두 번째 줄  ";

        PostUpdateRequest request = new PostUpdateRequest(
                PostType.REVIEW,
                PostTopic.COCHLEAR_IMPLANT,
                "  수정한 제목  ",
                content
        );

        PostUpdateCommand command = request.toCommand();

        assertThat(command.type())
                .isEqualTo(PostType.REVIEW);

        assertThat(command.topic())
                .isEqualTo(PostTopic.COCHLEAR_IMPLANT);

        assertThat(command.title())
                .isEqualTo("수정한 제목");

        assertThat(command.content())
                .isEqualTo(content);
    }

    private PostCreateRequest request(
            String title,
            String content
    ) {
        return new PostCreateRequest(
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                title,
                content
        );
    }

    private Set<String> invalidFields(
            PostCreateRequest request
    ) {
        /**
         * 1. 테스트에서 검증할 객체(request) 전달 -> 이 때 request는 PostCreateRequest
         *  PostCreateRequest에는 @ValidPostCreateRequest 검증 어노테이션이 붙어있음.
         * 2.어노테이션 안에 검증기 클래스가 지정되어 있음
         *  @Constraint(validatedBy = PostCreateRequestValidator.class)
         * 3. 연결된 검증기의 isValid() 호출
         */
        return validator.validate(request)  // 객체의 검증 규칙을 실행하고 위반 정보를 보관
                .stream()   // 반환된 위반 정보를 하나씩 처리
                .map(violation ->
                        violation.getPropertyPath().toString()
                )   // 각 위반 정보에서 필드 경로만 추출
                .collect(Collectors.toSet());   // 필드명을 중복 없이 모음
    }
}