package io.github.takgeun.iyum.auth.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME) // 실행 중에 검증기가 이 어노테이션을 읽을 수 있도록 설정
@Constraint(validatedBy = SignupRequestValidator.class) // 실제 검증을 수행할 클래스 지정
public @interface ValidSignupRequest {

    /*
    사용자 정의 검증 어노테이션의 구성
     */
    String message() default "회원가입 입력값이 올바르지 않습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
