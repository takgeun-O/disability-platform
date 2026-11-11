package io.github.takgeun.iyum.admin.member.infrastructure;

import io.github.takgeun.iyum.admin.member.domain.MemberModerationAction;
import io.github.takgeun.iyum.admin.member.domain.MemberModerationLog;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true"
})
@Testcontainers
@Transactional
@Execution(ExecutionMode.SAME_THREAD)
class MemberModerationLogReadRepositoryTest {

    private static final Instant BASE_TIME =
            Instant.parse("2026-11-12T00:00:00Z");

    private static final Sort LATEST_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
    );

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                POSTGRES::getJdbcUrl
        );
        registry.add(
                "spring.datasource.username",
                POSTGRES::getUsername
        );
        registry.add(
                "spring.datasource.password",
                POSTGRES::getPassword
        );
    }

    @Autowired
    MemberModerationLogRepository logRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Long actorId;
    private Long targetId;
    private Long otherTargetId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);

        String passwordHash = passwordEncoder.encode(
                "ModerationRead123!"
        );

        Member actor = saveActiveMember(
                "log-admin-" + suffix + "@example.com",
                "관리" + suffix,
                passwordHash
        );

        Member target = saveActiveMember(
                "log-target-" + suffix + "@example.com",
                "대상" + suffix,
                passwordHash
        );

        Member otherTarget = saveActiveMember(
                "log-other-" + suffix + "@example.com",
                "다른" + suffix,
                passwordHash
        );

        actorId = actor.getId();
        targetId = target.getId();
        otherTargetId = otherTarget.getId();

        // 테스트 데이터의 처리 관리자 역할을 설정한다.
        int updatedRows = jdbcTemplate.update("""
                UPDATE members
                   SET role = 'ADMIN'
                 WHERE id = ?
                """,
                actorId
        );

        assertThat(updatedRows).isEqualTo(1);
    }

    @Test
    void 대상_회원의_이력만_최신순으로_페이징하고_전체_개수를_계산한다() {
        MemberModerationLog first = saveLog(
                targetId,
                MemberModerationAction.SUSPEND,
                BASE_TIME
        );

        MemberModerationLog second = saveLog(
                targetId,
                MemberModerationAction.LIFT_SUSPENSION,
                BASE_TIME.plusSeconds(1)
        );

        MemberModerationLog third = saveLog(
                targetId,
                MemberModerationAction.SUSPEND,
                BASE_TIME.plusSeconds(2)
        );

        MemberModerationLog fourth = saveLog(
                targetId,
                MemberModerationAction.LIFT_SUSPENSION,
                BASE_TIME.plusSeconds(3)
        );

        MemberModerationLog fifth = saveLog(
                targetId,
                MemberModerationAction.SUSPEND,
                BASE_TIME.plusSeconds(4)
        );

        // 더 최신이어도 다른 회원의 이력은 결과와 전체 개수에서 제외한다.
        saveLog(
                otherTargetId,
                MemberModerationAction.SUSPEND,
                BASE_TIME.plusSeconds(100)
        );

        Page<MemberModerationLog> firstPage = readPage(
                targetId,
                0,
                2
        );

        assertThat(firstPage.getContent())
                .extracting(MemberModerationLog::getId)
                .containsExactly(
                        fifth.getId(),
                        fourth.getId()
                );

        assertThat(firstPage.getNumber()).isZero();
        assertThat(firstPage.getSize()).isEqualTo(2);
        assertThat(firstPage.getNumberOfElements()).isEqualTo(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(5L);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.hasPrevious()).isFalse();

        Page<MemberModerationLog> secondPage = readPage(
                targetId,
                1,
                2
        );

        assertThat(secondPage.getContent())
                .extracting(MemberModerationLog::getId)
                .containsExactly(
                        third.getId(),
                        second.getId()
                );

        assertThat(secondPage.getTotalElements()).isEqualTo(5L);
        assertThat(secondPage.getTotalPages()).isEqualTo(3);
        assertThat(secondPage.hasNext()).isTrue();
        assertThat(secondPage.hasPrevious()).isTrue();

        Page<MemberModerationLog> lastPage = readPage(
                targetId,
                2,
                2
        );

        assertThat(lastPage.getContent())
                .extracting(MemberModerationLog::getId)
                .containsExactly(first.getId());

        assertThat(lastPage.getNumberOfElements()).isEqualTo(1);
        assertThat(lastPage.getTotalElements()).isEqualTo(5L);
        assertThat(lastPage.getTotalPages()).isEqualTo(3);
        assertThat(lastPage.hasNext()).isFalse();
        assertThat(lastPage.hasPrevious()).isTrue();
    }

    @Test
    void 처리_시각이_같으면_ID_내림차순으로_페이지_경계를_결정한다() {
        MemberModerationLog first = saveLog(
                targetId,
                MemberModerationAction.SUSPEND,
                BASE_TIME
        );

        MemberModerationLog second = saveLog(
                targetId,
                MemberModerationAction.LIFT_SUSPENSION,
                BASE_TIME
        );

        MemberModerationLog third = saveLog(
                targetId,
                MemberModerationAction.SUSPEND,
                BASE_TIME
        );

        assertThat(first.getId()).isLessThan(second.getId());
        assertThat(second.getId()).isLessThan(third.getId());

        Page<MemberModerationLog> firstPage = readPage(
                targetId,
                0,
                2
        );

        Page<MemberModerationLog> secondPage = readPage(
                targetId,
                1,
                2
        );

        assertThat(firstPage.getContent())
                .extracting(MemberModerationLog::getId)
                .containsExactly(
                        third.getId(),
                        second.getId()
                );

        assertThat(secondPage.getContent())
                .extracting(MemberModerationLog::getId)
                .containsExactly(first.getId());

        assertThat(firstPage.getTotalElements()).isEqualTo(3L);
        assertThat(secondPage.getTotalElements()).isEqualTo(3L);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(secondPage.getTotalPages()).isEqualTo(2);
    }

    @Test
    void 이력이_없는_회원은_빈_페이지를_반환한다() {
        // 다른 회원의 이력이 있어도 조회 대상 회원의 결과는 비어 있다.
        saveLog(
                otherTargetId,
                MemberModerationAction.SUSPEND,
                BASE_TIME
        );

        Page<MemberModerationLog> result = readPage(
                targetId,
                0,
                20
        );

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getNumber()).isZero();
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getTotalPages()).isZero();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.hasPrevious()).isFalse();
    }

    @Test
    void 범위를_벗어난_페이지도_전체_개수와_페이지_수를_유지한다() {
        saveLog(
                targetId,
                MemberModerationAction.SUSPEND,
                BASE_TIME
        );

        saveLog(
                targetId,
                MemberModerationAction.LIFT_SUSPENSION,
                BASE_TIME.plusSeconds(1)
        );

        saveLog(
                targetId,
                MemberModerationAction.SUSPEND,
                BASE_TIME.plusSeconds(2)
        );

        // 전체 결과는 2페이지지만 6번째 페이지를 요청한다.
        Page<MemberModerationLog> result = readPage(
                targetId,
                5,
                2
        );

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getNumber()).isEqualTo(5);
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getTotalElements()).isEqualTo(3L);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.hasPrevious()).isTrue();
    }

    private Page<MemberModerationLog> readPage(
            Long memberId,
            int page,
            int size
    ) {
        return logRepository.findByTargetMemberId(
                memberId,
                PageRequest.of(page, size, LATEST_SORT)
        );
    }

    private MemberModerationLog saveLog(
            Long memberId,
            MemberModerationAction action,
            Instant createdAt
    ) {
        return logRepository.saveAndFlush(
                MemberModerationLog.create(
                        actorId,
                        memberId,
                        action,
                        "이력 조회 검증",
                        createdAt
                )
        );
    }

    private Member saveActiveMember(
            String email,
            String nickname,
            String passwordHash
    ) {
        Member member = Member.createPending(
                email,
                passwordHash,
                nickname
        );

        member.activeAfterEmailVerification();

        return memberRepository.saveAndFlush(member);
    }
}