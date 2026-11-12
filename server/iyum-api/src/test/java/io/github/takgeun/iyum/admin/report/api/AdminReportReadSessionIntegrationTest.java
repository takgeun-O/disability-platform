package io.github.takgeun.iyum.admin.report.api;

import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.report.domain.ContentReport;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import io.github.takgeun.iyum.community.report.infrastructure.ContentReportRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
@Execution(ExecutionMode.SAME_THREAD)
class AdminReportReadSessionIntegrationTest {

    private static final String LIST_PATH = "/api/v1/admin/reports";

    private static final String PASSWORD = "AdminReportTest123!";

    private static final Instant NOW =
            Instant.parse("2026-11-12T03:00:00Z");

    private static final Instant HIDDEN_AT = NOW.plusSeconds(60);
    private static final Instant DELETED_AT = NOW.plusSeconds(120);

    private static final String TITLE = "신고 당시 제목";

    private static final String POST_BODY =
            "가".repeat(119) + "😀나\n다음 줄";

    private static final String COMMENT_BODY =
            "첫 댓글\n\t둘째 줄";

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
    ContentReportRepository reportRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    private TransactionTemplate transactions;

    private Member admin;
    private Member author;
    private Member reporter;

    private Long postId;
    private Long commentId;

    private String passwordHash;

    /*
     * 테스트 클래스 전체에 @Transactional을 붙이지 않는다.
     *
     * 준비 데이터와 변경 사항을 커밋한 다음 HTTP 요청에서
     * 실제 DB 상태를 다시 읽도록 한다.
     *
     * 이 클래스 전용 PostgreSQL 컨테이너를 사용하며,
     * 각 테스트 시작 시 신고 데이터만 정리한다.
     * 회원과 콘텐츠는 고유한 식별자를 사용한다.
     */
    @BeforeEach
    void setUp() {
        transactions = new TransactionTemplate(transactionManager);

        reportRepository.deleteAllInBatch();

        passwordHash = passwordEncoder.encode(PASSWORD);

        admin = saveMember("관리자");
        author = saveMember("작성자");
        reporter = saveMember("신고자");

        int updated = jdbcTemplate.update("""
                UPDATE members
                   SET role = 'ADMIN',
                       credential_version = 3
                 WHERE id = ?
                """,
                admin.getId()
        );

        assertThat(updated).isEqualTo(1);

        postId = createPost(TITLE, POST_BODY);
        commentId = createComment(postId, COMMENT_BODY);
    }

