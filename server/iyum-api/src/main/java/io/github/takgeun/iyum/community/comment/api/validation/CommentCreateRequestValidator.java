package io.github.takgeun.iyum.community.comment.api.validation;

import io.github.takgeun.iyum.community.comment.api.dto.CommentCreateRequest;
import io.github.takgeun.iyum.community.comment.domain.CommentInputPolicy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CommentCreateRequestValidator
        implements ConstraintValidator<
        ValidCommentCreateRequest,
        CommentCreateRequest
        > {

    @Override
    public boolean isValid(
            CommentCreateRequest request,
            ConstraintValidatorContext context
    ) {
        // 요청 객체 자체의 필수 여부는 컨트롤러의
        // @RequestBody 또는 별도 @NotNull에서 처리
        if (request == null) {
            return true;
        }

        try {
            // 본문 검증 규칙을 여기서 다시 작성하지 않고 기존 도메인 정책을 그대로 호출
            // 이렇게 해야 API 요청과 엔티티 생성에 같은 기준이 적용
            CommentInputPolicy.validateContent(request.content());
            return true;
        } catch (IllegalArgumentException exception) {
            context.disableDefaultConstraintViolation();

            context.buildConstraintViolationWithTemplate(
                            exception.getMessage()
                    )
                    // 검증 오류를 content 필드의 오류로 등록
                    // 이 연결이 있어야 기존 GlobalExceptionHandler가 처리하는 fieldErrors에 다음처럼 들어간다.
        //            {
        //                "code": "VALIDATION_FAILED",
        //                    "message": "입력값을 확인해 주세요.",
        //                    "fieldErrors": [
        //                {
        //                    "field": "content",
        //                        "message": "댓글 내용은 필수입니다."
        //                }
        //  ]
        //            }
                    .addPropertyNode("content")
                    .addConstraintViolation();

            return false;
        }
    }
}
