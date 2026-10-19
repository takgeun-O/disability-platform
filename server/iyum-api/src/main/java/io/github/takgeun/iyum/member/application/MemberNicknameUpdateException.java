package io.github.takgeun.iyum.member.application;

import lombok.Getter;

@Getter
public class MemberNicknameUpdateException extends RuntimeException {

    private final Code code;

    public MemberNicknameUpdateException(Code code) {
        super(code.message);
        this.code = code;
    }

    // cause를 받는 생성자는 원래 발생한 예외를 보존하면서 서비스 예외로 변환하기 위한 것.
    // 다른 예외를 잡아서 서비스 예외로 변환하는 경우
    // 닉네임을 저장하다가 DB의 닉네임 중복 제약을 위반한 경우가 대표적
//    try {
//        memberRepository.flush();
//    } catch (DataIntegrityViolationException exception) {
//        // 실제로 닉네임 중복 제약 위반인지 확인한 경우
//        throw new MemberNicknameUpdateException(
//                MemberNicknameUpdateException.Code.NICKNAME_ALREADY_EXISTS,
//                exception
//        );
//    }
//    --> 이후 다음처럼 확인 가능
//      exception.getMessage(); // "이미 사용 중인 닉네임입니다."
//      exception.getCause();   // 원래 발생한 DataIntegrityViolationException
//    이렇게 하면 DB 예외를 잡아서 MemberNicknameUpdateException의 NICKNAME_ALREADY_EXISTS으로 변환 가능
    public MemberNicknameUpdateException(
            Code code,  // 애플리케이션에서 이해하고 처리할 실패 사유
            Throwable cause // DB 저장 중 실제로 발생한 원래 예외
    ) {
        super(code.message, cause);
        this.code = code;
    }

    public enum Code {

        MEMBER_NOT_FOUND(
                "회원 정보를 확인할 수 없습니다."
        ),

        MEMBER_NOT_ACTIVE(
                "활성 상태의 회원만 닉네임을 변경할 수 있습니다."
        ),

        INVALID_NICKNAME(
                "닉네임 형식을 확인해 주세요. 예약된 닉네임은 사용할 수 없습니다."
        ),

        NICKNAME_ALREADY_EXISTS(
                "이미 사용 중인 닉네임입니다."
        );

        private final String message;

        Code(String message) {
            this.message = message;
        }
    }
}