    @Test
    void 실제_신고_접수_후_관리자_세션으로_목록과_상세를_조회한다()
            throws Exception {
        MockHttpSession reporterSession = login(reporter.getEmail());

        Long postReportId = submitReport(
                reporterSession,
                "/api/v1/community/posts/" + postId + "/reports"
        );

        Long commentReportId = submitReport(
                reporterSession,
                "/api/v1/community/posts/" + postId
                        + "/comments/" + commentId + "/reports"
        );

        MockHttpSession adminSession = login(admin.getEmail());

        // 임의의 요청 파라미터로 세션의 관리자 정보를 덮어쓸 수 없다.
        JsonNode list = readList(
                adminSession,
                "actorMemberId", reporter.getId().toString(),
                "credentialVersion", "999"
        );

        assertThat(reportIds(list))
                .containsExactlyInAnyOrder(postReportId, commentReportId);

        assertThat(list.path("page").asInt()).isZero();
        assertThat(list.path("size").asInt()).isEqualTo(20);
        assertThat(list.path("totalElements").asLong()).isEqualTo(2);
        assertThat(list.path("totalPages").asInt()).isEqualTo(1);
        assertThat(list.path("hasNext").asBoolean()).isFalse();
        assertThat(list.path("hasPrevious").asBoolean()).isFalse();

        JsonNode postItem = findItem(list, postReportId);
        JsonNode commentItem = findItem(list, commentReportId);

        assertThat(postItem.path("targetType").asString()).isEqualTo("POST");
        assertThat(postItem.path("targetId").asLong()).isEqualTo(postId);
        assertThat(postItem.path("status").asString()).isEqualTo("RECEIVED");
        assertThat(postItem.path("reasonType").asString()).isEqualTo("OTHER");
        assertThat(postItem.path("titleSnapshot").asString()).isEqualTo(TITLE);

        assertThat(postItem.path("contentSnapshotPreview").asString())
                .isEqualTo("가".repeat(119) + "😀…");

        assertThat(postItem.path("currentAuthor").path("memberId").asLong())
                .isEqualTo(author.getId());
        assertThat(postItem.path("currentAuthor").path("nickname").asString())
                .isEqualTo(author.getNickname());

        assertThat(postItem.has("contentSnapshot")).isFalse();
        assertThat(postItem.has("reporterMemberId")).isFalse();

        assertThat(commentItem.path("targetType").asString())
                .isEqualTo("COMMENT");
        assertThat(commentItem.path("targetId").asLong())
                .isEqualTo(commentId);
        assertThat(commentItem.has("titleSnapshot")).isTrue();
        assertThat(commentItem.path("titleSnapshot").isNull()).isTrue();
        assertThat(commentItem.path("contentSnapshotPreview").asString())
                .isEqualTo("첫 댓글 둘째 줄");

        JsonNode postDetail = readDetail(adminSession, postReportId);
        JsonNode commentDetail = readDetail(adminSession, commentReportId);

        assertThat(postDetail.path("reporterMemberId").asLong())
                .isEqualTo(reporter.getId());
        assertThat(postDetail.path("reasonDetail").asString())
                .isEqualTo("검토 요청");
        assertThat(postDetail.path("contentSnapshot").asString())
                .isEqualTo(POST_BODY);
        assertThat(postDetail.path("currentTarget").path("content").asString())
                .isEqualTo(POST_BODY);

        assertThat(postDetail.path("currentTarget").has("parentPostId"))
                .isTrue();
        assertThat(postDetail.path("currentTarget").path("parentPostId").isNull())
                .isTrue();

        assertThat(commentDetail.path("contentSnapshot").asString())
                .isEqualTo(COMMENT_BODY);
        assertThat(commentDetail.path("currentTarget").path("targetId").asLong())
                .isEqualTo(commentId);
        assertThat(commentDetail.path("currentTarget").path("parentPostId").asLong())
                .isEqualTo(postId);

        assertThat(commentDetail.path("currentTarget").has("title")).isTrue();
        assertThat(commentDetail.path("currentTarget").path("title").isNull())
                .isTrue();

        // 신고 접수와 관리자 조회만으로 콘텐츠를 숨기지 않는다.
        for (JsonNode detail : List.of(postDetail, commentDetail)) {
            JsonNode current = detail.path("currentTarget");

            assertThat(current.has("hiddenAt")).isTrue();
            assertThat(current.path("hiddenAt").isNull()).isTrue();
            assertThat(current.has("deletedAt")).isTrue();
            assertThat(current.path("deletedAt").isNull()).isTrue();
        }

        // 조회 과정에서 신고가 변경되거나 추가되지 않는다.
        assertThat(reportRepository.count()).isEqualTo(2);
        assertThat(reportRepository.findById(postReportId)
                .orElseThrow().getStatus())
                .isEqualTo(ContentReportStatus.RECEIVED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LATEST", "OLDEST"})
    void 필터_정렬_페이지와_전체_개수가_실제_DB에_반영된다(
            String sort
    ) throws Exception {
        // 같은 접수 시각에서는 ID가 정렬 기준이 된다.
        Long newerFirst = fixture(
                true,
                ContentReportStatus.RECEIVED,
                ContentReportReasonType.SPAM_ADVERTISING,
                NOW.plusSeconds(10)
        );
        Long newerSecond = fixture(
                true,
                ContentReportStatus.RECEIVED,
                ContentReportReasonType.SPAM_ADVERTISING,
                NOW.plusSeconds(10)
        );

        // ID가 더 커도 접수 시각이 오래되면 시간 순서를 따른다.
        Long older = fixture(
                true,
                ContentReportStatus.RECEIVED,
                ContentReportReasonType.SPAM_ADVERTISING,
                NOW
        );

        // 각 조건 중 하나씩만 불일치하는 데이터.
        fixture(
                false,
                ContentReportStatus.RECEIVED,
                ContentReportReasonType.SPAM_ADVERTISING,
                NOW
        );
        fixture(
                true,
                ContentReportStatus.RESOLVED,
                ContentReportReasonType.SPAM_ADVERTISING,
                NOW
        );
        fixture(
                true,
                ContentReportStatus.RECEIVED,
                ContentReportReasonType.ABUSE_HATE,
                NOW
        );
        fixture(
                true,
                ContentReportStatus.RECEIVED,
                ContentReportReasonType.SPAM_ADVERTISING,
                Instant.parse("2026-11-11T14:59:59Z")
        );
        fixture(
                true,
                ContentReportStatus.RECEIVED,
                ContentReportReasonType.SPAM_ADVERTISING,
                Instant.parse("2026-11-12T15:00:00Z")
        );

        MockHttpSession session = login(admin.getEmail());

        JsonNode firstPage = readFilteredPage(session, sort, 0);
        JsonNode secondPage = readFilteredPage(session, sort, 1);
        JsonNode outsidePage = readFilteredPage(session, sort, 2);

        List<Long> expected = sort.equals("LATEST")
                ? List.of(newerSecond, newerFirst, older)
                : List.of(older, newerFirst, newerSecond);

        assertThat(reportIds(firstPage))
                .containsExactlyElementsOf(expected.subList(0, 2));
        assertThat(reportIds(secondPage))
                .containsExactlyElementsOf(expected.subList(2, 3));
        assertThat(reportIds(outsidePage)).isEmpty();

        for (JsonNode page : List.of(firstPage, secondPage, outsidePage)) {
            assertThat(page.path("totalElements").asLong()).isEqualTo(3);
            assertThat(page.path("totalPages").asInt()).isEqualTo(2);
            assertThat(page.path("size").asInt()).isEqualTo(2);
        }

        assertThat(firstPage.path("hasNext").asBoolean()).isTrue();
        assertThat(firstPage.path("hasPrevious").asBoolean()).isFalse();
        assertThat(secondPage.path("hasNext").asBoolean()).isFalse();
        assertThat(secondPage.path("hasPrevious").asBoolean()).isTrue();
        assertThat(outsidePage.path("page").asInt()).isEqualTo(2);
        assertThat(outsidePage.path("hasNext").asBoolean()).isFalse();
    }

    @Test
    void 한국_날짜의_시작을_포함하고_다음날_시작을_제외한다()
            throws Exception {
        Instant start = Instant.parse("2026-11-11T15:00:00Z");
        Instant end = Instant.parse("2026-11-12T15:00:00Z");

        fixture(
                true,
                ContentReportStatus.RECEIVED,
                ContentReportReasonType.SPAM_ADVERTISING,
                start.minusNanos(1_000)
        );
        Long atStart = fixture(
                true,
                ContentReportStatus.RECEIVED,
                ContentReportReasonType.SPAM_ADVERTISING,
                start
        );
        Long beforeEnd = fixture(
                false,
                ContentReportStatus.RECEIVED,
                ContentReportReasonType.SPAM_ADVERTISING,
                end.minusNanos(1_000)
        );
        fixture(
                false,
                ContentReportStatus.RECEIVED,
                ContentReportReasonType.SPAM_ADVERTISING,
                end
        );

        JsonNode result = readList(
                login(admin.getEmail()),
                "startDate", "2026-11-12",
                "endDate", "2026-11-12"
        );

        assertThat(reportIds(result)).containsExactly(beforeEnd, atStart);
        assertThat(result.path("totalElements").asLong()).isEqualTo(2);
    }

    @Test
    void 상태를_생략하면_모든_상태를_조회하고_없는_조합은_빈_결과다()
            throws Exception {
        Long received = fixture(
                true,
                ContentReportStatus.RECEIVED,
                ContentReportReasonType.SPAM_ADVERTISING,
                NOW
        );
        Long resolved = fixture(
                false,
                ContentReportStatus.RESOLVED,
                ContentReportReasonType.ABUSE_HATE,
                NOW
        );
        Long dismissed = fixture(
                true,
                ContentReportStatus.DISMISSED,
                ContentReportReasonType.OTHER,
                NOW
        );

        MockHttpSession session = login(admin.getEmail());

        JsonNode all = readList(session);

        assertThat(reportIds(all))
                .containsExactly(dismissed, resolved, received);
        assertThat(all.path("totalElements").asLong()).isEqualTo(3);

        JsonNode empty = readList(
                session,
                "targetType", "COMMENT",
                "status", "DISMISSED",
                "reasonType", "OTHER"
        );

        assertThat(reportIds(empty)).isEmpty();
        assertThat(empty.path("totalElements").asLong()).isZero();
        assertThat(empty.path("totalPages").asInt()).isZero();
        assertThat(empty.path("hasNext").asBoolean()).isFalse();
    }

    @Test
    void 원문_변경과_삭제와_탈퇴_후에도_스냅샷과_현재_정보를_구분한다()
            throws Exception {
        Long postReportId = saveReport(
                true,
                postId,
                ContentReportStatus.RECEIVED,
                ContentReportReasonType.SPAM_ADVERTISING,
                NOW
        );
        Long commentReportId = saveReport(
                false,
                commentId,
                ContentReportStatus.RECEIVED,
                ContentReportReasonType.ABUSE_HATE,
                NOW
        );

        MockHttpSession session = login(admin.getEmail());

        transactions.executeWithoutResult(transaction -> {
            Post post = entityManager.find(Post.class, postId);
            Comment comment = entityManager.find(Comment.class, commentId);

            post.update(
                    PostType.GENERAL,
                    PostTopic.DAILY,
                    "수정된 제목",
                    "수정된 게시글\n본문"
            );
            comment.update("수정된 댓글\n본문", NOW.plusSeconds(30));

            post.hide(HIDDEN_AT);
            comment.hide(HIDDEN_AT);

            post.delete(DELETED_AT);
            comment.delete(DELETED_AT);

            entityManager.find(Member.class, author.getId())
                    .withdraw(NOW.plusSeconds(180));
            entityManager.find(Member.class, reporter.getId())
                    .withdraw(NOW.plusSeconds(180));
        });

        JsonNode list = readList(session);

        assertThat(reportIds(list))
                .containsExactly(commentReportId, postReportId);
        assertThat(list.path("totalElements").asLong()).isEqualTo(2);

        JsonNode postItem = findItem(list, postReportId);
        assertThat(postItem.path("titleSnapshot").asString()).isEqualTo(TITLE);
        assertThat(postItem.path("contentSnapshotPreview").asString())
                .isEqualTo("가".repeat(119) + "😀…");

        for (JsonNode item : list.path("items")) {
            assertThat(item.path("currentAuthor").path("memberId").asLong())
                    .isEqualTo(author.getId());
            assertThat(item.path("currentAuthor").path("nickname").asString())
                    .isEqualTo("탈퇴한 회원");
            assertThat(item.path("currentAuthor").path("withdrawn").asBoolean())
                    .isTrue();
        }

        JsonNode postDetail = readDetail(session, postReportId);
        JsonNode commentDetail = readDetail(session, commentReportId);

        assertThat(postDetail.path("titleSnapshot").asString()).isEqualTo(TITLE);
        assertThat(postDetail.path("contentSnapshot").asString())
                .isEqualTo(POST_BODY);
        assertThat(postDetail.path("currentTarget").path("title").asString())
                .isEqualTo("수정된 제목");
        assertThat(postDetail.path("currentTarget").path("content").asString())
                .isEqualTo("수정된 게시글\n본문");

        assertThat(commentDetail.path("contentSnapshot").asString())
                .isEqualTo(COMMENT_BODY);
        assertThat(commentDetail.path("currentTarget").path("content").asString())
                .isEqualTo("수정된 댓글\n본문");
        assertThat(commentDetail.path("currentTarget").path("parentPostId").asLong())
                .isEqualTo(postId);

        for (JsonNode detail : List.of(postDetail, commentDetail)) {
            assertThat(detail.path("reporterMemberId").asLong())
                    .isEqualTo(reporter.getId());
            assertThat(detail.path("status").asString()).isEqualTo("RECEIVED");
            assertThat(detail.has("reasonDetail")).isTrue();
            assertThat(detail.path("reasonDetail").isNull()).isTrue();

            JsonNode current = detail.path("currentTarget");

            assertThat(current.path("hiddenAt").asString())
                    .isEqualTo(HIDDEN_AT.toString());
            assertThat(current.path("deletedAt").asString())
                    .isEqualTo(DELETED_AT.toString());
        }

        assertThat(reportRepository.findById(postReportId)
                .orElseThrow().getContentSnapshot())
                .isEqualTo(POST_BODY);

        assertThat(reportRepository.findById(commentReportId)
                .orElseThrow().getContentSnapshot())
                .isEqualTo(COMMENT_BODY);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MAX_VALUE})
    void 없는_신고는_실제_서비스에서_404로_반환한다(Long reportId)
            throws Exception {
        MockHttpSession session = login(admin.getEmail());

        expectError(
                mockMvc.perform(
                        get(LIST_PATH + "/" + reportId)
                                .session(session)
                ),
                404,
                "REPORT_NOT_FOUND"
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 비로그인은_실제_보안_필터에서_401로_차단한다(boolean detail)
            throws Exception {
        Long reportId = fixture(
                true,
                ContentReportStatus.RECEIVED,
                ContentReportReasonType.SPAM_ADVERTISING,
                NOW
        );

        expectError(
                mockMvc.perform(get(targetPath(detail, reportId))),
                401,
                "AUTHENTICATION_REQUIRED"
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 실제_일반_회원_세션은_관리자_조회가_403이다(boolean detail)
            throws Exception {
        Long reportId = fixture(
                true,
                ContentReportStatus.RECEIVED,
                ContentReportReasonType.SPAM_ADVERTISING,
                NOW
        );

        MockHttpSession session = login(reporter.getEmail());

        expectError(
                mockMvc.perform(
                        get(targetPath(detail, reportId))
                                .session(session)
                                .queryParam(
                                        "actorMemberId",
                                        admin.getId().toString()
                                )
                ),
                403,
                "ACCESS_DENIED"
        );

        // 접근 거부가 정상 일반 회원의 세션을 만료시키지는 않는다.
        assertThat(session.isInvalid()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "ROLE, false",
            "ROLE, true",
            "VERSION, false",
            "VERSION, true",
            "STATUS, false",
            "STATUS, true",
            "WITHDRAWAL, false",
            "WITHDRAWAL, true"
    })
    void 로그인_후_관리자_정보가_변경되면_기존_세션을_차단한다(
            String change,
            boolean detail
    ) throws Exception {
        Long reportId = fixture(
                true,
                ContentReportStatus.RECEIVED,
                ContentReportReasonType.SPAM_ADVERTISING,
                NOW
        );

        MockHttpSession session = login(admin.getEmail());
        String path = targetPath(detail, reportId);

        // 변경 전에는 동일한 세션으로 조회할 수 있다.
        expectOk(mockMvc.perform(get(path).session(session)));

        switch (change) {
            case "ROLE" -> assertThat(jdbcTemplate.update("""
                    UPDATE members SET role = 'USER' WHERE id = ?
                    """,
                    admin.getId()
            )).isEqualTo(1);

            case "VERSION" -> assertThat(jdbcTemplate.update("""
                    UPDATE members
                       SET credential_version = credential_version + 1
                     WHERE id = ?
                    """,
                    admin.getId()
            )).isEqualTo(1);

            case "STATUS" -> assertThat(jdbcTemplate.update("""
                    UPDATE members SET status = 'SUSPENDED' WHERE id = ?
                    """,
                    admin.getId()
            )).isEqualTo(1);

            case "WITHDRAWAL" ->
                    transactions.executeWithoutResult(transaction ->
                            entityManager.find(Member.class, admin.getId())
                                    .withdraw(NOW.plusSeconds(180))
                    );

            default -> throw new IllegalArgumentException(change);
        }

        expectError(
                mockMvc.perform(get(path).session(session)),
                401,
                "AUTHENTICATION_REQUIRED"
        );

        assertThat(session.isInvalid()).isTrue();
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

        JsonNode csrfBody = body(csrfResult);
        String headerName = csrfBody.path("headerName").asString();
        String csrfToken = csrfBody.path("token").asString();

        assertThat(headerName).isEqualTo("X-CSRF-TOKEN");
        assertThat(csrfToken).isNotBlank();

        MvcResult loginResult = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .session(csrfSession)
                                .header(headerName, csrfToken)
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

        // 로그인 과정에서 세션이 교체될 수 있어 결과에서 다시 받는다.
        MockHttpSession session =
                (MockHttpSession) loginResult.getRequest().getSession(false);

        assertThat(session).isNotNull();
        assertThat(session.isInvalid()).isFalse();

        return session;
    }

    private Long submitReport(
            MockHttpSession session,
            String path
    ) throws Exception {
        // 로그인 이후의 세션에서 CSRF 토큰을 다시 조회한다.
        MvcResult csrfResult = mockMvc.perform(
                        get("/api/v1/auth/csrf").session(session)
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode csrf = body(csrfResult);

        MvcResult result = mockMvc.perform(
                        post(path)
                                .session(session)
                                .header(
                                        csrf.path("headerName").asString(),
                                        csrf.path("token").asString()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        Map.of(
                                                "reasonType", "OTHER",
                                                "reasonDetail", "  검토 요청  "
                                        )
                                ))
                )
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andReturn();

        long reportId = body(result).path("reportId").asLong();
        assertThat(reportId).isPositive();

        return reportId;
    }

    private JsonNode readList(
            MockHttpSession session,
            String... parameters
    ) throws Exception {
        if (parameters.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "파라미터 이름과 값을 쌍으로 전달해야 합니다."
            );
        }

        MockHttpServletRequestBuilder request = get(LIST_PATH)
                .session(session)
                .accept(MediaType.APPLICATION_JSON);

        for (int i = 0; i < parameters.length; i += 2) {
            request.queryParam(parameters[i], parameters[i + 1]);
        }

        // GET에는 CSRF 토큰을 전달하지 않는다.
        JsonNode result = body(
                expectOk(mockMvc.perform(request)).andReturn()
        );

        assertThat(result.path("items").isArray()).isTrue();
        assertNoSensitiveFields(result);

        return result;
    }

    private JsonNode readFilteredPage(
            MockHttpSession session,
            String sort,
            int page
    ) throws Exception {
        return readList(
                session,
                "targetType", "POST",
                "status", "RECEIVED",
                "reasonType", "SPAM_ADVERTISING",
                "startDate", "2026-11-12",
                "endDate", "2026-11-12",
                "sort", sort,
                "page", Integer.toString(page),
                "size", "2"
        );
    }

    private JsonNode readDetail(
            MockHttpSession session,
            Long reportId
    ) throws Exception {
        JsonNode result = body(
                expectOk(mockMvc.perform(
                        get(LIST_PATH + "/" + reportId)
                                .session(session)
                                .accept(MediaType.APPLICATION_JSON)
                )).andReturn()
        );

        assertNoSensitiveFields(result);

        return result;
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

    private ResultActions expectError(
            ResultActions response,
            int expectedStatus,
            String expectedCode
    ) throws Exception {
        return response
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.items").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.contentSnapshot").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.currentTarget").doesNotHaveJsonPath())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsByteArray()
        );
    }

    private List<Long> reportIds(JsonNode response) {
        List<Long> ids = new ArrayList<>();

        for (JsonNode item : response.path("items")) {
            ids.add(item.path("reportId").asLong());
        }

        return ids;
    }

    private JsonNode findItem(JsonNode response, Long reportId) {
        for (JsonNode item : response.path("items")) {
            if (item.path("reportId").asLong() == reportId.longValue()) {
                return item;
            }
        }

        throw new AssertionError("응답에서 신고를 찾지 못했습니다: " + reportId);
    }

    private void assertNoSensitiveFields(JsonNode response) throws Exception {
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).doesNotContain(
                "\"email\":",
                "\"password\":",
                "\"passwordHash\":",
                "\"credentialVersion\":",
                "\"reporterEmail\":"
        );
    }

    private String targetPath(boolean detail, Long reportId) {
        return detail ? LIST_PATH + "/" + reportId : LIST_PATH;
    }

    private Member saveMember(String label) {
        String unique = UUID.randomUUID()
                .toString()
                .replace("-", "");

        Member member = Member.createPending(
                unique + "@example.com",
                passwordHash,
                label + unique.substring(0, 10)
        );

        member.activeAfterEmailVerification();

        return memberRepository.saveAndFlush(member);
    }

    private Long createPost(String title, String content) {
        return transactions.execute(transaction -> {
            Post post = Post.create(
                    entityManager.getReference(Member.class, author.getId()),
                    PostType.GENERAL,
                    PostTopic.DAILY,
                    title,
                    content
            );

            entityManager.persist(post);
            entityManager.flush();

            return post.getId();
        });
    }

    private Long createComment(Long parentPostId, String content) {
        return transactions.execute(transaction -> {
            Comment comment = Comment.create(
                    entityManager.getReference(Post.class, parentPostId),
                    entityManager.getReference(Member.class, author.getId()),
                    content
            );

            entityManager.persist(comment);
            entityManager.flush();

            return comment.getId();
        });
    }

    private Long fixture(
            boolean postTarget,
            ContentReportStatus status,
            ContentReportReasonType reasonType,
            Instant createdAt
    ) {
        // 같은 신고자의 동일 대상 중복 신고 제약에 걸리지 않도록
        // 필터 테스트의 각 신고마다 별도 콘텐츠를 준비한다.
        Long parentPostId = createPost("필터 테스트 제목", "필터 테스트 본문");

        Long targetId = postTarget
                ? parentPostId
                : createComment(parentPostId, "필터 테스트 댓글");

        return saveReport(
                postTarget,
                targetId,
                status,
                reasonType,
                createdAt
        );
    }

    private Long saveReport(
            boolean postTarget,
            Long targetId,
            ContentReportStatus status,
            ContentReportReasonType reasonType,
            Instant createdAt
    ) {
        Long reportId = transactions.execute(transaction -> {
            String reasonDetail = reasonType == ContentReportReasonType.OTHER
                    ? "검토 요청"
                    : null;

            ContentReport report;

            if (postTarget) {
                Post post = entityManager.find(Post.class, targetId);

                report = ContentReport.forPost(
                        reporter.getId(),
                        post.getId(),
                        reasonType,
                        reasonDetail,
                        post.getTitle(),
                        post.getContent(),
                        createdAt
                );
            } else {
                Comment comment = entityManager.find(Comment.class, targetId);

                report = ContentReport.forComment(
                        reporter.getId(),
                        comment.getId(),
                        reasonType,
                        reasonDetail,
                        comment.getContent(),
                        createdAt
                );
            }

            entityManager.persist(report);
            entityManager.flush();

            return report.getId();
        });

        assertThat(reportId).isNotNull();

        if (status != ContentReportStatus.RECEIVED) {
            // 신고 처리 API 구현 전, 조회 조건 검증용 상태를 준비한다.
            int updated = jdbcTemplate.update(
                    "UPDATE content_reports SET status = ? WHERE id = ?",
                    status.name(),
                    reportId
            );

            assertThat(updated).isEqualTo(1);
        }

        return reportId;
    }
}