package io.github.takgeun.iyum.community.post.api.validation;

import io.github.takgeun.iyum.community.post.api.dto.PostCreateRequest;
import io.github.takgeun.iyum.community.post.domain.PostInputPolicy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

// 제목·본문 검증기
public class PostCreateRequestValidator implements ConstraintValidator<ValidPostCreateRequest, PostCreateRequest> {

    @Override
    public boolean isValid(PostCreateRequest request, ConstraintValidatorContext context) {

        // request의 제목과 본문을 각각 검사하고,
        // 둘 다 잘못되면 바깥에서 두 필드의 오류를 모두 등록하는 구조
        if(request == null) {
            return true;
        }

        boolean valid = true;

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

                // 클래스 전체에 붙은 검증 오류를 title 또는 content 라는 개별 필드에 연결
                // 그래야 기존 GlobalExceptionHandler가 읽는 fieldErrors에 포함된다.
                .addPropertyNode(field)

                .addConstraintViolation();
    }
}
