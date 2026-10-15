package io.github.takgeun.iyum.community.post.api.validation;

import io.github.takgeun.iyum.community.post.api.dto.PostUpdateRequest;
import io.github.takgeun.iyum.community.post.domain.PostInputPolicy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PostUpdateRequestValidator
        implements ConstraintValidator<
        ValidPostUpdateRequest,
        PostUpdateRequest
        > {

    @Override
    public boolean isValid(
            PostUpdateRequest request,
            ConstraintValidatorContext context
    ) {
        // 개별 필드 검증을 생략한다는 뜻
        if (request == null) {
            return true;
        }

        boolean valid = true;

        // 제목 검사
        try {
            PostInputPolicy.normalizeTitle(request.title());
        } catch (IllegalArgumentException exception) {
            addViolation(
                    context,
                    "title",
                    exception.getMessage()
            );

            valid = false;
        }

        // 본문 검사
        try {
            PostInputPolicy.validateContent(request.content());
        } catch (IllegalArgumentException exception) {
            addViolation(
                    context,
                    "content",
                    exception.getMessage()
            );

            valid = false;
        }

        return valid;
    }

    private void addViolation(
            ConstraintValidatorContext context,
            String field,
            String message
    ) {
        context.disableDefaultConstraintViolation();

        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addConstraintViolation();
    }
}
