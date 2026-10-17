package io.github.takgeun.iyum.community.comment.api.validation;

import io.github.takgeun.iyum.community.comment.api.dto.CommentUpdateRequest;
import io.github.takgeun.iyum.community.comment.domain.CommentInputPolicy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 1. 요청 JSON을 CommentUpdateRequest로 반환
 * 2. 컨트롤러에 있는 @Valid에 의해 검증기가 실행된다.
 * 3. 본문 정책 위반 예외를 잡아서 content 오류로 등록한다.
 * 4. false를 반환하면 Spring MVC가 MethodArgumentNotValidException을 발생시킨다.
 * 5. 기존 GlobalExceptionHandler가 이를 HTTP 400 응답으로 변환
 */
public class CommentUpdateRequestValidator
        implements ConstraintValidator<
            ValidCommentUpdateRequest,
            CommentUpdateRequest
        > {

    @Override
    public boolean isValid(
            CommentUpdateRequest request,
            ConstraintValidatorContext context
    ) {
        // 요청 본문 자체의 누락은 컨트롤러의 @RequestBody에서 처리한다.
        if (request == null) {
            return true;
        }

        try {
            // 작성과 수정에 동일한 본문 정책 적용
            CommentInputPolicy.validateContent(request.content());
            return true;
        } catch (IllegalArgumentException exception) {
            // 본문 검증에서 발생한 예외를 content 필드의 검증 오류로 바꾼다.

            // 1. 기본 오류 메시지를 사용하지 않도록 한다.
            // @ValidCommentUpdateRequest에 정의된 기본 message 대신 직접 만든 오류 메시지 사용
            context.disableDefaultConstraintViolation();

            // 2. 예외 메시지로 검증 오류를 만든다.
            context.buildConstraintViolationWithTemplate(
                            exception.getMessage()
                    )
                    // 3. 오류가 발생한 필드 지정
                    // "이 오류는 요청 객체 전체가 아니라 content 필드에 해당한다"라는 의미
                    // 이 검증기는 CommentUpdateRequest 객체 전체를 검증하므로
                    // 필드를 지정하지 않으면 객체 수준의 오류가 된다.
                    // 여기서 필드명을 지정해서 프론트에서 댓글 입력창에 오류를 표시할 수 있도록 함
                    .addPropertyNode("content")
                    // 4. 설정한 오류를 실제로 등록한다.
                    .addConstraintViolation();

            return false;   // 검증 실패 반환
        }
    }
}
