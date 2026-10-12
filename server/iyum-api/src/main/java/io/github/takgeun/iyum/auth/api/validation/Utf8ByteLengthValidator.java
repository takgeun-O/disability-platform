package io.github.takgeun.iyum.auth.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class Utf8ByteLengthValidator implements ConstraintValidator<Utf8ByteLength, String> {

    private int max;

    @Override
    public void initialize(Utf8ByteLength annotation) {
        this.max = annotation.max();
    }

    @Override
    public boolean isValid(
            String value,
            ConstraintValidatorContext context
    ) {
        if(value == null) {
            // 이 검증기는 길이만 담당하기 때문에
            // 필수 입력 검사는 @NotBlank가 담당하도록 분리하기 위해 들어온 문자열이 없으면 그냥 true 반환
            return true;
        }

        // 문자열을 UTF-8 바이트 배열로 변환해서 길이 확인
        return value.getBytes(StandardCharsets.UTF_8).length <= max;
    }
}
