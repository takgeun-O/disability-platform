package io.github.takgeun.iyum.community.post.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PostUpdateRequestValidator.class) // 이 어노테이션의 검증을 어느 클래스가 담당하는지 설정
public @interface ValidPostUpdateRequest {

    String message() default "게시글 수정 입력값이 올바르지 않습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
