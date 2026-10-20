package io.github.takgeun.iyum.member.api.validation;

import io.github.takgeun.iyum.member.api.dto.MemberNicknameUpdateRequest;
import io.github.takgeun.iyum.member.domain.MemberInputPolicy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MemberNicknameUpdateRequestValidator
        implements ConstraintValidator<
        ValidMemberNicknameUpdateRequest,
        MemberNicknameUpdateRequest
        > {

    @Override
    public boolean isValid(
            MemberNicknameUpdateRequest request,
            ConstraintValidatorContext context
    ) {
        // 요청 본문 자체의 누락은 @RequestBody에서 처리
        if (request == null) {
            return true;
        }

        try {
            MemberInputPolicy.normalizedNickname(request.nickname());
            return true;
        } catch (IllegalArgumentException exception) {
            context.disableDefaultConstraintViolation();

            context.buildConstraintViolationWithTemplate(
                            exception.getMessage()
                    )
                    // 오류가 닉네임 입력칸에 해당한다는 정보 알리기
                    // 즉, json 응답에서 fieldErrors에서 "field": "nickname" 넣을 수 있게 해줌
                    .addPropertyNode("nickname")
                    .addConstraintViolation();

            return false;
        }
    }
}
