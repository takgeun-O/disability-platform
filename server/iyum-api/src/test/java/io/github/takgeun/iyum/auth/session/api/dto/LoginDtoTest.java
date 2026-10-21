package io.github.takgeun.iyum.auth.session.api.dto;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로그인 요청의 입력 규칙과 성공 응답 형식 검증
 */
class LoginDtoTest {

    private static final String EMAIL = "member@example.com";
    private static final String PASSWORD = "Example123!";

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory =
                Validation.buildDefaultValidatorFactory();

        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void 이메일을_정리하고_비밀번호_원문을_유지한다() {
        String originalPassword = " Example123! ";

        LoginRequest request = new LoginRequest(
                " Member@Example.COM ",
                originalPassword
        );

        assertThat(invalidFields(request)).isEmpty();
        assertThat(request.email()).isEqualTo(EMAIL);
        assertThat(request.password())
                .isEqualTo(originalPassword);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "not-an-email",
            "member@"
    })
    void 이메일이_없거나_형식이_잘못되면_실패한다(
            String email
    ) {
        LoginRequest request =
                new LoginRequest(email, PASSWORD);

        assertThat(invalidFields(request))
                .contains("email");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "\t"
    })
    void 비밀번호가_없으면_실패한다(String password) {
        LoginRequest request =
                new LoginRequest(EMAIL, password);

        assertThat(invalidFields(request))
                .containsExactly("password");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "a",
            "12345678",
            "abcdefgh"
    })
    void 가입용_비밀번호_조건을_로그인_DTO에서_재검사하지_않는다(
            String password
    ) {
        LoginRequest request =
                new LoginRequest(EMAIL, password);

        assertThat(invalidFields(request)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("passwordByteLengthCases")
    void 비밀번호의_UTF8_바이트_상한을_검사한다(
            String password,
            boolean expectedValid
    ) {
        LoginRequest request =
                new LoginRequest(EMAIL, password);

        if (expectedValid) {
            assertThat(invalidFields(request)).isEmpty();
        } else {
            assertThat(invalidFields(request))
                    .containsExactly("password");
        }
    }

    static Stream<Arguments> passwordByteLengthCases() {
        return Stream.of(
                Arguments.of(
                        "a".repeat(72),
                        true
                ),
                Arguments.of(
                        "a".repeat(73),
                        false
                ),
                Arguments.of(
                        "가".repeat(24),
                        true
                ),
                Arguments.of(
                        "가".repeat(24) + "a",
                        false
                ),
                Arguments.of(
                        "😀".repeat(18),
                        true
                ),
                Arguments.of(
                        "😀".repeat(18) + "a",
                        false
                )
        );
    }

    @Test
    void toString에_로그인_입력값을_포함하지_않는다() {
        LoginRequest request =
                new LoginRequest(EMAIL, PASSWORD);

        assertThat(request.toString())
                .doesNotContain(EMAIL, PASSWORD);
    }

    @Test
    void 로그인_응답에는_공개할_회원_정보만_포함한다() {
        String passwordHash = "test-only-password-hash";

        MemberPrincipal principal = new MemberPrincipal(
                1L,
                EMAIL,
                passwordHash,
                "타끈",
                MemberStatus.ACTIVE,
                MemberRole.USER,
                0L
        );

        LoginResponse response =
                LoginResponse.from(principal);

        JsonMapper objectMapper =
                JsonMapper.builder().build();

        String json =
                objectMapper.writeValueAsString(response);

        var body = objectMapper.readTree(json);

        assertThat(body.size()).isEqualTo(4);

        assertThat(body.get("memberId").asLong())
                .isEqualTo(1L);

        assertThat(body.get("email").asString())
                .isEqualTo(EMAIL);

        assertThat(body.get("nickname").asString())
                .isEqualTo("타끈");

        assertThat(body.get("role").asString())
                .isEqualTo("USER");

        assertThat(json).doesNotContain(
                passwordHash,
                "password",
                "credentials",
                "authorities"
        );
    }

    private Set<String> invalidFields(LoginRequest request) {
        return validator.validate(request).stream()
                .map(violation ->
                        violation.getPropertyPath().toString()
                )
                .collect(Collectors.toSet());
    }
}