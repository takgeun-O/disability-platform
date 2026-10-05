package io.github.takgeun.iyum.global.config;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

// PasswordConfig를 설정으로 사용하는 작은 Spring 컨테이너를 만들고
// 테스트에 빈을 주입해준다. -> DB연결이나 웹 서버 설정이 없으므로 Docker나 개발 서버가 필요하지 않음.
@SpringJUnitConfig(PasswordConfig.class)
public class PasswordConfigTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void BCrypt_저장값을_생성하고_비밀번호를_검증한다() {
        // given
        String rawPassword = "Pass1234!";

        // when
        String passwordHash = passwordEncoder.encode(rawPassword);

        // then
        assertThat(passwordHash)
                .startsWith("{bcrypt}")
                .isNotEqualTo(rawPassword);

        // 현재 password_hash 컬럼 길이 안에 저장할 수 있어야 합니다.
        assertThat(passwordHash.length()).isLessThanOrEqualTo(255);

        assertThat(
                passwordEncoder.matches(rawPassword, passwordHash)
        ).isTrue();

        assertThat(
                passwordEncoder.matches("Wrong1234!", passwordHash)
        ).isFalse();
    }

    @Test
    void 같은_비밀번호도_서로_다른_해시를_생성한다() {

        // given
        String rawPassword = "Pass1234!";

        // when
        String firstHash = passwordEncoder.encode(rawPassword);
        String secondHash = passwordEncoder.encode(rawPassword);

        // then
        assertThat(firstHash).isNotEqualTo(secondHash);

        assertThat(
                passwordEncoder.matches(rawPassword, firstHash)
        ).isTrue();

        assertThat(
                passwordEncoder.matches(rawPassword, secondHash)
        ).isTrue();
    }

    @Test
    void UTF8_72바이트_비밀번호를_해시하고_검증한다() {
        // 영문·숫자 3바이트 + 한글 23자 × 3바이트 = 72바이트
        String rawPassword = "Ab1" + "가".repeat(23);

        assertThat(
                rawPassword.getBytes(StandardCharsets.UTF_8).length
        ).isEqualTo(72);

        // when
        String passwordHash = passwordEncoder.encode(rawPassword);

        // then
        assertThat(
                passwordEncoder.matches(rawPassword, passwordHash)
        ).isTrue();
    }

    @Test
    void UTF8_72바이트를_초과하면_해시_생성을_거절한다() {
        // 72바이트 비밀번호에 영문 1글자를 추가하여 73바이트
        String rawPassword = "Ab1" + "가".repeat(23) + "a";

        assertThat(
                rawPassword.getBytes(StandardCharsets.UTF_8).length
        ).isEqualTo(73);

        assertThatThrownBy(
                () -> passwordEncoder.encode(rawPassword)
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
