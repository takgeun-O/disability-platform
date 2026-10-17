package io.github.takgeun.iyum.community.comment.api.validation;

import io.github.takgeun.iyum.community.comment.api.dto.CommentCreateRequest;
import jakarta.validation.ConstraintViolation;
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

import static org.assertj.core.api.Assertions.assertThat;

class CommentCreateRequestValidatorTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    @Test
    void 정상_본문은_검증을_통과하고_Command에도_그대로_전달된다() {
        String content = "  첫 번째 줄\n\n두 번째 줄  ";
        CommentCreateRequest request =
                new CommentCreateRequest(content);

        Set<ConstraintViolation<CommentCreateRequest>> violations =
                validator.validate(request);

        assertThat(violations).isEmpty();
        assertThat(request.content()).isEqualTo(content);
        assertThat(request.toCommand().content()).isEqualTo(content);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n", " \t\n "})
    void 내용이_없거나_공백뿐이면_content_필드_오류로_등록한다(
            String content
    ) {
        assertContentViolation(
                content,
                "댓글 내용은 필수입니다."
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"가", "😀"})
    void 이천_코드포인트까지_허용한다(String character) {
        String content = character.repeat(2_000);
        CommentCreateRequest request =
                new CommentCreateRequest(content);

        Set<ConstraintViolation<CommentCreateRequest>> violations =
                validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"가", "😀"})
    void 이천_코드포인트를_초과하면_content_필드_오류로_등록한다(
            String character
    ) {
        assertContentViolation(
                character.repeat(2_001),
                "댓글 내용은 2,000자 이하여야 합니다."
        );
    }

    @Test
    void 공백과_줄바꿈도_최대_길이에_포함한다() {
        String content = "가".repeat(1_999) + " \n";

        assertContentViolation(
                content,
                "댓글 내용은 2,000자 이하여야 합니다."
        );
    }

    private void assertContentViolation(
            String content,
            String expectedMessage
    ) {
        CommentCreateRequest request =
                new CommentCreateRequest(content);

        Set<ConstraintViolation<CommentCreateRequest>> violations =
                validator.validate(request);

        assertThat(violations)
                .singleElement()
                .satisfies(violation -> {
                    assertThat(violation.getPropertyPath().toString())
                            .isEqualTo("content");

                    assertThat(violation.getMessage())
                            .isEqualTo(expectedMessage);
                });
    }
}