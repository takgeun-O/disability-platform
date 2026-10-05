package io.github.takgeun.iyum.auth.api.validation;

import io.github.takgeun.iyum.auth.api.dto.SignupRequest;
import io.github.takgeun.iyum.member.domain.MemberInputPolicy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class SignupRequestValidator implements ConstraintValidator<ValidSignupRequest, SignupRequest> {

    private static final int MAX_PASSWORD_BYTES = 72;

    @Override
    public boolean isValid(SignupRequest request, ConstraintValidatorContext context) {

        if (request == null) {
            return true;
        }

        boolean valid = true;

        String password = request.password();
        String passwordConfirm = request.passwordConfirm();
        String nickname = request.nickname();

        // 비밀번호 최대 바이트 길이 검사
        if (hasText(password)
                && password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            addViolation(
                    context,
                    "password",
                    "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다."
            );

            valid = false;
        }

        // 비밀번호와 비밀번호 확인 비교
        if (hasText(password)
                && hasText(passwordConfirm)
                && !password.equals(passwordConfirm)) {

            addViolation(
                    context,
                    "passwordConfirm",
                    "비밀번호가 일치하지 않습니다."
            );

            valid = false;
        }

        // 기준 닉네임 정책 재사용
        if (hasText(nickname)) {
            try {
                MemberInputPolicy.normalizedNickname(nickname);
            } catch (IllegalArgumentException exception) {
                addViolation(
                        context,
                        "nickname",
                        "닉네임은 2~20자의 한글·영문·숫자·밑줄만 사용할 수 있으며, "
                                + "예약된 이름은 사용할 수 없습니다."
                );

                valid = false;
            }
        }

        return valid;
    }

    private void addViolation(ConstraintValidatorContext context, String field, String message) {
        context.disableDefaultConstraintViolation();

        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field) // 오류를 해당 필드에 연결한다. 이를 통해 나중에 프론트엔드에 비밀번호 확인 입력란의 오류로 전달 가능
                .addConstraintViolation();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
