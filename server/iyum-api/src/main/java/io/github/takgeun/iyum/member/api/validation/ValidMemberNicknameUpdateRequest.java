package io.github.takgeun.iyum.member.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MemberNicknameUpdateRequestValidator.class)
public @interface ValidMemberNicknameUpdateRequest {

    String message() default "닉네임 입력값이 올바르지 않습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
