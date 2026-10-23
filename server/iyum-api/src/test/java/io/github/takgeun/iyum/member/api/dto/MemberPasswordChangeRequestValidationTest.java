package io.github.takgeun.iyum.member.api.dto;

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

class MemberPasswordChangeRequestValidationTest {

    private static final String CURRENT_PASSWORD = "OldPass123!";
    private static final String NEW_PASSWORD = "NewPass456!";

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
    void 정상_입력을_허용한다() {
        var request = new MemberPasswordChangeRequest(
                CURRENT_PASSWORD,
                NEW_PASSWORD,
                NEW_PASSWORD
        );

        assertThat(invalidFields(request)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void 현재_비밀번호는_필수다(String currentPassword) {
        var request = new MemberPasswordChangeRequest(
                currentPassword,
                NEW_PASSWORD,
                NEW_PASSWORD
        );

        assertThat(invalidFields(request))
                .containsExactly("currentPassword");
    }

    @Test
    void 현재_비밀번호에는_새_비밀번호의_복잡도_규칙을_적용하지_않는다() {
        var request = new MemberPasswordChangeRequest(
                "x",
                NEW_PASSWORD,
                NEW_PASSWORD
        );

        assertThat(invalidFields(request)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "Ab12345",
            "abcdefgh",
            "12345678"
    })
    void 새_비밀번호의_필수값과_길이와_구성을_검증한다(String password) {
        var request = new MemberPasswordChangeRequest(
                CURRENT_PASSWORD,
                password,
                password
        );

        assertThat(invalidFields(request))
                .contains("newPassword");
    }

    @Test
    void 영문과_숫자를_포함한_8자_비밀번호를_허용한다() {
        String password = "Ab123456";

        var request = new MemberPasswordChangeRequest(
                CURRENT_PASSWORD,
                password,
                password
        );

        assertThat(invalidFields(request)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void 확인값이_없으면_확인_필드에_필수값_오류만_생긴다(
            String confirmation
    ) {
        var request = new MemberPasswordChangeRequest(
                CURRENT_PASSWORD,
                NEW_PASSWORD,
                confirmation
        );

        assertThat(validator.validate(request)).hasSize(1);

        assertThat(invalidFields(request))
                .containsExactly("newPasswordConfirm");
    }

    @Test
    void 확인값이_다르면_확인_필드에_오류가_생긴다() {
        var request = new MemberPasswordChangeRequest(
                CURRENT_PASSWORD,
                NEW_PASSWORD,
                "DifferentPass789!"
        );

        assertThat(invalidFields(request))
                .containsExactly("newPasswordConfirm");
    }

    @Test
    void 비밀번호_원문의_앞뒤_공백을_유지한다() {
        String currentPassword = " OldPass123! ";
        String newPassword = " NewPass456! ";

        var request = new MemberPasswordChangeRequest(
                currentPassword,
                newPassword,
                newPassword
        );

        assertThat(invalidFields(request)).isEmpty();
        assertThat(request.currentPassword()).isEqualTo(currentPassword);
        assertThat(request.newPassword()).isEqualTo(newPassword);
        assertThat(request.newPasswordConfirm()).isEqualTo(newPassword);

        var differentConfirmation = new MemberPasswordChangeRequest(
                currentPassword,
                newPassword,
                newPassword.strip()
        );

        assertThat(invalidFields(differentConfirmation))
                .containsExactly("newPasswordConfirm");
    }

    @Test
    void 현재_비밀번호는_UTF8_72바이트까지_허용한다() {
        String allowed = "가".repeat(24);
        String rejected = allowed + "a";

        assertThat(allowed.getBytes(StandardCharsets.UTF_8))
                .hasSize(72);

        assertThat(rejected.getBytes(StandardCharsets.UTF_8))
                .hasSize(73);

        assertThat(invalidFields(new MemberPasswordChangeRequest(
                allowed,
                NEW_PASSWORD,
                NEW_PASSWORD
        ))).isEmpty();

        assertThat(invalidFields(new MemberPasswordChangeRequest(
                rejected,
                NEW_PASSWORD,
                NEW_PASSWORD
        ))).containsExactly("currentPassword");
    }

    @Test
    void 새_비밀번호는_UTF8_72바이트까지_허용한다() {
        String allowed = "Ab1" + "가".repeat(23);
        String rejected = allowed + "a";

        assertThat(allowed.getBytes(StandardCharsets.UTF_8))
                .hasSize(72);

        assertThat(rejected.getBytes(StandardCharsets.UTF_8))
                .hasSize(73);

        assertThat(invalidFields(new MemberPasswordChangeRequest(
                CURRENT_PASSWORD,
                allowed,
                allowed
        ))).isEmpty();

        assertThat(invalidFields(new MemberPasswordChangeRequest(
                CURRENT_PASSWORD,
                rejected,
                rejected
        ))).containsExactly("newPassword");
    }

    @Test
    void 현재와_새_비밀번호의_동일_여부는_서비스에서_판단한다() {
        var request = new MemberPasswordChangeRequest(
                NEW_PASSWORD,
                NEW_PASSWORD,
                NEW_PASSWORD
        );

        assertThat(invalidFields(request)).isEmpty();
    }

    @Test
    void toString에_비밀번호를_노출하지_않는다() {
        String confirmation = "DifferentPass789!";

        var request = new MemberPasswordChangeRequest(
                CURRENT_PASSWORD,
                NEW_PASSWORD,
                confirmation
        );

        assertThat(request.toString())
                .isEqualTo("MemberPasswordChangeRequest[REDACTED]")
                .doesNotContain(
                        CURRENT_PASSWORD,
                        NEW_PASSWORD,
                        confirmation
                );
    }

    private Set<String> invalidFields(
            MemberPasswordChangeRequest request
    ) {
        return validator.validate(request).stream()
                .map(violation ->
                        violation.getPropertyPath().toString()
                )
                .collect(Collectors.toSet());
    }
}