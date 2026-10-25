package io.github.takgeun.iyum.member.api.validation;

import io.github.takgeun.iyum.member.api.dto.MemberPasswordChangeRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MemberPasswordChangeRequestValidator
        implements ConstraintValidator<
        ValidMemberPasswordChangeRequest,
        MemberPasswordChangeRequest
        > {

    /**
     * 여기서 통과하는 검증은 입력 형식만을 검증하며,
     * 변경 권한 확인이나 비밀번호 변경 완료를 의미하지 않음.
     */
    @Override
    public boolean isValid(
            MemberPasswordChangeRequest request,
            ConstraintValidatorContext context
    ) {
        // 요청 본문 자체의 누락은 이후 컨트롤러의 @RequestBody에서 처리한다.
        if (request == null) {
            return true;
        }

        // DTO에서 넘어온 값들 검증 준비 및 시작
        String password = request.newPassword();
        String confirmation = request.newPasswordConfirm();

        // 필수값 오류를 등록하는 역할은 각 필드의 @NotBlank가 담당한다.
        // 그러니 여기서는 빈 확인값에 "필수 입력"과 "불일치" 오류를 중복 등록하지 않는다.
        // 검증기에서도 필수 여부를 검사할 수는 있으나, 동일한 작업을 한다고 더 안전해지는 건 아니고
        // 오히려 역할을 나누는 것이 추후 유지 보수에도 좋다.
        if (!hasText(password) || !hasText(confirmation)) {
            return true;
        }

        // trim이나 strip 없이 입력 원문끼리 비교한다.
        if (password.equals(confirmation)) {
            return true;
        }

        context.disableDefaultConstraintViolation();

        context.buildConstraintViolationWithTemplate(
                        "비밀번호가 일치하지 않습니다."
                )
                // DTO 전체 오류가 아니라 확인 입력칸의 오류로 전달한다.
                .addPropertyNode("newPasswordConfirm")
                .addConstraintViolation();

        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
