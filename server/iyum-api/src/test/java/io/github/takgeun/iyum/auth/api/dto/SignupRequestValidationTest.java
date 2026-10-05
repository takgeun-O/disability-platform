package io.github.takgeun.iyum.auth.api.dto;

import io.github.takgeun.iyum.auth.application.SignupTermsException;
import io.github.takgeun.iyum.auth.application.SignupTermsPolicy;
import io.github.takgeun.iyum.member.domain.TermsCode;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.github.takgeun.iyum.member.domain.TermsCode.*;
import static org.assertj.core.api.Assertions.*;

public class SignupRequestValidationTest {

    private static final String PASSWORD = "Pass1234!";

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void 정상_입력을_허용하고_이메일과_닉네임을_정리한다() {
        String originalPassword = " Pass1234! ";

        SignupRequest request = request(
                " Member@Example.COM ",
                originalPassword,
                originalPassword,
                " 타끈_01 "
        );

        assertThat(invalidFields(request)).isEmpty();

        assertThat(request.email()).isEqualTo("member@example.com");
        assertThat(request.email()).isEqualTo("member@example.com");
        assertThat(request.nickname()).isEqualTo("타끈_01");

        assertThat(request.password()).isEqualTo(originalPassword);
        assertThat(request.passwordConfirm()).isEqualTo(originalPassword);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "not-an-email",
            "member@"
    })
    void 이메일이_없거나_형식이_잘못되면_실패한다(String email) {
        SignupRequest request = request(
                email,
                PASSWORD,
                PASSWORD,
                "타끈_01"
        );

        assertThat(invalidFields(request)).contains("email");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "abc1234",
            "abcdefgh",
            "12345678"
    })
    void 비밀번호_입력_규칙을_위반하면_실패한다(String password) {
        SignupRequest request = request(
                "member@example.com",
                password,
                password,
                "타끈_01"
        );

        assertThat(invalidFields(request)).contains("password");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void 비밀번호_확인이_없으면_실패한다(String passwordConfirm) {
        SignupRequest request = request(
                "member@example.com",
                PASSWORD,
                passwordConfirm,
                "타끈_01"
        );

        assertThat(invalidFields(request)).contains("passwordConfirm");
    }

    @Test
    void 비밀번호가_다르면_확인_필드에_오류가_생긴다() {
        SignupRequest request = request(
                "member@example.com",
                PASSWORD,
                "Different123!",
                "타끈_01"
        );

        assertThat(invalidFields(request))
                .containsExactly("passwordConfirm");
    }

    @Test
    void 비밀번호는_UTF8_72바이트까지_허용한다() {
        // 영문·숫자 3바이트 + 한글 23자 × 3바이트 = 72바이트
        String allowedPassword = "Ab1" + "가".repeat(23);

        SignupRequest allowed = request(
                "member@example.com",
                allowedPassword,
                allowedPassword,
                "타끈_01"
        );

        assertThat(invalidFields(allowed)).isEmpty();

        // 영문·숫자 3바이트 + 한글 24자 × 3바이트 = 75바이트
        String tooLongPassword = "Ab1" + "가".repeat(24);

        SignupRequest rejected = request(
                "member@example.com",
                tooLongPassword,
                tooLongPassword,
                "타끈_01"
        );

        assertThat(invalidFields(rejected)).contains("password");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "가",
            "abcdefghijklmnopqrstu",
            "닉 네임",
            "닉!네임",
            "ADMIN",
            "관리자"
    })
    void 기존_닉네임_정책을_위반하면_실패한다(String nickname) {
        SignupRequest request = request(
                "member@example.com",
                PASSWORD,
                PASSWORD,
                nickname
        );

        assertThat(invalidFields(request)).contains("nickname");
    }

    @Test
    void 약관_목록이_없거나_비어_있으면_실패한다() {
        assertThat(invalidFields(requestWithAgreements(null)))
                .contains("agreements");

        assertThat(invalidFields(requestWithAgreements(List.of())))
                .contains("agreements");
    }

    @Test
    void 약관_항목_내부의_필수값도_검증한다() {
        SignupRequest request = requestWithAgreements(
                List.of(new AgreementRequest(null, "", null))
        );

        assertThat(invalidFields(request)).contains(
                "agreements[0].termsCode",
                "agreements[0].version",
                "agreements[0].agreed"
        );
    }

    @Test
    void 약관_목록에_null_항목이_있으면_실패한다() {
        SignupRequest request = requestWithAgreements(
                Collections.singletonList(null)
        );
        assertThat(invalidFields(request))
                .anyMatch(field -> field.startsWith("agreements[0]"));
    }

    @ParameterizedTest
    @CsvSource({
            "dev-v1, false, REQUIRED_AGREEMENT_NOT_ACCEPTED",
            "dev-v0, true, TERMS_VERSION_MISMATCH"
    })
    void 미동의와_이전_버전은_약관_정책에서_거절한다(
            String version,
            boolean agreed,
            SignupTermsException.Code expectedCode
    ) {
        SignupRequest request = requestWithAgreements(
                List.of(
                        new AgreementRequest(
                                SERVICE_TERMS,
                                version,
                                agreed
                        ),
                        new AgreementRequest(
                                PRIVACY_COLLECTION_USE,
                                "dev-v1",
                                true
                        )
                )
        );

        // 필수 필드가 없고 형식이 맞으므로 DTO 검증은 통과
        assertThat(invalidFields(request)).isEmpty();

        // 실제 가입 가능 여부는 기존 약관 정책이 판단
        SignupTermsPolicy policy = new SignupTermsPolicy();

        assertThatThrownBy(
                () -> policy.validateAndResolve(
                        request.toSignupAgreements()
                )
        ).isInstanceOfSatisfying(
                SignupTermsException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(expectedCode)
        );
    }

    @Test
    void toString에_비밀번호가_포함되지_않는다() {
        SignupRequest request = request(
                "member@example.com",
                "FirstSecret123!",
                "OtherSecret456!",
                "타끈_01"
        );

        assertThat(request.toString()).doesNotContain(
                "FirstSecret123!",
                "OtherSecret456!"
        );
    }

    private SignupRequest request(
            String email,
            String password,
            String passwordConfirm,
            String nickname
    ) {
        return new SignupRequest(
                email,
                password,
                passwordConfirm,
                nickname,
                validAgreements()
        );
    }

    private List<AgreementRequest> validAgreements() {
        return List.of(
                new AgreementRequest(
                        SERVICE_TERMS,
                        "dev-v1",
                        true
                ),
                new AgreementRequest(
                        PRIVACY_COLLECTION_USE,
                        "dev-v1",
                        true
                )
        );
    }

    private Set<String> invalidFields(SignupRequest request) {
        return validator.validate(request).stream()
                .map(violation ->
                        violation.getPropertyPath().toString()
                )
                .collect(Collectors.toSet());
    }

    private SignupRequest requestWithAgreements(
            List<AgreementRequest> agreements
    ) {
        return new SignupRequest(
                "member@example.com",
                PASSWORD,
                PASSWORD,
                "타끈_01",
                agreements
        );
    }
}
