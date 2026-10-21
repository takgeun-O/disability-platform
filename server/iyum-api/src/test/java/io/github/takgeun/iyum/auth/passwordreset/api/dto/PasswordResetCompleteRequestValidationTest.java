package io.github.takgeun.iyum.auth.passwordreset.api.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetCompleteRequestValidationTest {

    private static final String TOKEN = "a".repeat(43);
    private static final String PASSWORD = "NewPassword123!";

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
    void 정상_입력을_허용한다() {
        var request = request(TOKEN, PASSWORD, PASSWORD);

        assertThat(invalidFields(request)).isEmpty();
    }

    @Test
    void 토큰의_URL_safe_문자를_허용한다() {
        String token = "a".repeat(39) + "Z0_-";

        assertThat(token).hasSize(43);

        assertThat(invalidFields(
                request(token, PASSWORD, PASSWORD)
        )).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "\t",
            "short-token"
    })
    void 토큰이_없거나_짧으면_거부한다(String token) {
        assertThat(invalidFields(
                request(token, PASSWORD, PASSWORD)
        )).containsExactly("token");
    }

    @Test
    void 토큰의_길이와_허용_문자를_검증한다() {
        String[] invalidTokens = {
                "a".repeat(42),
                "a".repeat(44),
                "a".repeat(42) + "+",
                "a".repeat(42) + "/",
                "a".repeat(42) + "=",
                "a".repeat(42) + "가",
                " " + TOKEN,
                TOKEN + " "
        };

        for (String token : invalidTokens) {
            assertThat(invalidFields(
                    request(token, PASSWORD, PASSWORD)
            )).containsExactly("token");
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "\t",
            "Ab12345",
            "abcdefgh",
            "12345678"
    })
    void 새_비밀번호가_규칙을_위반하면_거부한다(String password) {
        assertThat(invalidFields(
                request(TOKEN, password, password)
        )).contains("newPassword");
    }

    @Test
    void 영문과_숫자를_포함한_8자_비밀번호를_허용한다() {
        String password = "Ab123456";

        assertThat(invalidFields(
                request(TOKEN, password, password)
        )).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void 확인값이_없으면_확인_필드에만_오류가_생긴다(
            String confirmation
    ) {
        var request = request(TOKEN, PASSWORD, confirmation);

        var violations = validator.validate(request);

        assertThat(violations).hasSize(1);

        assertThat(invalidFields(request))
                .containsExactly("newPasswordConfirm");
    }

    @Test
    void 비밀번호가_다르면_확인_필드에_오류가_생긴다() {
        var request = request(
                TOKEN,
                PASSWORD,
                "DifferentPassword456!"
        );

        assertThat(invalidFields(request))
                .containsExactly("newPasswordConfirm");
    }

    @Test
    void 비밀번호의_앞뒤_공백을_유지한다() {
        String password = " NewPassword123! ";

        var request = request(TOKEN, password, password);

        assertThat(invalidFields(request)).isEmpty();
        assertThat(request.newPassword()).isEqualTo(password);
        assertThat(request.newPasswordConfirm()).isEqualTo(password);

        // 확인값만 공백을 제거했다면 서로 다른 비밀번호다.
        assertThat(invalidFields(
                request(TOKEN, password, password.strip())
        )).containsExactly("newPasswordConfirm");
    }

    @Test
    void UTF8_72바이트는_허용하고_73바이트는_거부한다() {
        // 영문·숫자 3바이트 + 한글 23자 × 3바이트 = 72바이트
        String allowed = "Ab1" + "가".repeat(23);
        String rejected = allowed + "a";

        assertThat(allowed.getBytes(StandardCharsets.UTF_8))
                .hasSize(72);

        assertThat(rejected.getBytes(StandardCharsets.UTF_8))
                .hasSize(73);

        assertThat(invalidFields(
                request(TOKEN, allowed, allowed)
        )).isEmpty();

        assertThat(invalidFields(
                request(TOKEN, rejected, rejected)
        )).containsExactly("newPassword");
    }

    @Test
    void 바이트_초과와_확인값_불일치를_각각_전달한다() {
        String tooLong = "Ab1" + "가".repeat(24);

        assertThat(invalidFields(
                request(TOKEN, tooLong, PASSWORD)
        )).containsExactlyInAnyOrder(
                "newPassword",
                "newPasswordConfirm"
        );
    }

    @Test
    void toString에_토큰과_비밀번호를_노출하지_않는다() {
        var request = request(
                TOKEN,
                PASSWORD,
                "DifferentPassword456!"
        );

        assertThat(request.toString())
                .isEqualTo("PasswordResetCompleteRequest[REDACTED]")
                .doesNotContain(
                        TOKEN,
                        PASSWORD,
                        "DifferentPassword456!"
                );
    }

    private PasswordResetCompleteRequest request(
            String token,
            String password,
            String confirmation
    ) {
        return new PasswordResetCompleteRequest(
                token,
                password,
                confirmation
        );
    }

    private Set<String> invalidFields(
            PasswordResetCompleteRequest request
    ) {
        return validator.validate(request).stream()
                .map(violation ->
                        violation.getPropertyPath().toString()
                )
                .collect(Collectors.toSet());
    }
}