package io.github.takgeun.iyum.admin.operation.infrastructure;

import io.github.takgeun.iyum.admin.moderation.domain.ContentModerationLog;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.operation.application.AdminOperationLogReadQuery;
import io.github.takgeun.iyum.admin.operation.application.AdminOperationLogTargetType;
import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.member.domain.Member;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true"
})
@Testcontainers
@Transactional
class AdminOperationLogReadRepositoryTest {

    private static final Instant NOW =
            Instant.parse("2026-11-04T03:00:00Z");

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
    AdminOperationLogReadRepository repository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean(name = "auditingDateTimeProvider")
    DateTimeProvider dateTimeProvider;

    private Long actorId;
    private Long postId;
    private Long commentId;

    @BeforeEach
    void setUp() {
        when(dateTimeProvider.getNow())
                .thenReturn(Optional.of(NOW));

        Member actor = Member.createPending(
                "operation-log-read@example.com",
                "test-only-password-hash",
                "기록조회회원"
        );
        actor.activeAfterEmailVerification();

        entityManager.persist(actor);
        entityManager.flush();
        actorId = actor.getId();

        jdbcTemplate.update(
                "UPDATE members SET role = 'ADMIN' WHERE id = ?",
                actorId
        );

        // JDBC로 변경한 역할을 다시 읽는다.
        entityManager.clear();
        Member author = entityManager.find(Member.class, actorId);

        Post post = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                "운영 기록 조회 대상",
                "게시글 내용"
        );
        entityManager.persist(post);

        Comment comment = Comment.create(
                post,
                author,
                "댓글 내용"
        );
        entityManager.persist(comment);
        entityManager.flush();

