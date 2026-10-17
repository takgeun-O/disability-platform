package io.github.takgeun.iyum.community.comment.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CommentUpdateRequestValidator.class)
public @interface ValidCommentUpdateRequest {

    String message() default "댓글 수정 입력값이 올바르지 않습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
