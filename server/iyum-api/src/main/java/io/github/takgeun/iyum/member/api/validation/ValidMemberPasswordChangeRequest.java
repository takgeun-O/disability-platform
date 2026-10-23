package io.github.takgeun.iyum.member.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

// DTO 전체에 붙일 검증 애노테이션 추가
// 여기서는 실제 검사 로직이 없고, 이 애노테이션이 붙은 객체는
// MemberPasswordChangeRequestValidator로 검사하라는 연결 정보가 들어있음.
@Documented // Javadoc으로 생성한 API 문서에 포함되도록 한다.
@Target(ElementType.TYPE)   // 붙일 수 있는 위치 지정 (클래스, 인터페이스, record 같은 타입 선언에 붙일 수 있다는 의미
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MemberPasswordChangeRequestValidator.class)
public @interface ValidMemberPasswordChangeRequest {    // 애노테이션 정의

    // 검증 실패 시 사용할 기본 오류 메시지
    String message() default "비밀번호 변경 입력값이 올바르지 않습니다.";

    // 특정 검증 그룹에서만 실행하도록 구분할 때 사용
    Class<?>[] groups() default {};

    // 오류 심각도 등 추가 메타데이터를 지정할 때 사용. (요청 본문을 뜻하는 것은 아님)
    Class<? extends Payload>[] payload() default {};
}
