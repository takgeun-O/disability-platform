package io.github.takgeun.iyum.global.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class QuerydslConfig {

    // JPAQueryFactory는 QueryDSL 조회를 시작하는 객체.
    // QueryDSL JPA로 작성한 조건은 JPQL로 변환되고, Hibernate가 실제 SQL을 실행하는 구조
    @Bean
    public JPAQueryFactory jpaQueryFactory(
            EntityManager entityManager
    ) {
        return new JPAQueryFactory(entityManager);
    }
}
