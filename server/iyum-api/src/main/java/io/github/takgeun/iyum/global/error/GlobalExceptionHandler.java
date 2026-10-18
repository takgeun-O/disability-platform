package io.github.takgeun.iyum.global.error;

import io.github.takgeun.iyum.auth.emailverification.application.InvalidEmailVerificationTokenException;
import io.github.takgeun.iyum.auth.signup.application.SignupConflictException;
import io.github.takgeun.iyum.auth.signup.application.SignupTermsException;
import io.github.takgeun.iyum.auth.security.EmailVerificationRequiredException;
import io.github.takgeun.iyum.community.comment.application.CommentCreateException;
import io.github.takgeun.iyum.community.comment.application.CommentDeleteException;
import io.github.takgeun.iyum.community.comment.application.CommentReadException;
import io.github.takgeun.iyum.community.comment.application.CommentUpdateException;
import io.github.takgeun.iyum.community.post.application.PostCreateException;
import io.github.takgeun.iyum.community.post.application.PostDeleteException;
import io.github.takgeun.iyum.community.post.application.PostReadException;
import io.github.takgeun.iyum.community.post.application.PostUpdateException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Comparator;
import java.util.List;

import static io.github.takgeun.iyum.community.comment.application.CommentUpdateException.Code.COMMENT_NOT_FOUND;

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

    // URL 파라미터 형식 오류 처리
    // 예를 들어 GET /api/v1/community/posts?page=abc 이렇게 잘못된 요청을 처리할 때 사용
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        ApiErrorResponse body = ApiErrorResponse.of(
                "INVALID_REQUEST_PARAMETER",
                "요청 파라미터의 형식과 값을 확인해 주세요."
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

    /**
     * 이메일 인증 처리 중 발생한 예외를 클라이언트가 이해할 수 있는 HTTP 400 오류 응답으로 바꾸는 역할
     * <p>
     * 1. 클라이언트가 이메일 인증 요청 보냄
     * 프론트엔드가 이메일 링크에서 얻은 토큰을 요청 본문에 담아 서버로 보낸다.
     * -> 해당 요청은 제일 먼저 Spring Security 필터를 거친다.
     * -> CSRF 등 보안 검사를 통과하면 Spring MVC가 해당 요청을 처리할 컨트롤러를 찾아 실행한다.
     * 2.컨트롤러가 인증 서비스를 호출한다.
     * 서비스는 토큰의 해시로 DB 기록을 찾고, 회원 상태와 토큰의 만료·사용·폐기 여부를 검사한다.
     * 3. 서비스에서 사용할 수 없는 토큰을 발견하고 예외를 던진다. (InvalidEmailVerificationTokenException)
     * 이 시점부터 정상 처리 흐름은 중단된다. 컨트롤러도 정상 결과를 받지 못하므로 성공 응답을 만드는 코드로 진행하지 않는다.
     * 이 예외가 RuntimeException을 상속하고 기본 @Transactional 설정을 사용한다면, 서비스 트랜잭션의 변경 사항도 롤백 대상이 된다.
     * 4. Spring이 해당 예외를 처리할 메서드를 찾는다.
     * Spring MVC는 처리되지 않은 예외에 맞는 @ExceptionHandler를 찾아 호출한다. -> @ExceptionHandler(InvalidEmailVerificationTokenException.class) 발견!
     * 파라미터 exception에는 실제 발생한 예외 객체(InvalidEmailVerificationTokenException)가 전달된다.
     * 5. 응답 본문 객체를 생성한다.
     * 6. HTTP 상태·헤더·본문을 조합해서 반환
     */
    @ExceptionHandler(InvalidEmailVerificationTokenException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidEmailVerificationToken(
            InvalidEmailVerificationTokenException exception
    ) {
        ApiErrorResponse body = ApiErrorResponse.of(
                "EMAIL_VERIFICATION_INVALID",
                "유효하지 않거나 사용할 수 없는 이메일 인증 링크입니다."
        );

        /**
         * HTTP/1.1 400 Bad Request
         * Content-Type: application/json
         * Cache-Control: no-store
         *
         * {
         *   "code": "EMAIL_VERIFICATION_INVALID",
         *   "message": "유효하지 않거나 사용할 수 없는 이메일 인증 링크입니다.",
         *   "fieldErrors": []
         * }
         */
        return ResponseEntity
                .badRequest()
                // HTTP 캐시는 브라우저나 중간 서버가 응답을 저장해 두었다가 재사용하는 기능
                .cacheControl(CacheControl.noStore())   // 인증 실패 응답을 HTTP 캐시에 저장하지 마라.
                .body(body);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(
            BadCredentialsException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                // HTTP 캐시는 브라우저나 중간 서버가 응답을 저장해 두었다가 재사용하는 기능
                .cacheControl(CacheControl.noStore())   // 인증 실패 응답을 HTTP 캐시에 저장하지 마라.
                .body(ApiErrorResponse.of(
                        "LOGIN_FAILED",
                        "이메일 또는 비밀번호를 확인해 주세요."
                ));
    }

    @ExceptionHandler(EmailVerificationRequiredException.class)
    public ResponseEntity<ApiErrorResponse> handleEmailVerificationRequired(
            EmailVerificationRequiredException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .cacheControl(CacheControl.noStore())
                .body(ApiErrorResponse.of(
                        "EMAIL_VERIFICATION_REQUIRED",
                        "이메일 인증을 완료해 주세요."
                ));
    }

    @ExceptionHandler(AccountStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountStatus(
            AccountStatusException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .cacheControl(CacheControl.noStore())
                .body(ApiErrorResponse.of(
                        "ACCOUNT_UNAVAILABLE",
                        "현재 로그인할 수 없는 계정입니다."
                ));
    }

    @ExceptionHandler(AuthenticationServiceException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationService(
            AuthenticationException exception
    ) {
        return ResponseEntity
                .internalServerError()
                .cacheControl(CacheControl.noStore())
                .body(ApiErrorResponse.of(
                        "INTERNAL_SERVER_ERROR",
                        "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
                ));
    }

    // 게시글 작성 예외를 HTTP 응답으로 연결하기
    @ExceptionHandler(PostCreateException.class)
    public ResponseEntity<ApiErrorResponse> handlePostCreate(
            PostCreateException exception
    ) {
        return switch (exception.getCode()) {

            case AUTHOR_NOT_FOUND -> ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .cacheControl(CacheControl.noStore())
                    .body(ApiErrorResponse.of(
                            "AUTHENTICATION_REQUIRED",
                            "로그인이 필요합니다."
                    ));

            case AUTHOR_NOT_ACTIVE -> ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .cacheControl(CacheControl.noStore())
                    .body(ApiErrorResponse.of(
                            "POST_WRITE_NOT_ALLOWED",
                            exception.getMessage()
                    ));
        };
    }

    @ExceptionHandler(PostReadException.class)
    public ResponseEntity<ApiErrorResponse> handlePostRead(
            PostReadException exception
    ) {
        HttpStatus status = switch (exception.getCode()) {
            case INVALID_PAGE_REQUEST, INVALID_CURSOR ->
                    HttpStatus.BAD_REQUEST;

            case POST_NOT_FOUND ->
                    HttpStatus.NOT_FOUND;
        };

        return ResponseEntity
                .status(status)
                .cacheControl(CacheControl.noStore())
                .body(ApiErrorResponse.of(
                        exception.getCode().name(),
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(PostUpdateException.class)
    public ResponseEntity<ApiErrorResponse> handlePostUpdate(
            PostUpdateException exception
    ) {
        // 참고로 비로그인 요청에 CSRF 토큰까지 없으면 CSRF 검사에서 먼저 403이 나올 수 있음.
        HttpStatus status = switch (exception.getCode()) {
            case MEMBER_NOT_FOUND -> HttpStatus.UNAUTHORIZED;
            case MEMBER_NOT_ACTIVE, NOT_POST_AUTHOR -> HttpStatus.FORBIDDEN;
            case POST_NOT_FOUND -> HttpStatus.NOT_FOUND;
        };

        String responseCode = switch (exception.getCode()) {
            case MEMBER_NOT_FOUND -> "AUTHENTICATION_REQUIRED";
            case MEMBER_NOT_ACTIVE -> "POST_WRITE_NOT_ALLOWED";
            case NOT_POST_AUTHOR -> "ACCESS_DENIED";
            case POST_NOT_FOUND -> "POST_NOT_FOUND";
        };

        String message =
                exception.getCode() == PostUpdateException.Code.MEMBER_NOT_FOUND
                        ? "로그인이 필요합니다."
                        : exception.getMessage();

        return ResponseEntity
                .status(status)
                .cacheControl(CacheControl.noStore())
                .body(ApiErrorResponse.of(
                        responseCode,
                        message
                ));
    }

    @ExceptionHandler(PostDeleteException.class)
    public ResponseEntity<ApiErrorResponse> handlePostDelete(
            PostDeleteException exception
    ) {
        HttpStatus status = switch (exception.getCode()) {
            case MEMBER_NOT_FOUND -> HttpStatus.UNAUTHORIZED;
            case MEMBER_NOT_ACTIVE, NOT_POST_AUTHOR -> HttpStatus.FORBIDDEN;
            case POST_NOT_FOUND -> HttpStatus.NOT_FOUND;
        };

        String code = switch (exception.getCode()) {
            case MEMBER_NOT_FOUND -> "AUTHENTICATION_REQUIRED";
            case MEMBER_NOT_ACTIVE -> "POST_WRITE_NOT_ALLOWED";
            case NOT_POST_AUTHOR -> "ACCESS_DENIED";
            case POST_NOT_FOUND -> "POST_NOT_FOUND";
        };

        String message =
                exception.getCode() == PostDeleteException.Code.MEMBER_NOT_FOUND
                        ? "로그인이 필요합니다."
                        : exception.getMessage();

        return ResponseEntity
                .status(status)
                .cacheControl(CacheControl.noStore())
                .body(ApiErrorResponse.of(code, message));
    }

    @ExceptionHandler(CommentReadException.class)
    public ResponseEntity<ApiErrorResponse> handleCommentRead(
            CommentReadException exception
    ) {
        HttpStatus status = switch (exception.getCode()) {
            case INVALID_PAGE_REQUEST, INVALID_CURSOR ->
                    HttpStatus.BAD_REQUEST;

            case POST_NOT_FOUND ->
                    HttpStatus.NOT_FOUND;
        };

        return ResponseEntity
                .status(status)
                .cacheControl(CacheControl.noStore())
                .body(ApiErrorResponse.of(
                        exception.getCode().name(),
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(CommentCreateException.class)
    public ResponseEntity<ApiErrorResponse> handleCommentCreate(
            CommentCreateException exception
    ) {
        HttpStatus status = switch (exception.getCode()) {
            case AUTHOR_NOT_FOUND -> HttpStatus.UNAUTHORIZED;
            case AUTHOR_NOT_ACTIVE -> HttpStatus.FORBIDDEN;
            case POST_NOT_FOUND -> HttpStatus.NOT_FOUND;
        };

        String code = switch (exception.getCode()) {
            case AUTHOR_NOT_FOUND -> "AUTHENTICATION_REQUIRED";
            case AUTHOR_NOT_ACTIVE -> "COMMENT_WRITE_NOT_ALLOWED";
            case POST_NOT_FOUND -> "POST_NOT_FOUND";
        };

        String message =
                exception.getCode()
                        == CommentCreateException.Code.AUTHOR_NOT_FOUND
                        ? "로그인이 필요합니다."
                        : exception.getMessage();

        return ResponseEntity
                .status(status)
                .cacheControl(CacheControl.noStore())
                .body(ApiErrorResponse.of(
                        code,
                        message
                ));
    }

    @ExceptionHandler(CommentUpdateException.class)
    public ResponseEntity<ApiErrorResponse> handleCommentUpdate(
            CommentUpdateException exception
    ) {
        HttpStatus status = switch (exception.getCode()) {
            case MEMBER_NOT_FOUND -> HttpStatus.UNAUTHORIZED;

            case MEMBER_NOT_ACTIVE, NOT_COMMENT_AUTHOR ->
                    HttpStatus.FORBIDDEN;

            case POST_NOT_FOUND, COMMENT_NOT_FOUND ->
                    HttpStatus.NOT_FOUND;
        };

        String code = switch (exception.getCode()) {
            case MEMBER_NOT_FOUND -> "AUTHENTICATION_REQUIRED";
            case MEMBER_NOT_ACTIVE -> "COMMENT_WRITE_NOT_ALLOWED";
            case NOT_COMMENT_AUTHOR -> "ACCESS_DENIED";
            case POST_NOT_FOUND -> "POST_NOT_FOUND";
            case COMMENT_NOT_FOUND -> "COMMENT_NOT_FOUND";
        };

        String message =
                exception.getCode()
                        == CommentUpdateException.Code.MEMBER_NOT_FOUND
                        ? "로그인이 필요합니다."
                        : exception.getMessage();

        return ResponseEntity
                .status(status)
                .cacheControl(CacheControl.noStore())
                .body(ApiErrorResponse.of(code, message));
    }

    @ExceptionHandler(CommentDeleteException.class)
    public ResponseEntity<ApiErrorResponse> handleCommentDelete(
            CommentDeleteException exception
    ) {
        HttpStatus status = switch (exception.getCode()) {
            case MEMBER_NOT_FOUND -> HttpStatus.UNAUTHORIZED;

            case MEMBER_NOT_ACTIVE, NOT_COMMENT_AUTHOR ->
                    HttpStatus.FORBIDDEN;

            case POST_NOT_FOUND, COMMENT_NOT_FOUND ->
                    HttpStatus.NOT_FOUND;
        };

        String code = switch (exception.getCode()) {
            case MEMBER_NOT_FOUND -> "AUTHENTICATION_REQUIRED";
            case MEMBER_NOT_ACTIVE -> "COMMENT_WRITE_NOT_ALLOWED";
            case NOT_COMMENT_AUTHOR -> "ACCESS_DENIED";
            case POST_NOT_FOUND -> "POST_NOT_FOUND";
            case COMMENT_NOT_FOUND -> "COMMENT_NOT_FOUND";
        };

        String message =
                exception.getCode()
                        == CommentDeleteException.Code.MEMBER_NOT_FOUND
                        ? "로그인이 필요합니다."
                        : exception.getMessage();

        return ResponseEntity
                .status(status)
                .cacheControl(CacheControl.noStore())
                .body(ApiErrorResponse.of(code, message));
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