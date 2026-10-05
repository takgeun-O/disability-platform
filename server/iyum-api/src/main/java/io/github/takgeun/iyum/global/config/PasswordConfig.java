package io.github.takgeun.iyum.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

// 이 설정 클래스의 @Bean 메서드 사이에 직접 호출하는 코드가 없으므로,
// 그 호출을 관리하기 위한 프록시를 만들지 않도록 설정

/**
 * 어차피 이 클래스에서는 Bean을 생성하는 클래스가 하나만 있으므로 다른 코드에서 PasswordConfig의 passwordEncoder()를
 * 직접 호출하지 않고 필요한 곳에서 PasswordEncoder를 주입받아 사용한다면 굳이 호출을 가로챌 필요가 없음.
 * 그래서 false로 설정해서 불필요한 CGLIB 클래스 생성·처리 비용을 줄이는 것. (다만 성능 향상을 꽤하기보다는 필요 없는 처리를 생략한다는 의미임을 표현하는 것에 목적을 둬야 함. 성능 향상은 미미하므로)
 * 즉, PasswordConfig에서는 빈을 등록하고, 사용하는 쪽에서는 주입받는 구조이므로 false로 두어도 정상 동작한다.
 *
 * 헷갈리기 쉬운 포인트
 * -> false는 빈의 싱글톤 관리를 끄는 것이 아니라 @Bean 메서드 직접 호출에 대한 개입을 끄는 설정임.
 */
@Configuration(proxyBeanMethods = false)
public class PasswordConfig {


    @Bean   // 메서드가 반환한 객체를 Spring이 관리하도록 등록 -> 이후 회원가입 서비스에서 PasswordEncoder를 생성자 주입으로 받아 사용할 것
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories
                // 새 비밀번호를 BCrypt로 해시하고, 저장값에 알고리즘 식별자를 붙임
                // 저장되는 문자열 구조 : {bcrypt}$2a$10$...
                // 이 전체 문자열을 Member.passwordHash에 저장한다.
                // encode(원문비밀번호) : 회원가입 시 저장할 해시 생성
                // matches(입력비밀번호, 저장된해시) : 로그인 시 일치 여부 확인
                .createDelegatingPasswordEncoder();
    }
}
