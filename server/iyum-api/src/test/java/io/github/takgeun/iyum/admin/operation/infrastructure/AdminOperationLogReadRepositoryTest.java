package io.github.takgeun.iyum.admin.operation.infrastructure;

import io.github.takgeun.iyum.admin.moderation.domain.ContentModerationLog;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationReasonType;
import io.github.takgeun.iyum.admin.operation.application.AdminOperationLogReadQuery;
import io.github.takgeun.iyum.admin.operation.application.AdminOperationLogSort;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
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

    @ParameterizedTest
    @EnumSource(AdminOperationLogSort.class)
    void 대상과_조치와_기간을_모두_적용하고_페이지를_나눈다(
            AdminOperationLogSort sort
    ) {
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
                        date, date, 0, 2, sort
                )
        );
        Page<ContentModerationLog> lastPage = repository.findLogs(
                new AdminOperationLogReadQuery(
                        AdminOperationLogTargetType.POST,
                        ModerationAction.HIDE,
                        date, date, 1, 2, sort
                )
        );

        List<Long> expected = switch (sort) {
            case LATEST -> List.of(third, second, first);
            case OLDEST -> List.of(first, second, third);
        };

        assertThat(firstPage.getContent())
                .extracting(ContentModerationLog::getId)
                .containsExactlyElementsOf(expected.subList(0, 2));
        assertThat(lastPage.getContent())
                .extracting(ContentModerationLog::getId)
                .containsExactlyElementsOf(expected.subList(2, 3));

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

    @ParameterizedTest
    @EnumSource(AdminOperationLogSort.class)
    void 처리시각과_ID를_선택한_방향으로_정렬한다(
            AdminOperationLogSort sort
    ) {
        Long newerFirst = savePostLog(
                ModerationAction.HIDE,
                NOW.plusSeconds(60)
        );
        Long newerSecond = saveCommentLog(
                ModerationAction.HIDE,
                NOW.plusSeconds(60)
        );

        // 가장 큰 ID를 갖지만 처리 시각은 가장 오래된 기록.
        // ID만으로 정렬하는 잘못된 구현을 검출한다.
        Long older = savePostLog(
                ModerationAction.RESTORE,
                NOW
        );
        flushAndClear();

        Page<ContentModerationLog> result = repository.findLogs(
                new AdminOperationLogReadQuery(
                        null, null, null, null, 0, 20, sort
                )
        );

        List<Long> expected = switch (sort) {
            case LATEST -> List.of(newerSecond, newerFirst, older);
            case OLDEST -> List.of(older, newerFirst, newerSecond);
        };

        assertThat(result.getContent())
                .extracting(ContentModerationLog::getId)
                .containsExactlyElementsOf(expected);

        Sort.Direction direction = switch (sort) {
            case LATEST -> Sort.Direction.DESC;
            case OLDEST -> Sort.Direction.ASC;
        };

        assertThat(result.getSort())
                .isEqualTo(Sort.by(direction, "createdAt", "id"));
        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @ParameterizedTest
    @EnumSource(AdminOperationLogSort.class)
    void 같은_처리시각의_기록을_페이지별로_중복과_누락_없이_조회한다(
            AdminOperationLogSort sort
    ) {
        Long first = savePostLog(ModerationAction.HIDE, NOW);
        Long second = saveCommentLog(ModerationAction.HIDE, NOW);
        Long third = savePostLog(ModerationAction.RESTORE, NOW);
        flushAndClear();

        Page<ContentModerationLog> firstPage = repository.findLogs(
                new AdminOperationLogReadQuery(
                        null, null, null, null, 0, 2, sort
                )
        );

        Page<ContentModerationLog> secondPage = repository.findLogs(
                new AdminOperationLogReadQuery(
                        null, null, null, null, 1, 2, sort
                )
        );

        List<Long> expected = switch (sort) {
            case LATEST -> List.of(third, second, first);
            case OLDEST -> List.of(first, second, third);
        };

        assertThat(firstPage.getContent())
                .extracting(ContentModerationLog::getId)
                .containsExactlyElementsOf(expected.subList(0, 2));

        assertThat(secondPage.getContent())
                .extracting(ContentModerationLog::getId)
                .containsExactlyElementsOf(expected.subList(2, 3));

        for (Page<ContentModerationLog> page :
                List.of(firstPage, secondPage)) {
            assertThat(page.getTotalElements()).isEqualTo(3);
            assertThat(page.getTotalPages()).isEqualTo(2);
        }

        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.hasPrevious()).isFalse();
        assertThat(secondPage.hasNext()).isFalse();
        assertThat(secondPage.hasPrevious()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "POST, HIDE, SPAM_ADVERTISING, 광고 링크 반복, 광고 링크 반복",
            "COMMENT, HIDE, SPAM_ADVERTISING, 광고 링크 반복, 광고 링크 반복",
            "POST, RESTORE, NO_VIOLATION, , 재검토 결과 위반 없음",
            "COMMENT, RESTORE, NO_VIOLATION, , 재검토 결과 위반 없음",
            "POST, HIDE, OTHER, 개별 검토 사유, 개별 검토 사유",
            "COMMENT, RESTORE, OTHER, 개별 복원 사유, 개별 복원 사유"
    })
    void 사유_유형과_상세_내용을_DB에_저장하고_다시_조회한다(
            String target,
            ModerationAction action,
            ModerationReasonType reasonType,
            String detail,
            String expectedReason
    ) {
        // 상세 사유가 있는 경우 앞뒤 공백 정리도 함께 검증한다.
        String inputDetail = detail == null
                ? null
                : "  " + detail + "  ";

        ContentModerationLog log = target.equals("POST")
                ? ContentModerationLog.forPost(
                actorId,
                postId,
                action,
                reasonType,
                inputDetail,
                NOW
        )
                : ContentModerationLog.forComment(
                actorId,
                commentId,
                action,
                reasonType,
                inputDetail,
                NOW
        );

        entityManager.persist(log);
        entityManager.flush();

        Long logId = log.getId();

        // 저장 직후의 객체를 그대로 검사하지 않도록
        // 1차 캐시를 비우고 DB에서 다시 조회한다.
        entityManager.clear();

        ContentModerationLog stored = entityManager.find(
                ContentModerationLog.class,
                logId
        );

        assertThat(stored).isNotNull();
        assertThat(stored.getId()).isEqualTo(logId);
        assertThat(stored.getActorMemberId()).isEqualTo(actorId);
        assertThat(stored.getAction()).isEqualTo(action);
        assertThat(stored.getReasonType()).isEqualTo(reasonType);
        assertThat(stored.getReasonDetail()).isEqualTo(detail);
        assertThat(stored.getReason()).isEqualTo(expectedReason);
        assertThat(stored.getCreatedAt()).isEqualTo(NOW);

        if (target.equals("POST")) {
            assertThat(stored.getPostId()).isEqualTo(postId);
            assertThat(stored.getCommentId()).isNull();
        } else {
            assertThat(stored.getPostId()).isNull();
            assertThat(stored.getCommentId()).isEqualTo(commentId);
        }

        // enum이 순서 번호가 아닌 문자열 코드로 저장되는지 확인한다.
        String storedReasonType = jdbcTemplate.queryForObject(
                """
                        SELECT reason_type
                        FROM admin_content_moderation_logs
                        WHERE id = ?
                        """,
                String.class,
                logId
        );

        assertThat(storedReasonType).isEqualTo(reasonType.name());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "COMMENT"})
    void 이모지_500자의_상세_사유를_DB에_저장할_수_있다(
            String target
    ) {
        String detail = "😀".repeat(500);

        ContentModerationLog log = target.equals("POST")
                ? ContentModerationLog.forPost(
                actorId,
                postId,
                ModerationAction.HIDE,
                ModerationReasonType.OTHER,
                detail,
                NOW
        )
                : ContentModerationLog.forComment(
                actorId,
                commentId,
                ModerationAction.HIDE,
                ModerationReasonType.OTHER,
                detail,
                NOW
        );

        entityManager.persist(log);
        entityManager.flush();

        Long logId = log.getId();
        entityManager.clear();

        ContentModerationLog stored = entityManager.find(
                ContentModerationLog.class,
                logId
        );

        assertThat(stored).isNotNull();
        assertThat(stored.getReasonType())
                .isEqualTo(ModerationReasonType.OTHER);
        assertThat(stored.getReasonDetail()).isEqualTo(detail);
        assertThat(stored.getReason()).isEqualTo(detail);
    }

    @Test
    void 기존_자유입력_방식은_LEGACY와_원문을_보존한다() {
        ContentModerationLog log = ContentModerationLog.forPost(
                actorId,
                postId,
                ModerationAction.HIDE,
                "기존 운영자가 작성한 사유",
                NOW
        );

        entityManager.persist(log);
        entityManager.flush();

        Long logId = log.getId();
        entityManager.clear();

        ContentModerationLog stored = entityManager.find(
                ContentModerationLog.class,
                logId
        );

        assertThat(stored).isNotNull();
        assertThat(stored.getReasonType())
                .isEqualTo(ModerationReasonType.LEGACY);
        assertThat(stored.getReasonDetail()).isNull();
        assertThat(stored.getReason())
                .isEqualTo("기존 운영자가 작성한 사유");
        assertThat(stored.getCreatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @EnumSource(ModerationReasonType.class)
    void 사유_유형으로_게시글과_댓글_기록을_필터링한다(
            ModerationReasonType selectedType
    ) {
        Long expectedPostId = null;
        Long expectedCommentId = null;

        for (ModerationReasonType type : ModerationReasonType.values()) {
            ModerationAction action = switch (type) {
                case MODERATION_ERROR, NO_VIOLATION ->
                        ModerationAction.RESTORE;
                default -> ModerationAction.HIDE;
            };

            Long postLogId = saveReasonLog(false, action, type, NOW);
            Long commentLogId = saveReasonLog(true, action, type, NOW);

            if (type == selectedType) {
                expectedPostId = postLogId;
                expectedCommentId = commentLogId;
            }
        }

        flushAndClear();

        Page<ContentModerationLog> result = repository.findLogs(
                new AdminOperationLogReadQuery(
                        AdminOperationLogTargetType.ALL,
                        null,
                        null,
                        null,
                        0,
                        20,
                        AdminOperationLogSort.LATEST,
                        selectedType
                )
        );

        // 처리 시각이 같으므로 나중에 저장한 댓글 기록이 먼저 나온다.
        assertThat(result.getContent())
                .extracting(ContentModerationLog::getId)
                .containsExactly(expectedCommentId, expectedPostId);

        assertThat(result.getContent())
                .extracting(ContentModerationLog::getReasonType)
                .containsOnly(selectedType);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getTotalPages()).isEqualTo(1);
    }

    @Test
    void 사유_유형을_생략하면_신규_기록과_LEGACY를_모두_조회한다() {
        Long legacy = savePostLog(ModerationAction.HIDE, NOW);

        Long spam = saveReasonLog(
                false,
                ModerationAction.HIDE,
                ModerationReasonType.SPAM_ADVERTISING,
                NOW
        );

        Long other = saveReasonLog(
                true,
                ModerationAction.RESTORE,
                ModerationReasonType.OTHER,
                NOW
        );

        flushAndClear();

        Page<ContentModerationLog> result = readPage(0, 20);

        assertThat(result.getContent())
                .extracting(ContentModerationLog::getId)
                .containsExactly(other, spam, legacy);

        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @ParameterizedTest
    @EnumSource(AdminOperationLogSort.class)
    void 사유와_기존_필터를_함께_적용한_뒤_정렬하고_페이지를_나눈다(
            AdminOperationLogSort sort
    ) {
        Long first = saveReasonLog(
                false,
                ModerationAction.HIDE,
                ModerationReasonType.OTHER,
                NOW
        );

        Long second = saveReasonLog(
                false,
                ModerationAction.HIDE,
                ModerationReasonType.OTHER,
                NOW.plusSeconds(1)
        );

        // second와 같은 시각으로 만들어 ID 보조 정렬도 확인한다.
        Long third = saveReasonLog(
                false,
                ModerationAction.HIDE,
                ModerationReasonType.OTHER,
                NOW.plusSeconds(1)
        );

        // 대상 유형만 불일치.
        saveReasonLog(
                true,
                ModerationAction.HIDE,
                ModerationReasonType.OTHER,
                NOW
        );

        // 조치 유형만 불일치.
        saveReasonLog(
                false,
                ModerationAction.RESTORE,
                ModerationReasonType.OTHER,
                NOW
        );

        // 사유 유형만 불일치.
        saveReasonLog(
                false,
                ModerationAction.HIDE,
                ModerationReasonType.SPAM_ADVERTISING,
                NOW
        );

        // 처리 기간만 불일치.
        saveReasonLog(
                false,
                ModerationAction.HIDE,
                ModerationReasonType.OTHER,
                NOW.minusSeconds(86_400)
        );

        flushAndClear();

        LocalDate date = LocalDate.of(2026, 11, 4);

        List<Long> expected = switch (sort) {
            case LATEST -> List.of(third, second, first);
            case OLDEST -> List.of(first, second, third);
        };

        // 첫 페이지, 마지막 페이지, 범위 밖 페이지까지 확인한다.
        for (int pageNumber = 0; pageNumber < 3; pageNumber++) {
            Page<ContentModerationLog> result = repository.findLogs(
                    new AdminOperationLogReadQuery(
                            AdminOperationLogTargetType.POST,
                            ModerationAction.HIDE,
                            date,
                            date,
                            pageNumber,
                            2,
                            sort,
                            ModerationReasonType.OTHER
                    )
            );

            int from = Math.min(pageNumber * 2, expected.size());
            int to = Math.min(from + 2, expected.size());

            assertThat(result.getContent())
                    .extracting(ContentModerationLog::getId)
                    .containsExactlyElementsOf(expected.subList(from, to));

            // 범위 밖 페이지에서도 필터에 맞는 전체 개수는 유지한다.
            assertThat(result.getTotalElements()).isEqualTo(3);
            assertThat(result.getTotalPages()).isEqualTo(2);
            assertThat(result.getNumber()).isEqualTo(pageNumber);
            assertThat(result.hasNext()).isEqualTo(pageNumber == 0);
            assertThat(result.hasPrevious()).isEqualTo(pageNumber > 0);
        }
    }

    @Test
    void 조치와_사유를_동시에_만족하는_기록이_없으면_빈_결과를_반환한다() {
        saveReasonLog(
                false,
                ModerationAction.HIDE,
                ModerationReasonType.SPAM_ADVERTISING,
                NOW
        );

        saveReasonLog(
                false,
                ModerationAction.RESTORE,
                ModerationReasonType.NO_VIOLATION,
                NOW
        );

        flushAndClear();

        Page<ContentModerationLog> result = repository.findLogs(
                new AdminOperationLogReadQuery(
                        AdminOperationLogTargetType.ALL,
                        ModerationAction.RESTORE,
                        null,
                        null,
                        0,
                        20,
                        AdminOperationLogSort.LATEST,
                        ModerationReasonType.SPAM_ADVERTISING
                )
        );

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getTotalPages()).isZero();
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

    /**
     * 필터 검증용 운영 기록을 저장한다.
     * <p>
     * LEGACY는 기존 자유입력 팩터리로 생성한다.
     * 신규 조치용 팩터리에 LEGACY를 전달하지 않는다.
     */
    private Long saveReasonLog(
            boolean commentTarget,
            ModerationAction action,
            ModerationReasonType reasonType,
            Instant at
    ) {
        ContentModerationLog log;

        if (reasonType == ModerationReasonType.LEGACY) {
            log = commentTarget
                    ? ContentModerationLog.forComment(
                    actorId,
                    commentId,
                    action,
                    "동일한 상세 사유",
                    at
            )
                    : ContentModerationLog.forPost(
                    actorId,
                    postId,
                    action,
                    "동일한 상세 사유",
                    at
            );
        } else {
            // 사유 문구를 모두 같게 만들어,
            // 문구가 아니라 유형 코드로 필터링하는지 확인한다.
            log = commentTarget
                    ? ContentModerationLog.forComment(
                    actorId,
                    commentId,
                    action,
                    reasonType,
                    "동일한 상세 사유",
                    at
            )
                    : ContentModerationLog.forPost(
                    actorId,
                    postId,
                    action,
                    reasonType,
                    "동일한 상세 사유",
                    at
            );
        }

        entityManager.persist(log);
        entityManager.flush();
        return log.getId();
    }
}