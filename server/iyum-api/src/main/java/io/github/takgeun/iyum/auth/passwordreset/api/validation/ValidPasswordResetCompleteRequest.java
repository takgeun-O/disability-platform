package io.github.takgeun.iyum.auth.passwordreset.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

// 여러 필드를 함께 검사하는 검증기를 DTO에 연결하는 어노테이션
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordResetCompleteRequestValidator.class)
public @interface ValidPasswordResetCompleteRequest {

    String message() default "비밀번호 재설정 입력값이 올바르지 않습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
