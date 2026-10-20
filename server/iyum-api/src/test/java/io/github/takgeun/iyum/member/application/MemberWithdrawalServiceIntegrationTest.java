package io.github.takgeun.iyum.member.application;

import io.github.takgeun.iyum.community.comment.application.CommentReadService;
import io.github.takgeun.iyum.community.post.application.PostReadService;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Testcontainers
class MemberWithdrawalServiceIntegrationTest {

    private static final String RAW_PASSWORD = "Password123!";

    private static final Instant WITHDRAWN_TIME =
            Instant.parse("2026-10-20T09:00:00Z");

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    MemberWithdrawalService withdrawalService;

    @MockitoSpyBean
    MemberRepository memberRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PostReadService postReadService;

    @Autowired
    CommentReadService commentReadService;

    @MockitoBean
    Clock clock;

    @PersistenceContext
    EntityManager entityManager;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(WITHDRAWN_TIME);

        // 테스트 컨테이너 DB만 정리한다.
        // 외래 키를 참조하는 자식 테이블부터 삭제한다.
        jdbcTemplate.update("DELETE FROM community_comments");
        jdbcTemplate.update("DELETE FROM community_posts");
        jdbcTemplate.update("DELETE FROM email_verification_tokens");
        jdbcTemplate.update("DELETE FROM member_agreements");
        jdbcTemplate.update("DELETE FROM members");
    }

    @Test
    void 정상_탈퇴하면_계정정보와_연관_인증데이터를_정리한다() {
        Long memberId = createFixture(
                "withdraw@example.com",
                "탈퇴대상",
                true
        );

        // 여기서 서비스 트랜잭션 종료
        withdrawalService.withdraw(memberId, RAW_PASSWORD);

        // 서비스 트랜잭션이 끝난 후 DB에서 새로 조회한다.
        Member found = memberRepository.findById(memberId)
                .orElseThrow();

        assertThat(found.getId()).isEqualTo(memberId);
        assertThat(found.getStatus()).isEqualTo(MemberStatus.DELETED);
        assertThat(found.getEmail()).isNull();
        assertThat(found.getPasswordHash()).isNull();
        assertThat(found.getNickname()).isNull();
        assertThat(found.getWithdrawnAt()).isEqualTo(WITHDRAWN_TIME);

        assertThat(tokenCount(memberId)).isZero();
        assertThat(agreementCount(memberId)).isZero();
    }

    @Test
    void 비밀번호가_틀리면_모든_데이터를_유지한다() {
        Long memberId = createFixture(
                "wrong-password@example.com",
                "비밀번호검증",
                true
        );
        var before = databaseSnapshot();

        assertThatThrownBy(() ->
                withdrawalService.withdraw(
                        memberId,
                        "WrongPassword123!"
                )
        )
                .isInstanceOfSatisfying(
                        MemberWithdrawalException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        MemberWithdrawalException.Code
                                                .PASSWORD_MISMATCH
                                )
                );

        assertThat(databaseSnapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void 비밀번호가_없으면_데이터를_변경하지_않는다(
            String password
    ) {
        Long memberId = createFixture(
                "empty-password@example.com",
                "빈비밀번호검증",
                true
        );
        var before = databaseSnapshot();

        assertThatThrownBy(() ->
                withdrawalService.withdraw(memberId, password)
        )
                .isInstanceOfSatisfying(
                        MemberWithdrawalException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        MemberWithdrawalException.Code
                                                .INVALID_PASSWORD
                                )
                );

        assertThat(databaseSnapshot()).isEqualTo(before);
    }

    @Test
    void 비밀번호가_UTF8_72바이트를_넘으면_거절한다() {
        Long memberId = createFixture(
                "long-password@example.com",
                "길이검증",
                true
        );
        var before = databaseSnapshot();

        // 한글 한 글자는 UTF-8에서 3바이트이므로 총 75바이트.
        String password = "가".repeat(25);

        assertThatThrownBy(() ->
                withdrawalService.withdraw(memberId, password)
        )
                .isInstanceOfSatisfying(
                        MemberWithdrawalException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        MemberWithdrawalException.Code
                                                .INVALID_PASSWORD
                                )
                );

        assertThat(databaseSnapshot()).isEqualTo(before);
    }

    @Test
    void 활성_회원이_아니면_탈퇴를_진행하지_않는다() {
        Long memberId = createFixture(
                "pending@example.com",
                "인증대기회원",
                false
        );
        var before = databaseSnapshot();

        assertThatThrownBy(() ->
                withdrawalService.withdraw(memberId, RAW_PASSWORD)
        )
                .isInstanceOfSatisfying(
                        MemberWithdrawalException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        MemberWithdrawalException.Code
                                                .MEMBER_NOT_ACTIVE
                                )
                );

        assertThat(databaseSnapshot()).isEqualTo(before);
    }

    @Test
    void 이미_탈퇴한_회원은_다시_탈퇴처리하지_않는다() {
        Long memberId = createFixture(
                "already-withdrawn@example.com",
                "반복탈퇴검증",
                true
        );
        withdrawalService.withdraw(memberId, RAW_PASSWORD);
        var before = databaseSnapshot();

        assertThatThrownBy(() ->
                withdrawalService.withdraw(memberId, RAW_PASSWORD)
        )
                .isInstanceOfSatisfying(
                        MemberWithdrawalException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(
                                        MemberWithdrawalException.Code
                                                .MEMBER_NOT_ACTIVE
                                )
                );

        assertThat(databaseSnapshot()).isEqualTo(before);
    }

    @Test
    void 다른_회원과_게시글_댓글은_그대로_유지한다() {
        Long targetId = createFixture(
                "target@example.com",
                "탈퇴회원",
                true
        );
        Long otherId = createFixture(
                "other@example.com",
                "유지회원",
                true
        );

        var otherMemberBefore = jdbcTemplate.queryForMap(
                "SELECT * FROM members WHERE id = ?",
                otherId
        );
        var otherTokensBefore = jdbcTemplate.queryForList(
                """
                SELECT * FROM email_verification_tokens
                WHERE member_id = ?
                ORDER BY id
                """,
                otherId
        );
        var otherAgreementsBefore = jdbcTemplate.queryForList(
                """
                SELECT * FROM member_agreements
                WHERE member_id = ?
                ORDER BY id
                """,
                otherId
        );
        var postsBefore = jdbcTemplate.queryForList(
                "SELECT * FROM community_posts ORDER BY id"
        );
        var commentsBefore = jdbcTemplate.queryForList(
                "SELECT * FROM community_comments ORDER BY id"
        );

        withdrawalService.withdraw(targetId, RAW_PASSWORD);

        assertThat(jdbcTemplate.queryForMap(
                "SELECT * FROM members WHERE id = ?",
                otherId
        )).isEqualTo(otherMemberBefore);

        assertThat(jdbcTemplate.queryForList(
                """
                SELECT * FROM email_verification_tokens
                WHERE member_id = ?
                ORDER BY id
                """,
                otherId
        )).isEqualTo(otherTokensBefore);

        assertThat(jdbcTemplate.queryForList(
                """
                SELECT * FROM member_agreements
                WHERE member_id = ?
                ORDER BY id
                """,
                otherId
        )).isEqualTo(otherAgreementsBefore);

        // 내용·작성자 ID·삭제 시각 등 전체 컬럼이 유지되는지 확인.
        assertThat(jdbcTemplate.queryForList(
                "SELECT * FROM community_posts ORDER BY id"
        )).isEqualTo(postsBefore);

        assertThat(jdbcTemplate.queryForList(
                "SELECT * FROM community_comments ORDER BY id"
        )).isEqualTo(commentsBefore);
    }

    @Test
    void DB_변경_후_실패하면_삭제한_데이터까지_모두_롤백된다() {
        // 테스트용 회원과 관련 데이터 저장
        Long memberId = createFixture(
                "rollback@example.com",
                "롤백검증",
                true
        );
        // 탈퇴 전 DB상태 복사
        var before = databaseSnapshot();

        doAnswer(invocation -> {
            // @PersistenceContext로 주입받은 EntityManager는 현재 트랜잭션에 연결된 실제 영속성 컨텍스트로
            // 작업을 전달한다. 람다가 실행되는 시점에는 탈퇴 서비스의 트랜잭션이 진행 중이므로,
            // 서비스에서 변경한 회원을 DB에 반영할 수 있다.
            entityManager.flush();

            // 같은 트랜잭션 안에서는 변경이 적용된 상태여야 한다.
            assertThat(tokenCount(memberId)).isZero();
            assertThat(agreementCount(memberId)).isZero();

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM members WHERE id = ?",
                    String.class,
                    memberId
            )).isEqualTo("DELETED");

            // SQL 실행 후, 커밋 전에 일부러 실패시킨다.
            throw new IllegalStateException("테스트용 저장 실패");
        }).when(memberRepository).flush();

        assertThatThrownBy(() ->
                // 아래 과정이 서비스 트랜잭션 안에서 모두 처리
                    // 실제 탈퇴 서비스 호출
                    // 회원 잠금 조회와 상태·비밀번호 확인
                    // 인증 토큰 삭제
                    // 약관 동의 기록 삭제
                    // member.withdraw(...)로 회원 엔티티 변경
                    // memberRepository.flush() 호출
                withdrawalService.withdraw(memberId, RAW_PASSWORD)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("테스트용 저장 실패");

        // 서비스 트랜잭션이 롤백된 후 다시 읽는다.
        assertThat(databaseSnapshot()).isEqualTo(before);
    }

    @Test
    void 탈퇴한_회원의_게시글은_표시이름을_변경하여_조회한다() {
        Long memberId = createFixture(
                "post-author@example.com",
                "기존작성자",
                true
        );

        Long postId = jdbcTemplate.queryForObject("""
            SELECT id
            FROM community_posts
            WHERE author_id = ?
            """,
                Long.class,
                memberId
        );

        withdrawalService.withdraw(memberId, RAW_PASSWORD);

        // 게시글 상세
        var detail = postReadService.getPost(postId);

        assertThat(detail.author().memberId()).isEqualTo(memberId);
        assertThat(detail.author().nickname()).isEqualTo("탈퇴한 회원");
        assertThat(detail.title()).isEqualTo("유지할 게시글");
        assertThat(detail.content()).isEqualTo("탈퇴 후에도 유지할 본문");

        // 기존 페이지 방식 목록
        var page = postReadService.getPosts(null, null, 0, 10);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().author().nickname())
                .isEqualTo("탈퇴한 회원");
        assertThat(page.getContent().getFirst().author().memberId())
                .isEqualTo(memberId);

        // 커서 방식 피드
        var feed = postReadService.getFeed(
                null, null, null, null, 10
        );

        assertThat(feed.items()).hasSize(1);

        var item = feed.items().getFirst();

        assertThat(item.postId()).isEqualTo(postId);
        assertThat(item.author().memberId()).isEqualTo(memberId);
        assertThat(item.author().nickname()).isEqualTo("탈퇴한 회원");

        // 탈퇴 때문에 기존 댓글이 집계에서 빠지면 안 된다.
        assertThat(item.commentCount()).isEqualTo(1L);
    }

    @Test
    void 탈퇴한_회원의_댓글은_표시이름을_변경하여_조회한다() {
        Long memberId = createFixture(
                "comment-author@example.com",
                "기존댓글작성자",
                true
        );

        Long postId = jdbcTemplate.queryForObject("""
            SELECT id
            FROM community_posts
            WHERE author_id = ?
            """,
                Long.class,
                memberId
        );

        withdrawalService.withdraw(memberId, RAW_PASSWORD);

        var result = commentReadService.getComments(
                postId, null, null, 10
        );

        assertThat(result.items()).hasSize(1);

        var comment = result.items().getFirst();

        assertThat(comment.author().memberId()).isEqualTo(memberId);
        assertThat(comment.author().nickname()).isEqualTo("탈퇴한 회원");
        assertThat(comment.content()).isEqualTo("탈퇴 후에도 유지할 댓글");

        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void 활성_회원의_작성자_닉네임은_그대로_표시한다() {
        Long memberId = createFixture(
                "active-author@example.com",
                "정상작성자",
                true
        );

        Long postId = jdbcTemplate.queryForObject("""
            SELECT id
            FROM community_posts
            WHERE author_id = ?
            """,
                Long.class,
                memberId
        );

        var detail = postReadService.getPost(postId);
        var comments = commentReadService.getComments(
                postId, null, null, 10
        );

        assertThat(detail.author().nickname()).isEqualTo("정상작성자");

        assertThat(comments.items()).hasSize(1);
        assertThat(comments.items().getFirst().author().nickname())
                .isEqualTo("정상작성자");
    }

    private Long createFixture(
            String email,
            String nickname,
            boolean active
    ) {
        Member member = Member.createPending(
                email,
                passwordEncoder.encode(RAW_PASSWORD),
                nickname
        );

        if (active) {
            member.activeAfterEmailVerification();
        }

        Long memberId = memberRepository.saveAndFlush(member).getId();

        jdbcTemplate.update("""
                INSERT INTO member_agreements
                    (member_id, terms_code, terms_version, agreed_at)
                VALUES
                    (?, 'SERVICE_TERMS', 'test-v1', CURRENT_TIMESTAMP),
                    (?, 'PRIVACY_COLLECTION_USE', 'test-v1',
                     CURRENT_TIMESTAMP)
                """, memberId, memberId);

        // 사용된 토큰과 만료된 토큰도 모두 정리되는지 확인한다.
        jdbcTemplate.update("""
                INSERT INTO email_verification_tokens
                    (member_id, token_hash, created_at, expires_at,
                     used_at, version)
                VALUES
                    (?, ?, CURRENT_TIMESTAMP - INTERVAL '2 hours',
                     CURRENT_TIMESTAMP + INTERVAL '1 hour',
                     CURRENT_TIMESTAMP, 0),
                    (?, ?, CURRENT_TIMESTAMP - INTERVAL '2 hours',
                     CURRENT_TIMESTAMP - INTERVAL '1 hour',
                     NULL, 0)
                """,
                memberId, tokenHash(),
                memberId, tokenHash()
        );

        Long postId = jdbcTemplate.queryForObject("""
                INSERT INTO community_posts
                    (author_id, post_type, topic, title, content,
                     created_at, updated_at)
                VALUES
                    (?, 'GENERAL', 'DAILY', '유지할 게시글',
                     '탈퇴 후에도 유지할 본문',
                     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """,
                Long.class,
                memberId
        );

        jdbcTemplate.update("""
                INSERT INTO community_comments
                    (post_id, author_id, content, created_at, updated_at)
                VALUES
                    (?, ?, '탈퇴 후에도 유지할 댓글',
                     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                postId, memberId
        );

        return memberId;
    }

    private String tokenHash() {
        // DB 형식에 맞춘 테스트 전용 64자리 문자열.
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .repeat(2);
    }

    private long tokenCount(Long memberId) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM email_verification_tokens
                WHERE member_id = ?
                """,
                Long.class,
                memberId
        );
    }

    private long agreementCount(Long memberId) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM member_agreements
                WHERE member_id = ?
                """,
                Long.class,
                memberId
        );
    }

    private Map<String, List<Map<String, Object>>> databaseSnapshot() {
        Map<String, List<Map<String, Object>>> snapshot =
                new LinkedHashMap<>();

        // 테이블명은 테스트 코드에 고정된 값만 사용한다.
        for (String table : List.of(
                "members",
                "member_agreements",
                "email_verification_tokens",
                "community_posts",
                "community_comments"
        )) {
            snapshot.put(
                    table,
                    jdbcTemplate.queryForList(
                            "SELECT * FROM " + table + " ORDER BY id"
                    )
            );
        }

        return snapshot;
    }
}