        postId = post.getId();
        commentId = comment.getId();
    }

    @ParameterizedTest
    @EnumSource(AdminOperationLogTargetType.class)
    void 대상_유형별로_조회하고_전체_개수에도_반영한다(
            AdminOperationLogTargetType targetType
    ) {
        Long postLog = savePostLog(ModerationAction.HIDE, NOW);
        Long commentLog = saveCommentLog(ModerationAction.HIDE, NOW);
        flushAndClear();

        Page<ContentModerationLog> result = repository.findLogs(
                new AdminOperationLogReadQuery(
                        targetType, null, null, null, 0, 20
                )
        );

        List<Long> expected = switch (targetType) {
            case ALL -> List.of(commentLog, postLog);
            case POST -> List.of(postLog);
            case COMMENT -> List.of(commentLog);
        };

        assertThat(result.getContent())
                .extracting(ContentModerationLog::getId)
                .containsExactlyElementsOf(expected);

        assertThat(result.getTotalElements())
                .isEqualTo(expected.size());
    }

    @ParameterizedTest
    @EnumSource(ModerationAction.class)
    void 조치_유형별로_조회한다(ModerationAction action) {
        Long hidden = savePostLog(ModerationAction.HIDE, NOW);
        Long restored = savePostLog(ModerationAction.RESTORE, NOW);
        flushAndClear();

        Page<ContentModerationLog> result = repository.findLogs(
                new AdminOperationLogReadQuery(
                        null, action, null, null, 0, 20
                )
        );

        Long expected = action == ModerationAction.HIDE
                ? hidden
                : restored;

        assertThat(result.getContent())
                .extracting(ContentModerationLog::getId)
                .containsExactly(expected);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void 처리시각_내림차순이며_같은_시각이면_ID_내림차순이다() {
        Long newerFirst = savePostLog(
                ModerationAction.HIDE,
                NOW.plusSeconds(60)
        );
        Long newerSecond = saveCommentLog(
                ModerationAction.HIDE,
                NOW.plusSeconds(60)
        );

        // ID가 더 커도 시각이 오래됐으면 마지막에 나와야 한다.
        Long older = savePostLog(ModerationAction.RESTORE, NOW);
        flushAndClear();

        Page<ContentModerationLog> result = readPage(0, 20);

        assertThat(result.getContent())
                .extracting(ContentModerationLog::getId)
                .containsExactly(newerSecond, newerFirst, older);
    }

    @Test
    void 한국_날짜_검색은_시작을_포함하고_다음날_시작을_제외한다() {
        Instant start = Instant.parse("2026-11-03T15:00:00Z");
        Instant end = Instant.parse("2026-11-04T15:00:00Z");

        // PostgreSQL의 마이크로초 정밀도에 맞춘 경계 데이터.
        savePostLog(ModerationAction.HIDE, start.minusNanos(1_000));
        Long atStart = savePostLog(ModerationAction.HIDE, start);
        Long beforeEnd = savePostLog(
                ModerationAction.HIDE,
                end.minusNanos(1_000)
        );
        savePostLog(ModerationAction.HIDE, end);
        flushAndClear();

        LocalDate date = LocalDate.of(2026, 11, 4);

        Page<ContentModerationLog> result = repository.findLogs(
                new AdminOperationLogReadQuery(
                        null, null, date, date, 0, 20
                )
        );

        assertThat(result.getContent())
                .extracting(ContentModerationLog::getId)
                .containsExactly(beforeEnd, atStart);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 시작일이나_종료일_하나만으로도_검색한다(boolean startOnly) {
        Long previous = savePostLog(
                ModerationAction.HIDE,
                Instant.parse("2026-11-03T14:59:59Z")
        );
        Long inside = savePostLog(ModerationAction.HIDE, NOW);
        Long next = savePostLog(
                ModerationAction.HIDE,
                Instant.parse("2026-11-04T15:00:00Z")
        );
        flushAndClear();

        LocalDate date = LocalDate.of(2026, 11, 4);

        Page<ContentModerationLog> result = repository.findLogs(
                new AdminOperationLogReadQuery(
                        null,
                        null,
                        startOnly ? date : null,
                        startOnly ? null : date,
                        0,
                        20
                )
        );

        List<Long> expected = startOnly
                ? List.of(next, inside)
                : List.of(inside, previous);

        assertThat(result.getContent())
                .extracting(ContentModerationLog::getId)
                .containsExactlyElementsOf(expected);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void 대상과_조치와_기간을_모두_적용하고_페이지를_나눈다() {
        Long first = savePostLog(ModerationAction.HIDE, NOW);
        Long second = savePostLog(
                ModerationAction.HIDE,
                NOW.plusSeconds(1)
        );
        Long third = savePostLog(
                ModerationAction.HIDE,
                NOW.plusSeconds(2)
        );

        // 각 조건 중 하나씩 일치하지 않는 기록들.
        saveCommentLog(ModerationAction.HIDE, NOW);
        savePostLog(ModerationAction.RESTORE, NOW);
        savePostLog(ModerationAction.HIDE, NOW.minusSeconds(86_400));
        flushAndClear();

        LocalDate date = LocalDate.of(2026, 11, 4);

        Page<ContentModerationLog> firstPage = repository.findLogs(
                new AdminOperationLogReadQuery(
                        AdminOperationLogTargetType.POST,
                        ModerationAction.HIDE,
                        date, date, 0, 2
                )
        );
        Page<ContentModerationLog> lastPage = repository.findLogs(
                new AdminOperationLogReadQuery(
                        AdminOperationLogTargetType.POST,
                        ModerationAction.HIDE,
                        date, date, 1, 2
                )
        );

        assertThat(firstPage.getContent())
                .extracting(ContentModerationLog::getId)
                .containsExactly(third, second);
        assertThat(lastPage.getContent())
                .extracting(ContentModerationLog::getId)
                .containsExactly(first);

        for (Page<ContentModerationLog> page : List.of(firstPage, lastPage)) {
            assertThat(page.getTotalElements()).isEqualTo(3);
            assertThat(page.getTotalPages()).isEqualTo(2);
        }

        assertThat(firstPage.hasNext()).isTrue();
        assertThat(lastPage.hasNext()).isFalse();
    }

    @Test
    void 범위를_벗어난_페이지는_전체_개수를_유지한다() {
        savePostLog(ModerationAction.HIDE, NOW);
        flushAndClear();

        Page<ContentModerationLog> result = readPage(1, 1);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void 검색_결과가_없으면_빈_목록과_전체_개수_0을_반환한다() {
        savePostLog(ModerationAction.HIDE, NOW);
        flushAndClear();

        Page<ContentModerationLog> result = repository.findLogs(
                new AdminOperationLogReadQuery(
                        AdminOperationLogTargetType.COMMENT,
                        null, null, null, 0, 20
                )
        );

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getTotalPages()).isZero();
    }

    @Test
    void 대상_게시글이_숨김_후_삭제되어도_운영_기록은_조회된다() {
        Long postLog = savePostLog(ModerationAction.HIDE, NOW);
        Long commentLog = saveCommentLog(ModerationAction.HIDE, NOW);

        Post post = entityManager.find(Post.class, postId);
        post.hide(NOW);
        post.delete(NOW.plusSeconds(60));
        flushAndClear();

        Page<ContentModerationLog> result = readPage(0, 20);

        assertThat(result.getContent())
                .extracting(ContentModerationLog::getId)
                .containsExactly(commentLog, postLog);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void 허용_날짜_범위의_양끝을_사용해_DB를_조회할_수_있다() {
        Long logId = savePostLog(ModerationAction.HIDE, NOW);
        flushAndClear();

        Page<ContentModerationLog> result = repository.findLogs(
                new AdminOperationLogReadQuery(
                        null,
                        null,
                        LocalDate.of(1, 1, 1),
                        LocalDate.of(9999, 12, 31),
                        0,
                        20
                )
        );

        assertThat(result.getContent())
                .extracting(ContentModerationLog::getId)
                .containsExactly(logId);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    private Page<ContentModerationLog> readPage(int page, int size) {
        return repository.findLogs(
                new AdminOperationLogReadQuery(
                        null, null, null, null, page, size
                )
        );
    }

    private Long savePostLog(ModerationAction action, Instant at) {
        ContentModerationLog log = ContentModerationLog.forPost(
                actorId,
                postId,
                action,
                "운영 기록 조회 테스트",
                at
        );

        entityManager.persist(log);
        entityManager.flush();
        return log.getId();
    }

    private Long saveCommentLog(ModerationAction action, Instant at) {
        ContentModerationLog log = ContentModerationLog.forComment(
                actorId,
                commentId,
                action,
                "운영 기록 조회 테스트",
                at
        );

        entityManager.persist(log);
        entityManager.flush();
        return log.getId();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}