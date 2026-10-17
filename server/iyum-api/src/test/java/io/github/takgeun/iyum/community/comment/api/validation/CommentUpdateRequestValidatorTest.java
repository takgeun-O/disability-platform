package io.github.takgeun.iyum.community.comment.api.validation;

import io.github.takgeun.iyum.community.comment.api.dto.CommentUpdateRequest;
import io.github.takgeun.iyum.community.comment.application.CommentUpdateCommand;
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

class CommentUpdateRequestValidatorTest {

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
    void 정상적인_본문은_검증을_통과한다() {
        CommentUpdateRequest request =
                new CommentUpdateRequest("수정한 댓글입니다.");

        assertThat(validator.validate(request)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n", "\t\n"})
    void 본문이_없거나_공백뿐이면_content_필드_오류가_발생한다(
            String content
    ) {
        assertContentViolation(
                content,
                "댓글 내용은 필수입니다."
        );
    }

    @Test
    void 한글_2000자는_허용한다() {
        CommentUpdateRequest request =
                new CommentUpdateRequest("가".repeat(2_000));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void 한글_2001자는_거절한다() {
        assertContentViolation(
                "가".repeat(2_001),
                "댓글 내용은 2,000자 이하여야 합니다."
        );
    }

    @Test
    void 이모지_2000개는_허용한다() {
        String content = "😀".repeat(2_000);

        // 이 이모지는 UTF-16 코드 유닛 2개, 코드 포인트 1개다.
        assertThat(content.length()).isEqualTo(4_000);
        assertThat(content.codePointCount(0, content.length()))
                .isEqualTo(2_000);

        CommentUpdateRequest request =
                new CommentUpdateRequest(content);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void 이모지_2001개는_거절한다() {
        assertContentViolation(
                "😀".repeat(2_001),
                "댓글 내용은 2,000자 이하여야 합니다."
        );
    }

    @Test
    void 검증과_Command_변환은_앞뒤_공백과_줄바꿈을_보존한다() {
        String content = "  수정한 댓글\n두 번째 줄  ";

        CommentUpdateRequest request =
                new CommentUpdateRequest(content);

        assertThat(validator.validate(request)).isEmpty();

        CommentUpdateCommand command = request.toCommand();

        assertThat(request.content()).isEqualTo(content);
        assertThat(command.content()).isEqualTo(content);
    }

    private void assertContentViolation(
            String content,
            String expectedMessage
    ) {
        // 1. 검증할 요청 객체 만들기
        CommentUpdateRequest request =
                new CommentUpdateRequest(content);

        // 2. 검증을 실행하고 오류들을 받는다.
        // 이 때 CommentUpdateRequestValidator 실행
        // Set : 발생한 검증 위반들을 모은 집합
        Set<ConstraintViolation<CommentUpdateRequest>> violations =
                validator.validate(request);

        assertThat(violations).hasSize(1);

        // 오류 한 건을 꺼낸다.
        // Set에는 List처럼 get(0)가 없으니 반복자 사용
        ConstraintViolation<CommentUpdateRequest> violation =
                violations.iterator().next();

        // 오류가 content 필드에 연결됐는지 확인
        assertThat(violation.getPropertyPath().toString())
                .isEqualTo("content");

        assertThat(violation.getMessage())
                .isEqualTo(expectedMessage);
    }
}