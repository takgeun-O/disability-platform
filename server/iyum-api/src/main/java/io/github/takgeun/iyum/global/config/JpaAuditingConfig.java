package io.github.takgeun.iyum.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Instant;
import java.util.Optional;

@Configuration
// JPA Auditing을 활성화하면서 생성일시나 수정일시에 사용할 시간을 제공하는 Bean을 지정하는 설정
// 시간을 가져올 떄 "auditingDateTimeProvider" 이름의 Spring Bean을 사용하겠다는 것.
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        // 시간을 요청 받을 때마다 현재 시각을 Instant로 반환하라.
        // 즉, 설정 클래스가 만들어진 시각으로 고정되는 게 아니라 요청할 때마다 Instant.now()가 실행
        // 이걸 따로 둔 이유는 테스트에서 시간을 고정하여 수정 시각이 바뀌는지 정확히 확인하기 위함
        /**
         * 회원 엔티티 저장 상황을 예시로 들면
         * 1. Auditing 리스너가 엔티티의 저장 이벤트를 감지한다.
         * 2. 지정된 auditingDateTimeProvider에서 현재 시각을 가져온다.
         * 3. 그 시간을 @CreatedDate, @LastModifiedDate 필드에 기록한다.
         */
        return () -> Optional.of(Instant.now());
    }
}
