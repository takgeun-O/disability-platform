package io.github.takgeun.iyum.auth.session.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * 사용자 정의 검증 애노테이션을 만드는 코드
 */
@Documented
// 실제 검증을 수행할 클래스 지정
// "이 애노케이션이 붙은 값은 Utf8ByteLengthValidator로 검사해줘"
@Constraint(validatedBy = Utf8ByteLengthValidator.class)
@Target({
        // 애노테이션을 붙일 수 있는 위치 지정
        ElementType.FIELD,  // 클래스 필드
        ElementType.METHOD, // 메서드
        ElementType.PARAMETER,  // 메서드·생성자의 파라미터
        ElementType.ANNOTATION_TYPE,    // 다른 애노테이션 선언
        ElementType.RECORD_COMPONENT    // record의 구성 요소
})
@Retention(RetentionPolicy.RUNTIME)
public @interface Utf8ByteLength {

    /**
     * 사용할 때 이렇게 사용한다.
     * @Utf8ByteLength(max = 72)
     * String password
     */
    String message() default
            "UTF-8 기준 {max}바이트 이하여야 합니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    // 최대 바이트 수 지정
    int max();
}
