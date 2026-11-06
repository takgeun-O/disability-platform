package io.github.takgeun.iyum.admin.operation.api;

import io.github.takgeun.iyum.admin.moderation.domain.ContentModerationLog;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationAction;
import io.github.takgeun.iyum.admin.moderation.domain.ModerationReasonType;
import io.github.takgeun.iyum.admin.moderation.infrastructure.ContentModerationLogRepository;
import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true"
})
@AutoConfigureMockMvc(addFilters = true)
@Testcontainers
class AdminOperationLogSessionIntegrationTest {

    private static final String PATH =
            "/api/v1/admin/operation-logs";

    private static final String PASSWORD =
            "OperationLogTest123!";

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PostRepository postRepository;

    @Autowired
    CommentRepository commentRepository;

    @Autowired
    ContentModerationLogRepository logRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Member admin;
    private Member author;
    private Long postId;
    private Long commentId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);

        String passwordHash = passwordEncoder.encode(PASSWORD);

        admin = saveMember(
                "operation-admin-" + suffix + "@example.com",
                "기록관리" + suffix,
                passwordHash
        );

        author = saveMember(
                "operation-author-" + suffix + "@example.com",
                "기록작성" + suffix,
                passwordHash
        );

        // 실제 로그인 전에 DB의 역할과 인증 버전을 설정한다.
        int updated = jdbcTemplate.update("""
                UPDATE members
                SET role = 'ADMIN',
                    credential_version = 3
                WHERE id = ?
                """, admin.getId());

        assertThat(updated).isEqualTo(1);

        Post post = postRepository.saveAndFlush(
                Post.create(
                        author,
                        PostType.GENERAL,
                        PostTopic.DAILY,
                        "운영 기록 통합 테스트",
                        "게시글 본문"
                )
        );
        postId = post.getId();

        Comment comment = commentRepository.saveAndFlush(
                Comment.create(post, author, "댓글 본문")
        );
        commentId = comment.getId();
    }

    @AfterEach
    void cleanUpLogs() {
        // 이 클래스의 전용 Testcontainers DB에서
        // 이번 테스트 관리자가 만든 기록만 정리한다.
        // 전체 조회 개수가 다음 테스트에 누적되는 것을 방지한다.
        if (admin != null) {
            jdbcTemplate.update("""
                    DELETE FROM admin_content_moderation_logs
                    WHERE actor_member_id = ?
                    """, admin.getId());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "COMMENT"})
    void 실제_숨김복원으로_저장한_기록을_조회한다(
            String targetType
    ) throws Exception {
        MockHttpSession session = login(admin.getEmail());

        String moderationPath = targetType.equals("POST")
                ? "/api/v1/admin/posts/" + postId
                : "/api/v1/admin/posts/" + postId
                  + "/comments/" + commentId;

        Long targetId = targetType.equals("POST")
                ? postId
                : commentId;

        // 로그인 시 CSRF 토큰이 갱신될 수 있으므로 다시 발급받는다.
        JsonNode csrf = getCsrf(session);

        moderate(
                session,
                csrf,
                moderationPath + "/hide",
                ModerationReasonType.SPAM_ADVERTISING,
                "  운영 정책 위반 확인  "
        );

        moderate(
                session,
                csrf,
                moderationPath + "/restore",
                ModerationReasonType.NO_VIOLATION,
                "  재검토 결과 복원  "
        );

        MvcResult result = expectOk(mockMvc.perform(
                get(PATH)
                        .session(session)
                        .param("targetType", targetType)
        ))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalElements")
                        .value(is(2L), Long.class))
                .andExpect(jsonPath("$.items[0].action").value("RESTORE"))
                .andExpect(jsonPath("$.items[0].reason")
                        .value("재검토 결과 복원"))
                .andExpect(jsonPath("$.items[1].action").value("HIDE"))
                .andExpect(jsonPath("$.items[1].reason")
                        .value("운영 정책 위반 확인"))
                .andExpect(jsonPath("$.items[0].reasonType")
                        .value("NO_VIOLATION"))
                .andExpect(jsonPath("$.items[0].reasonDetail")
                        .value("재검토 결과 복원"))
                .andExpect(jsonPath("$.items[1].reasonType")
                        .value("SPAM_ADVERTISING"))
                .andExpect(jsonPath("$.items[1].reasonDetail")
                        .value("운영 정책 위반 확인"))
                .andExpect(jsonPath("$.items[0].targetType")
                        .value(targetType))
                .andExpect(jsonPath("$.items[1].targetType")
                        .value(targetType))
                .andExpect(jsonPath("$.items[0].targetId")
                        .value(is(targetId), Long.class))
                .andExpect(jsonPath("$.items[1].targetId")
                        .value(is(targetId), Long.class))
                .andExpect(jsonPath("$.items[0].actorMemberId")
                        .value(is(admin.getId()), Long.class))
                .andExpect(jsonPath("$.items[1].actorMemberId")
                        .value(is(admin.getId()), Long.class))
                .andReturn();

        // 응답 ID와 처리 시각이 실제 저장된 기록과 일치하는지 확인한다.
        JsonNode items = readJson(result).path("items");

        for (int i = 0; i < items.size(); i++) {
            JsonNode item = items.get(i);

            ContentModerationLog stored = logRepository.findById(
                    item.path("logId").asLong()
            ).orElseThrow();

            ModerationAction action = ModerationAction.valueOf(
                    item.path("action").asString()
            );

            ModerationReasonType expectedType =
                    action == ModerationAction.HIDE
                            ? ModerationReasonType.SPAM_ADVERTISING
                            : ModerationReasonType.NO_VIOLATION;

            String expectedDetail =
                    action == ModerationAction.HIDE
                            ? "운영 정책 위반 확인"
                            : "재검토 결과 복원";

            assertThat(stored.getAction()).isEqualTo(action);
            assertThat(stored.getReasonType()).isEqualTo(expectedType);

            // 실제 서비스에서 앞뒤 공백을 제거한 뒤 저장했는지 확인한다.
            assertThat(stored.getReasonDetail()).isEqualTo(expectedDetail);
            assertThat(stored.getReason()).isEqualTo(expectedDetail);

            assertThat(stored.getActorMemberId())
                    .isEqualTo(admin.getId());

            assertThat(Instant.parse(item.path("createdAt").asString()))
                    .isEqualTo(stored.getCreatedAt());

            assertThat(item.path("reasonType").asString())
                    .isEqualTo(stored.getReasonType().name());

            assertThat(item.has("reasonDetail")).isTrue();

            if (stored.getReasonDetail() == null) {
                assertThat(item.path("reasonDetail").isNull()).isTrue();
            } else {
                assertThat(item.path("reasonDetail").asString())
                        .isEqualTo(stored.getReasonDetail());
            }

            assertThat(item.path("reason").asString())
                    .isEqualTo(stored.getReason());

            if ("POST".equals(targetType)) {
                assertThat(stored.getPostId()).isEqualTo(postId);
                assertThat(stored.getCommentId()).isNull();
            } else {
                assertThat(stored.getPostId()).isNull();
                assertThat(stored.getCommentId()).isEqualTo(commentId);
            }
        }
    }

    @Test
    void 한국_날짜와_대상과_조치_필터를_적용하고_페이지를_나눈다()
            throws Exception {
        Instant start = Instant.parse("2026-11-03T15:00:00Z");
        Instant end = Instant.parse("2026-11-04T15:00:00Z");

        // 조건에 일치하는 두 건.
        Long first = savePostLog(ModerationAction.HIDE, start);
        Long last = savePostLog(
                ModerationAction.HIDE,
                end.minusNanos(1_000)
        );

        // 날짜·조치·대상 조건에 각각 맞지 않는 기록.
        savePostLog(ModerationAction.HIDE, start.minusNanos(1_000));
        savePostLog(ModerationAction.HIDE, end);
        savePostLog(ModerationAction.RESTORE, start);

        logRepository.saveAndFlush(ContentModerationLog.forComment(
                admin.getId(),
                commentId,
                ModerationAction.HIDE,
                "댓글 숨김 기록",
                start
        ));

        MockHttpSession session = login(admin.getEmail());

        expectOk(readFilteredPage(session, 0))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].logId")
                        .value(is(last), Long.class))
                .andExpect(jsonPath("$.totalElements")
                        .value(is(2L), Long.class))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.hasPrevious").value(false));

        expectOk(readFilteredPage(session, 1))
                .andExpect(jsonPath("$.items[0].logId")
                        .value(is(first), Long.class))
                .andExpect(jsonPath("$.totalElements")
                        .value(is(2L), Long.class))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(true));

        expectOk(readFilteredPage(session, 2))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements")
                        .value(is(2L), Long.class));
    }

    @Test
    void 기록이_없으면_빈_목록을_반환한다() throws Exception {
        MockHttpSession session = login(admin.getEmail());

        expectOk(mockMvc.perform(get(PATH).session(session)))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements")
                        .value(is(0L), Long.class))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void 비로그인은_운영_기록을_조회할_수_없다() throws Exception {
        savePostLog(ModerationAction.HIDE, Instant.now());

        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.items").doesNotExist());
    }

    @Test
    void 실제_일반_회원_세션은_403이며_로그인은_유지한다()
            throws Exception {
        savePostLog(ModerationAction.HIDE, Instant.now());

        MockHttpSession session = login(author.getEmail());

        mockMvc.perform(get(PATH).session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.items").doesNotExist());

        assertThat(session.isInvalid()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE", "VERSION", "STATUS"})
    void 로그인_후_인증정보가_변경되면_기존_세션을_차단한다(
            String change
    ) throws Exception {
        savePostLog(ModerationAction.HIDE, Instant.now());

        MockHttpSession session = login(admin.getEmail());

        expectOk(mockMvc.perform(get(PATH).session(session)))
                .andExpect(jsonPath("$.items.length()").value(1));

        int updated = switch (change) {
            case "ROLE" -> jdbcTemplate.update("""
                    UPDATE members
                    SET role = 'USER'
                    WHERE id = ?
                    """, admin.getId());

            case "VERSION" -> jdbcTemplate.update("""
                    UPDATE members
                    SET credential_version = credential_version + 1
                    WHERE id = ?
                    """, admin.getId());

            case "STATUS" -> jdbcTemplate.update("""
                    UPDATE members
                    SET status = 'PENDING'
                    WHERE id = ?
                    """, admin.getId());

            default -> throw new IllegalArgumentException(change);
        };

        assertThat(updated).isEqualTo(1);

        mockMvc.perform(get(PATH).session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.items").doesNotExist())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        assertThat(session.isInvalid()).isTrue();

        if (change.equals("ROLE")) {
            // 권한 강등 후 새로 로그인하면 유효한 USER 세션이므로 403.
            MockHttpSession newSession = login(admin.getEmail());

            mockMvc.perform(get(PATH).session(newSession))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

            assertThat(newSession.isInvalid()).isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEFAULT", "LATEST", "OLDEST"})
    void 실제_관리자_세션으로_정렬과_필터와_페이지를_함께_적용한다(
            String sort
    ) throws Exception {
        Instant earlier = Instant.parse("2026-11-04T01:00:00Z");
        Instant later = Instant.parse("2026-11-04T02:00:00Z");

        // 처리 시각이 같은 두 기록으로 ID 보조 정렬도 검증한다.
        Long newerFirst = savePostLog(ModerationAction.HIDE, later);
        Long newerSecond = savePostLog(ModerationAction.HIDE, later);

        // ID는 가장 크지만 처리 시각은 가장 오래된 기록이다.
        Long older = savePostLog(ModerationAction.HIDE, earlier);

        // 검색 조건에 맞지 않는 기록은 정렬·페이지·전체 개수에서 제외한다.
        savePostLog(ModerationAction.RESTORE, earlier);
        savePostLog(
                ModerationAction.HIDE,
                Instant.parse("2026-11-03T14:59:59Z")
        );
        logRepository.saveAndFlush(
                ContentModerationLog.forComment(
                        admin.getId(),
                        commentId,
                        ModerationAction.HIDE,
                        "댓글 숨김 기록",
                        earlier
                )
        );

        MockHttpSession session = login(admin.getEmail());

        List<Long> expected = sort.equals("OLDEST")
                ? List.of(older, newerFirst, newerSecond)
                : List.of(newerSecond, newerFirst, older);

        // 한 페이지에 한 건씩 조회하여 페이지 경계의 순서까지 확인한다.
        for (int page = 0; page < expected.size(); page++) {
            expectOk(readSortedFilteredPage(session, page, sort))
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].logId")
                            .value(is(expected.get(page)), Long.class))
                    .andExpect(jsonPath("$.items[0].targetType")
                            .value("POST"))
                    .andExpect(jsonPath("$.items[0].action")
                            .value("HIDE"))
                    .andExpect(jsonPath("$.page").value(page))
                    .andExpect(jsonPath("$.size").value(1))
                    .andExpect(jsonPath("$.totalElements")
                            .value(is(3L), Long.class))
                    .andExpect(jsonPath("$.totalPages").value(3))
                    .andExpect(jsonPath("$.hasNext")
                            .value(page < expected.size() - 1))
                    .andExpect(jsonPath("$.hasPrevious")
                            .value(page > 0));
        }

        // 범위를 벗어나도 전체 검색 결과 개수는 유지한다.
        expectOk(readSortedFilteredPage(session, 3, sort))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements")
                        .value(is(3L), Long.class))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.hasNext").value(false));

        assertThat(session.isInvalid()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "POST, false",
            "COMMENT, false",
            "POST, true",
            "COMMENT, true"
    })
    void 실제_세션으로_상세_생략과_코드포인트_500자를_처리한다(
            String targetType,
            boolean otherWithMaxDetail
    ) throws Exception {
        MockHttpSession session = login(admin.getEmail());
        JsonNode csrf = getCsrf(session);

        String moderationPath = "POST".equals(targetType)
                ? "/api/v1/admin/posts/" + postId
                : "/api/v1/admin/posts/" + postId
                  + "/comments/" + commentId;

        // 이모지는 UTF-16 길이가 아니라 코드 포인트 기준으로 센다.
        String expectedDetail = otherWithMaxDetail
                ? "😀".repeat(500)
                : null;

        String inputDetail = expectedDetail == null
                ? null
                : "  " + expectedDetail + "  ";

        ModerationReasonType hideType = otherWithMaxDetail
                ? ModerationReasonType.OTHER
                : ModerationReasonType.SPAM_ADVERTISING;

        ModerationReasonType restoreType = otherWithMaxDetail
                ? ModerationReasonType.OTHER
                : ModerationReasonType.NO_VIOLATION;

        moderate(
                session,
                csrf,
                moderationPath + "/hide",
                hideType,
                inputDetail
        );

        // HTTP 성공뿐 아니라 실제 콘텐츠 상태 변경도 확인한다.
        if ("POST".equals(targetType)) {
            assertThat(
                    postRepository.findById(postId)
                            .orElseThrow()
                            .getHiddenAt()
            ).isNotNull();
        } else {
            assertThat(
                    commentRepository.findById(commentId)
                            .orElseThrow()
                            .getHiddenAt()
            ).isNotNull();

            // 댓글 조치가 부모 게시글을 숨기면 안 된다.
            assertThat(
                    postRepository.findById(postId)
                            .orElseThrow()
                            .getHiddenAt()
            ).isNull();
        }

        moderate(
                session,
                csrf,
                moderationPath + "/restore",
                restoreType,
                inputDetail
        );

        if ("POST".equals(targetType)) {
            assertThat(
                    postRepository.findById(postId)
                            .orElseThrow()
                            .getHiddenAt()
            ).isNull();
        } else {
            assertThat(
                    commentRepository.findById(commentId)
                            .orElseThrow()
                            .getHiddenAt()
            ).isNull();
        }

        // 이 테스트의 관리자가 남긴 이력만 조회한다.
        // 엔티티의 메모리 값이 아닌 실제 DB 컬럼을 확인한다.
        var logs = jdbcTemplate.queryForList("""
                SELECT post_id,
                       comment_id,
                       action,
                       reason_type,
                       reason_detail,
                       reason
                FROM admin_content_moderation_logs
                WHERE actor_member_id = ?
                ORDER BY id ASC
                """, admin.getId());

        assertThat(logs).hasSize(2);

        assertThat(logs)
                .extracting(row -> row.get("action"))
                .containsExactly("HIDE", "RESTORE");

        for (Map<String, Object> log : logs) {
            boolean hiddenAction = "HIDE".equals(log.get("action"));

            ModerationReasonType expectedType = hiddenAction
                    ? hideType
                    : restoreType;

            String expectedReason = otherWithMaxDetail
                    ? expectedDetail
                    : hiddenAction
                      ? "광고·홍보·스팸"
                      : "재검토 결과 위반 없음";

            assertThat(log.get("reason_type"))
                    .isEqualTo(expectedType.name());

            assertThat(log.get("reason_detail"))
                    .isEqualTo(expectedDetail);

            // 상세가 없으면 유형의 표시명을,
            // 상세가 있으면 정규화한 상세를 저장한다.
            assertThat(log.get("reason"))
                    .isEqualTo(expectedReason);

            if ("POST".equals(targetType)) {
                assertThat(log.get("post_id")).isEqualTo(postId);
                assertThat(log.get("comment_id")).isNull();
            } else {
                assertThat(log.get("post_id")).isNull();
                assertThat(log.get("comment_id")).isEqualTo(commentId);
            }
        }

        MvcResult response = expectOk(mockMvc.perform(
                get(PATH)
                        .session(session)
                        .param("targetType", targetType)
        ))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn();

        JsonNode items = readJson(response).path("items");

        for (int i = 0; i < items.size(); i++) {
            JsonNode item = items.get(i);

            boolean hideAction =
                    "HIDE".equals(item.path("action").asString());

            ModerationReasonType expectedType =
                    hideAction ? hideType : restoreType;

            ContentModerationLog stored = logRepository.findById(
                    item.path("logId").asLong()
            ).orElseThrow();
            String expectedReason = otherWithMaxDetail
                    ? expectedDetail
                    : hideAction ? "광고·홍보·스팸" : "재검토 결과 위반 없음";

            assertThat(stored.getReasonType()).isEqualTo(expectedType);
            assertThat(stored.getReasonDetail()).isEqualTo(expectedDetail);
            assertThat(stored.getReason()).isEqualTo(expectedReason);
            assertThat(item.path("reason").asString())
                    .isEqualTo(stored.getReason());
            assertThat(item.path("actorMemberId").asLong())
                    .isEqualTo(stored.getActorMemberId())
                    .isEqualTo(admin.getId());
            assertThat(item.path("targetType").asString()).isEqualTo(targetType);
            assertThat(item.path("targetId").asLong())
                    .isEqualTo("POST".equals(targetType) ? postId : commentId);
            assertThat(Instant.parse(item.path("createdAt").asString()))
                    .isEqualTo(stored.getCreatedAt());

            assertThat(item.path("reasonType").asString())
                    .isEqualTo(expectedType.name());

            assertThat(item.has("reasonDetail")).isTrue();

            if (expectedDetail == null) {
                assertThat(item.path("reasonDetail").isNull()).isTrue();
            } else {
                assertThat(item.path("reasonDetail").asString())
                        .isEqualTo(expectedDetail);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("invalidReasonCases")
    void 잘못된_사유는_거부하고_콘텐츠와_운영기록을_유지한다(
            String targetType,
            String action,
            String invalidCase
    ) throws Exception {
        MockHttpSession session = login(admin.getEmail());
        JsonNode csrf = getCsrf(session);

        String moderationPath = "POST".equals(targetType)
                ? "/api/v1/admin/posts/" + postId
                : "/api/v1/admin/posts/" + postId
                  + "/comments/" + commentId;

        if ("restore".equals(action)) {
            // 복원 요청은 실제 숨김 상태에서 검증한다.
            // 정상 상태에서 검증하면 중복 요청 분기 때문에
            // 상태 변경 오류를 놓칠 수 있다.
            moderate(
                    session,
                    csrf,
                    moderationPath + "/hide",
                    ModerationReasonType.SPAM_ADVERTISING,
                    "복원 실패 검증을 위한 사전 숨김"
            );
        }

        // HTTP 요청 전의 실제 DB 상태를 보관한다.
        // 두 콘텐츠를 모두 비교하여 댓글 조치가 부모 게시글까지
        // 변경하는 경우도 잡는다.
        Map<String, Object> postBefore =
                jdbcTemplate.queryForMap("""
                        SELECT *
                        FROM community_posts
                        WHERE id = ?
                        """, postId);

        Map<String, Object> commentBefore =
                jdbcTemplate.queryForMap("""
                        SELECT *
                        FROM community_comments
                        WHERE id = ?
                        """, commentId);

        var logsBefore = jdbcTemplate.queryForList("""
                SELECT *
                FROM admin_content_moderation_logs
                WHERE actor_member_id = ?
                ORDER BY id ASC
                """, admin.getId());

        // 준비 상태도 명시적으로 확인한다.
        Map<String, Object> targetBefore = "POST".equals(targetType)
                ? postBefore
                : commentBefore;

        if ("restore".equals(action)) {
            assertThat(targetBefore.get("hidden_at")).isNotNull();
            assertThat(logsBefore).hasSize(1);
        } else {
            assertThat(targetBefore.get("hidden_at")).isNull();
            assertThat(logsBefore).isEmpty();
        }

        Map<String, Object> body =
                invalidReasonBody(action, invalidCase);

        mockMvc.perform(
                        post(moderationPath + "/" + action)
                                .session(session)
                                .header(
                                        csrf.path("headerName").asString(),
                                        csrf.path("token").asString()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body))
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code").value("INVALID_REASON"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        Map<String, Object> postAfter =
                jdbcTemplate.queryForMap("""
                        SELECT *
                        FROM community_posts
                        WHERE id = ?
                        """, postId);

        Map<String, Object> commentAfter =
                jdbcTemplate.queryForMap("""
                        SELECT *
                        FROM community_comments
                        WHERE id = ?
                        """, commentId);

        var logsAfter = jdbcTemplate.queryForList("""
                SELECT *
                FROM admin_content_moderation_logs
                WHERE actor_member_id = ?
                ORDER BY id ASC
                """, admin.getId());

        // hidden_at뿐 아니라 본문, 수정 시각 등 모든 컬럼을 비교한다.
        assertThat(postAfter).isEqualTo(postBefore);
        assertThat(commentAfter).isEqualTo(commentBefore);

        // 이력 개수뿐 아니라 기존 이력의 내용까지 유지되어야 한다.
        assertThat(logsAfter).isEqualTo(logsBefore);

        // 입력 오류 때문에 관리자 세션을 무효화하면 안 된다.
        assertThat(session.isInvalid()).isFalse();

        // 실제 보호된 API 요청에도 같은 세션을 사용할 수 있어야 한다.
        expectOk(mockMvc.perform(
                get(PATH).session(session)
        ));
    }

    @Test
    void 기존_자유입력_기록은_LEGACY와_null_상세로_조회된다()
            throws Exception {
        String originalReason = "기존에 자유롭게 작성한 처리 사유";

        ContentModerationLog stored = logRepository.saveAndFlush(
                ContentModerationLog.forPost(
                        admin.getId(),
                        postId,
                        ModerationAction.HIDE,
                        originalReason,
                        Instant.parse("2026-11-01T03:00:00Z")
                )
        );

        MockHttpSession session = login(admin.getEmail());

        MvcResult result = expectOk(mockMvc.perform(
                get(PATH)
                        .session(session)
                        .param("targetType", "POST")
        ))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].logId")
                        .value(is(stored.getId()), Long.class))
                .andExpect(jsonPath("$.items[0].reasonType")
                        .value("LEGACY"))
                .andExpect(jsonPath("$.items[0].reason")
                        .value(originalReason))
                .andReturn();

        JsonNode item = readJson(result).path("items").get(0);

        // 필드 누락과 명시적 null을 구분해서 확인한다.
        assertThat(item.has("reasonDetail")).isTrue();
        assertThat(item.path("reasonDetail").isNull()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "COMMENT"})
    void 실제_운영_조치와_LEGACY_기록을_사유별로_조회한다(
            String targetType
    ) throws Exception {
        MockHttpSession session = login(admin.getEmail());
        JsonNode csrf = getCsrf(session);

        boolean postTarget = targetType.equals("POST");
        Long targetId = postTarget ? postId : commentId;

        String targetPath = postTarget
                ? "/api/v1/admin/posts/" + postId
                : "/api/v1/admin/posts/" + postId
                  + "/comments/" + commentId;

        // 실제 API로 숨김·복원을 실행한다.
        moderate(
                session,
                csrf,
                targetPath + "/hide",
                ModerationReasonType.SPAM_ADVERTISING,
                "광고 링크 반복"
        );

        moderate(
                session,
                csrf,
                targetPath + "/restore",
                ModerationReasonType.NO_VIOLATION,
                null
        );

        // LEGACY는 신규 조치 API로 만들 수 없으므로
        // 기존 자유입력 팩터리로 조회용 데이터를 준비한다.
        ContentModerationLog legacy = postTarget
                ? ContentModerationLog.forPost(
                admin.getId(),
                postId,
                ModerationAction.HIDE,
                "기존 자유입력 사유",
                Instant.parse("2026-11-01T03:00:00Z")
        )
                : ContentModerationLog.forComment(
                admin.getId(),
                commentId,
                ModerationAction.HIDE,
                "기존 자유입력 사유",
                Instant.parse("2026-11-01T03:00:00Z")
        );

        logRepository.saveAndFlush(legacy);

        for (ModerationReasonType reasonType : List.of(
                ModerationReasonType.SPAM_ADVERTISING,
                ModerationReasonType.NO_VIOLATION,
                ModerationReasonType.LEGACY
        )) {
            // 이번 테스트 관리자가 해당 유형으로 남긴 기록은 각각 한 건이다.
            Long expectedLogId = jdbcTemplate.queryForObject(
                    """
                            SELECT id
                            FROM admin_content_moderation_logs
                            WHERE actor_member_id = ?
                              AND reason_type = ?
                            """,
                    Long.class,
                    admin.getId(),
                    reasonType.name()
            );

            ContentModerationLog stored = logRepository
                    .findById(expectedLogId)
                    .orElseThrow();

            MvcResult result = expectOk(mockMvc.perform(
                    get(PATH)
                            .session(session)
                            .param("targetType", targetType)
                            .param("reasonType", reasonType.name())
            ))
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.totalPages").value(1))
                    .andExpect(jsonPath("$.hasNext").value(false))
                    .andExpect(jsonPath("$.hasPrevious").value(false))
                    .andExpect(jsonPath("$.items[0].logId")
                            .value(is(expectedLogId), Long.class))
                    .andExpect(jsonPath("$.items[0].targetType")
                            .value(targetType))
                    .andExpect(jsonPath("$.items[0].targetId")
                            .value(is(targetId), Long.class))
                    .andExpect(jsonPath("$.items[0].reasonType")
                            .value(reasonType.name()))
                    .andExpect(jsonPath("$.items[0].action")
                            .value(stored.getAction().name()))
                    .andExpect(jsonPath("$.items[0].reason")
                            .value(stored.getReason()))
                    .andReturn();

            JsonNode item = readJson(result).path("items").get(0);

            // 상세 사유는 필드 누락과 명시적 null을 구분한다.
            assertThat(item.has("reasonDetail")).isTrue();

            if (stored.getReasonDetail() == null) {
                assertThat(item.path("reasonDetail").isNull()).isTrue();
            } else {
                assertThat(item.path("reasonDetail").asString())
                        .isEqualTo(stored.getReasonDetail());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 실제_세션에서_사유_조건이_없으면_모든_사유를_조회한다(
            boolean sendEmptyParameter
    ) throws Exception {
        MockHttpSession session = login(admin.getEmail());

        Long legacyId = savePostLog(
                ModerationAction.HIDE,
                Instant.parse("2026-11-01T03:00:00Z")
        );

        ContentModerationLog typed = logRepository.saveAndFlush(
                ContentModerationLog.forPost(
                        admin.getId(),
                        postId,
                        ModerationAction.HIDE,
                        ModerationReasonType.SPAM_ADVERTISING,
                        null,
                        Instant.parse("2026-11-02T03:00:00Z")
                )
        );

        MockHttpServletRequestBuilder request = get(PATH)
                .session(session)
                .param("targetType", "POST");

        if (sendEmptyParameter) {
            request.param("reasonType", "");
        }

        expectOk(mockMvc.perform(request))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].logId")
                        .value(is(typed.getId()), Long.class))
                .andExpect(jsonPath("$.items[0].reasonType")
                        .value("SPAM_ADVERTISING"))
                .andExpect(jsonPath("$.items[1].logId")
                        .value(is(legacyId), Long.class))
                .andExpect(jsonPath("$.items[1].reasonType")
                        .value("LEGACY"));
    }

    @Test
    void 잘못된_사유_검색값을_보내도_관리자_세션은_유지된다()
            throws Exception {
        MockHttpSession session = login(admin.getEmail());

        mockMvc.perform(
                        get(PATH)
                                .session(session)
                                .param("reasonType", "UNKNOWN")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_PARAMETER"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        assertThat(session.isInvalid()).isFalse();

        // 같은 세션으로 정상 조회가 가능해야 한다.
        expectOk(mockMvc.perform(
                get(PATH)
                        .session(session)
                        .param("reasonType", "LEGACY")
        ))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    private ResultActions readFilteredPage(
            MockHttpSession session,
            int page
    ) throws Exception {
        return mockMvc.perform(
                get(PATH)
                        .session(session)
                        .param("targetType", "POST")
                        .param("action", "HIDE")
                        .param("startDate", "2026-11-04")
                        .param("endDate", "2026-11-04")
                        .param("page", Integer.toString(page))
                        .param("size", "1")
        );
    }

    private void moderate(
            MockHttpSession session,
            JsonNode csrf,
            String path,
            ModerationReasonType reasonType,
            String reasonDetail
    ) throws Exception {

        Map<String, Object> body = new HashMap<>();
        body.put("reasonType", reasonType.name());

        // 상세가 없으면 필드를 생략한다.
        // 명시적 null의 역직렬화는 컨트롤러 테스트에서 검증했다.
        if (reasonDetail != null) {
            body.put("reasonDetail", reasonDetail);
        }

        mockMvc.perform(
                        post(path)
                                .session(session)
                                .header(
                                        csrf.path("headerName").asString(),
                                        csrf.path("token").asString()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body))
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(jsonPath("$.changed").value(true));
    }

    private Long savePostLog(ModerationAction action, Instant at) {
        return logRepository.saveAndFlush(
                ContentModerationLog.forPost(
                        admin.getId(),
                        postId,
                        action,
                        "날짜 필터 검증",
                        at
                )
        ).getId();
    }

    private Member saveMember(
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

    private MockHttpSession login(String email) throws Exception {
        MvcResult csrfResult = mockMvc.perform(
                        get("/api/v1/auth/csrf")
                )
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession csrfSession =
                (MockHttpSession) csrfResult.getRequest().getSession(false);

        assertThat(csrfSession).isNotNull();

        JsonNode csrf = readJson(csrfResult);

        assertThat(csrf.path("headerName").asString())
                .isEqualTo("X-CSRF-TOKEN");
        assertThat(csrf.path("token").asString()).isNotBlank();

        MvcResult loginResult = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .session(csrfSession)
                                .header(
                                        csrf.path("headerName").asString(),
                                        csrf.path("token").asString()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        Map.of(
                                                "email", email,
                                                "password", PASSWORD
                                        )
                                ))
                )
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session =
                (MockHttpSession) loginResult.getRequest().getSession(false);

        assertThat(session).isNotNull();
        return session;
    }

    private JsonNode getCsrf(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/v1/auth/csrf").session(session)
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode csrf = readJson(result);

        assertThat(csrf.path("headerName").asString())
                .isEqualTo("X-CSRF-TOKEN");
        assertThat(csrf.path("token").asString()).isNotBlank();

        return csrf;
    }

    private JsonNode readJson(MvcResult result) {
        return objectMapper.readTree(
                result.getResponse().getContentAsByteArray()
        );
    }

    private ResultActions expectOk(ResultActions response)
            throws Exception {
        return response
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ));
    }

    private ResultActions readSortedFilteredPage(
            MockHttpSession session,
            int page,
            String sort
    ) throws Exception {
        MockHttpServletRequestBuilder request = get(PATH)
                .session(session)
                .param("targetType", "POST")
                .param("action", "HIDE")
                .param("startDate", "2026-11-04")
                .param("endDate", "2026-11-04")
                .param("page", Integer.toString(page))
                .param("size", "1");

        // DEFAULT는 테스트에서만 사용하는 표시다.
        // 실제 요청에서는 sort 파라미터를 생략한다.
        if (!sort.equals("DEFAULT")) {
            request.param("sort", sort);
        }

        return mockMvc.perform(request);
    }

    private static Stream<Arguments> invalidReasonCases() {
        return Stream.of("POST", "COMMENT")
                .flatMap(targetType ->
                        Stream.of("hide", "restore")
                                .flatMap(action ->
                                        Stream.of(
                                                "MISSING_TYPE",
                                                "NULL_TYPE",
                                                "LEGACY",
                                                "WRONG_ACTION_TYPE",
                                                "OTHER_MISSING_DETAIL",
                                                "OTHER_NULL_DETAIL",
                                                "OTHER_EMPTY_DETAIL",
                                                "OTHER_BLANK_DETAIL",
                                                "OTHER_TOO_LONG_KOREAN",
                                                "OTHER_TOO_LONG_EMOJI",
                                                "PREDEFINED_TOO_LONG"
                                        ).map(invalidCase ->
                                                Arguments.of(
                                                        targetType,
                                                        action,
                                                        invalidCase
                                                )
                                        )
                                )
                );
    }

    private Map<String, Object> invalidReasonBody(
            String action,
            String invalidCase
    ) {
        // 명시적 null도 요청해야 하므로 HashMap을 사용한다.
        Map<String, Object> body = new HashMap<>();

        body.put("reasonType", ModerationReasonType.OTHER.name());
        body.put("reasonDetail", "유효한 상세 사유");

        switch (invalidCase) {
            case "MISSING_TYPE" -> body.remove("reasonType");

            case "NULL_TYPE" -> body.put("reasonType", null);

            case "LEGACY" -> body.put(
                    "reasonType",
                    ModerationReasonType.LEGACY.name()
            );

            case "WRONG_ACTION_TYPE" -> {
                // 숨김에는 복원 전용 유형을,
                // 복원에는 숨김 전용 유형을 전달한다.
                ModerationReasonType wrongType =
                        "hide".equals(action)
                                ? ModerationReasonType.NO_VIOLATION
                                : ModerationReasonType.SPAM_ADVERTISING;

                body.put("reasonType", wrongType.name());
            }

            case "OTHER_MISSING_DETAIL" -> body.remove("reasonDetail");

            case "OTHER_NULL_DETAIL" -> body.put("reasonDetail", null);

            case "OTHER_EMPTY_DETAIL" -> body.put("reasonDetail", "");

            case "OTHER_BLANK_DETAIL" -> body.put("reasonDetail", " \t\n\u3000 ");

            case "OTHER_TOO_LONG_KOREAN" -> body.put(
                    "reasonDetail",
                    "  " + "가".repeat(501) + "  "
            );

            case "OTHER_TOO_LONG_EMOJI" -> body.put(
                    "reasonDetail",
                    "  " + "😀".repeat(501) + "  "
            );

            case "PREDEFINED_TOO_LONG" -> {
                ModerationReasonType allowedType =
                        "hide".equals(action)
                                ? ModerationReasonType.SPAM_ADVERTISING
                                : ModerationReasonType.NO_VIOLATION;

                body.put("reasonType", allowedType.name());
                body.put("reasonDetail", "가".repeat(501));
            }

            default -> throw new IllegalArgumentException(
                    "알 수 없는 테스트 사례: " + invalidCase
            );
        }

        return body;
    }
}
