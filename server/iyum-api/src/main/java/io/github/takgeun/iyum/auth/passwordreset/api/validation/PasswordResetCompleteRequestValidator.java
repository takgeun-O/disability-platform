package io.github.takgeun.iyum.auth.passwordreset.api.validation;

import io.github.takgeun.iyum.auth.passwordreset.api.dto.PasswordResetCompleteRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class PasswordResetCompleteRequestValidator
        implements ConstraintValidator<
        ValidPasswordResetCompleteRequest,
        PasswordResetCompleteRequest
        > {

    private static final int MAX_PASSWORD_BYTES = 72;

    @Override
    public boolean isValid(
            PasswordResetCompleteRequest request,
            ConstraintValidatorContext context
    ) {
        if (request == null) {
            return true;
        }

        boolean valid = true;

        String password = request.newPassword();
        String confirmation = request.newPasswordConfirm();

        // 필수값 검사는 DTO의 @NotBlank에서 담당한다.
        if (hasText(password)
                && password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES
        ) {
            addViolation(
                    context,
                    "newPassword",
                    "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다."
            );

            valid = false;
        }

        // 빈 확인값에는 @NotBlank 오류만 표시
        if (hasText(password)
                && hasText(confirmation)
                && !password.equals(confirmation)) {

            addViolation(
                    context,
                    "newPasswordConfirm",
                    "비밀번호가 일치하지 않습니다."
            );

            valid = false;
        }

        return valid;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    // 커스텀 검증에서 특정 필드에 원하는 오류 메시지를 등록하는 보조 메서드
    private void addViolation(
            ConstraintValidatorContext context,
            String field,
            String message
    ) {
        // 기본 오류 메시지 사용 중지
        // 검증 애노테이션에 설정된 기본 위반 메시지가 자동으로 추가되지 않게 한다.
        context.disableDefaultConstraintViolation();

        // 오류 메시지 지정
        // 매개변수로 받은 message를 사용해서 오류를 만든다.
        context.buildConstraintViolationWithTemplate(message)
                // 오류가 발생한 필드 지정
                // DTO 전체에 붙인 검증 애노테이션에서 특정 필드의 오류로 표시하고 싶을 떄 유용
                // 이 설정 덕분에 확인값 불일치는 비밀번호 확인 입력칸의 오류로 전달 가능
                .addPropertyNode(field)
                .addConstraintViolation();  // 구성한 오류 등록
    }
}
