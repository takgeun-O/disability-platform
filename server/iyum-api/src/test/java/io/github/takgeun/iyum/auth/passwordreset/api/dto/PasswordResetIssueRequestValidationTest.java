package io.github.takgeun.iyum.auth.passwordreset.api.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetIssueRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory =
                Validation.buildDefaultValidatorFactory();

        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void 이메일의_앞뒤_공백을_제거하고_소문자로_변환한다() {
        var request = new PasswordResetIssueRequest(
                " \tMember@Example.COM \n"
        );

        assertThat(request.email())
                .isEqualTo("member@example.com");

        assertThat(invalidFields(request)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "\t",
            "not-an-email",
            "member@",
            "@example.com",
            "member name@example.com"
    })
    void 이메일이_없거나_형식이_잘못되면_거부한다(String email) {
        var request = new PasswordResetIssueRequest(email);

        assertThat(invalidFields(request))
                .containsExactly("email");
    }

    @Test
    void 정규화한_이메일이_254자를_초과하면_길이_검증에_실패한다() {
        String email = "a".repeat(64)
                + "@"
                + "b".repeat(63)
                + "."
                + "c".repeat(63)
                + "."
                + "d".repeat(62);

        assertThat(email).hasSize(255);

        var request = new PasswordResetIssueRequest(
                " " + email + " "
        );

        // 공백을 제거한 이메일을 기준으로 검사한다.
        assertThat(request.email()).hasSize(255);

        // 이메일 형식 오류와 별개로 @Size 검증도 작동해야 한다.
        assertThat(validator.validate(request))
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString())
                            .isEqualTo("email");

                    assertThat(
                            violation.getConstraintDescriptor()
                                    .getAnnotation()
                                    .annotationType()
                    ).isEqualTo(Size.class);
                });
    }

    @Test
    void 가입_여부와_무관하게_형식이_올바르면_DTO_검증을_통과한다() {
        var request = new PasswordResetIssueRequest(
                "not-registered@example.com"
        );

        // DTO 검증은 DB를 조회하거나 가입 여부를 판단하지 않는다.
        assertThat(invalidFields(request)).isEmpty();
    }

    @Test
    void toString에_이메일을_노출하지_않는다() {
        var request = new PasswordResetIssueRequest(
                "private-member@example.com"
        );

        assertThat(request.toString())
                .isEqualTo("PasswordResetIssueRequest[REDACTED]")
                .doesNotContain("private-member@example.com");
    }

    private Set<String> invalidFields(
            PasswordResetIssueRequest request
    ) {
        return validator.validate(request).stream()
                .map(violation ->
                        violation.getPropertyPath().toString()
                )
                .collect(Collectors.toSet());
    }
}