package io.github.takgeun.iyum.global.error;

import io.github.takgeun.iyum.auth.application.SignupConflictException;
import io.github.takgeun.iyum.auth.application.SignupTermsException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Comparator;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    public ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        // rejectedValue, BindingResult 전체, 요청 DTO는 응답이나 로그에 넣지 않는다.
        List<ApiErrorResponse.FieldError> fieldErrors = exception
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ApiErrorResponse.FieldError(
                        error.getField(),
                        error.getDefaultMessage() == null
                                ? "입력값을 확인해 주세요."
                                : error.getDefaultMessage()
                ))
                .distinct()
                .sorted(
                        Comparator
                                .comparing(ApiErrorResponse.FieldError::field)
                                .thenComparing(ApiErrorResponse.FieldError::message)
                )
                .toList();

        ApiErrorResponse body = new ApiErrorResponse(
                "VALIDATION_FAILED",
                "입력값을 확인해 주세요.",
                fieldErrors
        );

        return new ResponseEntity<>(body, headers, status);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        // 파서 예외에는 요청 원문이 포함될 수 있으므로 고정 메시지를 반환한다.
        ApiErrorResponse body = ApiErrorResponse.of(
                "INVALID_REQUEST_BODY",
                "요청 본문의 JSON 형식과 필드 값을 확인해 주세요."
        );

        return new ResponseEntity<>(body, headers, status);
    }

    @ExceptionHandler(SignupTermsException.class)
    public ResponseEntity<ApiErrorResponse> handleSignupTerms(
            SignupTermsException exception
    ) {
        ApiErrorResponse body = ApiErrorResponse.of(
                exception.getCode().name(),
                exception.getMessage()
        );

        return ResponseEntity
                .badRequest()
                .body(body);
    }

    @ExceptionHandler(SignupConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleSignupConflict(
            SignupConflictException exception
    ) {
        ApiErrorResponse body = ApiErrorResponse.of(
                exception.getCode().name(),
                exception.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(body);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        // 405/415 등의 MVC 오류도 내부 예외 대신 같은 JSON 구조로 반환한다.
        ApiErrorResponse errorBody = ApiErrorResponse.of(
                "HTTP_ERROR",
                "요청을 처리할 수 없습니다."
        );

        return super.handleExceptionInternal(
                exception,
                errorBody,
                headers,
                status,
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception
    ) {
        // 인증·인가 예외는 Security 필터가 처리하도록 유지한다.
        if (exception instanceof AccessDeniedException denied) {
            throw denied;
        }

        if (exception instanceof AuthenticationException authentication) {
            throw authentication;
        }

        // 알려진 중복 제약만 SignupService에서 변환한다.
        // 그 외 DB 오류는 409가 아니다.
        ApiErrorResponse body = ApiErrorResponse.of(
                "INTERNAL_SERVER_ERROR",
                "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
        );

        return ResponseEntity
                .internalServerError()
                .body(body);
    }
}