package io.github.takgeun.iyum.admin.content.api;

import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
class AdminPostReadSessionIntegrationTest {

    private static final String LIST_PATH =
            "/api/v1/admin/posts";

    private static final String PASSWORD =
            "AdminReadTest123!";

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-30T10:00:00Z");

    private static final Instant HIDDEN_AT =
            Instant.parse("2026-10-30T11:00:00Z");

    private static final Instant DELETED_AT =
            Instant.parse("2026-10-30T12:00:00Z");

    private static final String ORIGINAL_CONTENT =
            "첫 번째 줄\n\n두 번째 줄";

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
    JdbcTemplate jdbcTemplate;

    private Member admin;
    private Member author;

    private String keyword;

    private Long visiblePostId;
    private Long hiddenPostId;
    private Long deletedPostId;
    private Long hiddenAndDeletedPostId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "");

        keyword = "조회검증" + suffix;

        String passwordHash = passwordEncoder.encode(PASSWORD);

        admin = saveActiveMember(
                "admin-" + suffix + "@example.com",
                "조회관리" + suffix.substring(0, 8),
                passwordHash
        );

        author = saveActiveMember(
                "author-" + suffix + "@example.com",
                "조회작성" + suffix.substring(0, 8),
                passwordHash
        );

        // 테스트 준비용으로 관리자 권한과 인증 버전을 설정한다.
        int updatedRows = jdbcTemplate.update("""
                UPDATE members
                SET role = 'ADMIN',
                    credential_version = 3
                WHERE id = ?
                """,
                admin.getId()
        );

        assertThat(updatedRows).isEqualTo(1);

        visiblePostId = savePost("공개", false, false);
        hiddenPostId = savePost("숨김", true, false);
        deletedPostId = savePost("삭제", false, true);
        hiddenAndDeletedPostId =
                savePost("숨김후삭제", true, true);
    }

    @Test
    void 실제_관리자_로그인으로_목록과_숨김삭제_상세를_조회한다()
            throws Exception {
        MockHttpSession session = login(admin.getEmail());

        // 작성 시각을 동일하게 저장했으므로 ID 내림차순이다.
        expectOk(readList(session, "ALL", 0, 20))
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.totalElements")
                        .value(is(4L), Long.class))
                .andExpect(jsonPath("$.items[0].postId")
                        .value(is(hiddenAndDeletedPostId), Long.class))
                .andExpect(jsonPath("$.items[0].status")
                        .value("DELETED"))
                .andExpect(jsonPath("$.items[1].postId")
                        .value(is(deletedPostId), Long.class))
                .andExpect(jsonPath("$.items[2].postId")
                        .value(is(hiddenPostId), Long.class))
                .andExpect(jsonPath("$.items[2].status")
                        .value("HIDDEN"))
                .andExpect(jsonPath("$.items[3].postId")
                        .value(is(visiblePostId), Long.class))
                .andExpect(jsonPath("$.items[3].status")
                        .value("VISIBLE"))
                .andExpect(jsonPath("$.items[3].contentPreview")
                        .value("첫 번째 줄 두 번째 줄"));

        expectOk(readDetail(session, hiddenPostId))
                .andExpect(jsonPath("$.status").value("HIDDEN"))
                .andExpect(jsonPath("$.content").value(ORIGINAL_CONTENT))
                .andExpect(jsonPath("$.hiddenAt")
                        .value(HIDDEN_AT.toString()))
                .andExpect(jsonPath("$.author.memberId")
                        .value(is(author.getId()), Long.class));

        expectOk(readDetail(session, hiddenAndDeletedPostId))
                .andExpect(jsonPath("$.status").value("DELETED"))
                .andExpect(jsonPath("$.content").value(ORIGINAL_CONTENT))
                .andExpect(jsonPath("$.hiddenAt")
                        .value(HIDDEN_AT.toString()))
                .andExpect(jsonPath("$.deletedAt")
                        .value(DELETED_AT.toString()));

        // 관리자 조회가 가능해도 일반 공개 조회에서는 숨겨져야 한다.
        mockMvc.perform(get(
                        "/api/v1/community/posts/{postId}",
                        hiddenPostId
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    void 상태_검색과_페이지_전체_개수가_실제_DB에서_연결된다()
            throws Exception {
        MockHttpSession session = login(admin.getEmail());

        expectOk(readList(session, "VISIBLE", 0, 20))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].postId")
                        .value(is(visiblePostId), Long.class))
                .andExpect(jsonPath("$.totalElements")
                        .value(is(1L), Long.class));

        expectOk(readList(session, "HIDDEN", 0, 20))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].postId")
                        .value(is(hiddenPostId), Long.class))
                .andExpect(jsonPath("$.totalElements")
                        .value(is(1L), Long.class));

        expectOk(readList(session, "DELETED", 0, 1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].postId")
                        .value(is(hiddenAndDeletedPostId), Long.class))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements")
                        .value(is(2L), Long.class))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.hasPrevious").value(false));

        expectOk(readList(session, "DELETED", 1, 1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].postId")
                        .value(is(deletedPostId), Long.class))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements")
                        .value(is(2L), Long.class))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(true));

        expectOk(readList(session, "DELETED", 2, 1))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements")
                        .value(is(2L), Long.class));
    }

    @Test
    void 검색_결과가_없으면_200이고_상세가_없으면_404다()
            throws Exception {
        MockHttpSession session = login(admin.getEmail());

        expectOk(mockMvc.perform(
                get(LIST_PATH)
                        .session(session)
                        .param("keyword", keyword + "없는검색어")
        ))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements")
                        .value(is(0L), Long.class));

        // 테스트 전용 DB에 존재하지 않는 양수 ID를 사용한다.
        readDetail(session, Long.MAX_VALUE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ));
    }

    @Test
    void 작성자가_탈퇴해도_관리자는_게시글과_탈퇴_표시를_조회한다()
            throws Exception {
        MockHttpSession session = login(admin.getEmail());

        author.withdraw(DELETED_AT);
        memberRepository.saveAndFlush(author);

        expectOk(readList(session, "VISIBLE", 0, 20))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].author.memberId")
                        .value(is(author.getId()), Long.class))
                .andExpect(jsonPath("$.items[0].author.nickname")
                        .value("탈퇴한 회원"))
                .andExpect(jsonPath("$.items[0].author.withdrawn")
                        .value(true));

        expectOk(readDetail(session, hiddenPostId))
                .andExpect(jsonPath("$.content").value(ORIGINAL_CONTENT))
                .andExpect(jsonPath("$.author.nickname")
                        .value("탈퇴한 회원"))
                .andExpect(jsonPath("$.author.withdrawn").value(true))
                .andExpect(jsonPath("$.author.email").doesNotExist())
                .andExpect(jsonPath("$.author.passwordHash").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 실제_일반_회원_세션은_관리자_조회가_403이다(
            boolean detail
    ) throws Exception {
        MockHttpSession session = login(author.getEmail());

        mockMvc.perform(
                        get(targetPath(detail))
                                .session(session)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.items").doesNotExist())
                .andExpect(jsonPath("$.content").doesNotExist());

        // 일반 회원의 유효한 로그인 자체를 무효화할 이유는 없다.
        assertThat(session.isInvalid()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "ROLE, false",
            "ROLE, true",
            "VERSION, false",
            "VERSION, true",
            "STATUS, false",
            "STATUS, true"
    })
    void 로그인_후_권한이나_인증정보가_변경되면_기존_세션을_차단한다(
            String change,
            boolean detail
    ) throws Exception {
        MockHttpSession session = login(admin.getEmail());

        // 변경 전에는 동일한 세션으로 조회할 수 있다.
        expectOk(mockMvc.perform(
                get(targetPath(detail))
                        .session(session)
                        .param("keyword", keyword)
        ));

        int updatedRows = switch (change) {
            case "ROLE" -> jdbcTemplate.update("""
                    UPDATE members
                    SET role = 'USER'
                    WHERE id = ?
                    """,
                    admin.getId()
            );

            case "VERSION" -> jdbcTemplate.update("""
                    UPDATE members
                    SET credential_version = credential_version + 1
                    WHERE id = ?
                    """,
                    admin.getId()
            );

            case "STATUS" -> jdbcTemplate.update("""
                    UPDATE members
                    SET status = 'PENDING'
                    WHERE id = ?
                    """,
                    admin.getId()
            );

            default -> throw new IllegalArgumentException(
                    "지원하지 않는 테스트 조건: " + change
            );
        };

        assertThat(updatedRows).isEqualTo(1);

        mockMvc.perform(
                        get(targetPath(detail))
                                .session(session)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.items").doesNotExist())
                .andExpect(jsonPath("$.content").doesNotExist())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ));

        assertThat(session.isInvalid()).isTrue();

        if (change.equals("ROLE")) {
            // 권한 강등 후 새로 로그인하면 USER 세션이므로 403이다.
            MockHttpSession newSession = login(admin.getEmail());

            mockMvc.perform(
                            get(targetPath(detail))
                                    .session(newSession)
                    )
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code")
                            .value("ACCESS_DENIED"));
        }
    }

    @Test
    void 실제_관리자_세션으로_다중_상태와_검색어를_적용해_페이징한다()
            throws Exception {
        // 상태는 일치하지만 검색어가 다른 게시글을 추가한다.
        // 상태 조건과 검색어 조건이 AND로 적용되는지 확인한다.
        Post unrelatedVisible = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                "검색 대상이 아닌 정상 게시글",
                ORIGINAL_CONTENT
        );
        postRepository.saveAndFlush(unrelatedVisible);

        Post unrelatedHidden = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                "검색 대상이 아닌 숨김 게시글",
                ORIGINAL_CONTENT
        );
        unrelatedHidden.hide(HIDDEN_AT);
        postRepository.saveAndFlush(unrelatedHidden);

        // 실제 CSRF 발급 및 로그인 API를 호출해 얻은 세션이다.
        MockHttpSession session = login(admin.getEmail());

        // 선택 순서를 바꾸거나 값을 반복해도 결과는 같아야 한다.
        String[] statuses = {"HIDDEN", "VISIBLE", "HIDDEN"};

        JsonNode firstPage = readMultipleStatusPosts(
                session, 0, 1, statuses
        );
        JsonNode secondPage = readMultipleStatusPosts(
                session, 1, 1, statuses
        );
        JsonNode outOfRange = readMultipleStatusPosts(
                session, 2, 1, statuses
        );

        // 작성 시각이 같으므로 ID 내림차순이다.
        assertThat(postIds(firstPage))
                .containsExactly(hiddenPostId);
        assertThat(postIds(secondPage))
                .containsExactly(visiblePostId);
        assertThat(postIds(outOfRange)).isEmpty();

        assertThat(firstPage.path("items").path(0)
                .path("status").asString())
                .isEqualTo("HIDDEN");

        assertThat(secondPage.path("items").path(0)
                .path("status").asString())
                .isEqualTo("VISIBLE");

        for (JsonNode page : List.of(
                firstPage,
                secondPage,
                outOfRange
        )) {
            assertThat(page.path("totalElements").asLong())
                    .isEqualTo(2);
            assertThat(page.path("totalPages").asInt())
                    .isEqualTo(2);
            assertThat(page.path("size").asInt())
                    .isEqualTo(1);
        }

        assertThat(firstPage.path("page").asInt()).isZero();
        assertThat(firstPage.path("hasNext").asBoolean()).isTrue();
        assertThat(firstPage.path("hasPrevious").asBoolean()).isFalse();

        assertThat(secondPage.path("page").asInt()).isEqualTo(1);
        assertThat(secondPage.path("hasNext").asBoolean()).isFalse();
        assertThat(secondPage.path("hasPrevious").asBoolean()).isTrue();

        assertThat(outOfRange.path("page").asInt()).isEqualTo(2);
        assertThat(outOfRange.path("hasNext").asBoolean()).isFalse();

        // 세 상태를 모두 선택하면 삭제된 게시글까지 포함한다.
        JsonNode allStates = readMultipleStatusPosts(
                session,
                0,
                20,
                "VISIBLE",
                "HIDDEN",
                "DELETED"
        );

        assertThat(postIds(allStates)).containsExactly(
                hiddenAndDeletedPostId,
                deletedPostId,
                hiddenPostId,
                visiblePostId
        );

        assertThat(allStates.path("totalElements").asLong())
                .isEqualTo(4);

        // 숨김 시각이 남아 있어도 삭제 상태가 우선한다.
        assertThat(allStates.path("items").path(0)
                .path("status").asString())
                .isEqualTo("DELETED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"LATEST", "OLDEST"})
    void 실제_관리자_세션으로_작성일과_정렬을_적용해_페이징한다(
            String sort
    ) throws Exception {
        // 한국 시간 2026-10-30의 시작과 다음 날 시작이다.
        Instant from = Instant.parse("2026-10-29T15:00:00Z");
        Instant to = Instant.parse("2026-10-30T15:00:00Z");

        // 기존 visiblePostId와 hiddenPostId는
        // CREATED_AT(2026-10-30T10:00:00Z)에 작성되어 범위 안에 있다.
        //
        // 아래 게시글은 ID가 더 크지만 작성 시각은 더 오래되게 만든다.
        // ID만으로 정렬하는 오류도 검출할 수 있다.
        Long atStartId = savePost("시작 경계", false, false);
        setPostCreatedAt(atStartId, from);

        // 검색어와 상태가 일치해도 시작일 이전이면 제외한다.
        Long beforeStartId = savePost("시작 직전", false, false);
        setPostCreatedAt(
                beforeStartId,
                from.minusNanos(1_000)
        );

        // 종료일 다음 날 00:00은 포함하지 않는다.
        Long atEndId = savePost("종료 경계", true, false);
        setPostCreatedAt(atEndId, to);

        // 상태와 작성일이 일치해도 검색어가 다르면 제외한다.
        Post unrelated = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                "검색어가 다른 게시글",
                ORIGINAL_CONTENT
        );

        Post savedUnrelated = postRepository.saveAndFlush(unrelated);
        setPostCreatedAt(savedUnrelated.getId(), CREATED_AT);

        // 기존 deletedPostId와 hiddenAndDeletedPostId는
        // 검색어와 작성일이 일치하지만 선택한 상태에 없으므로 제외한다.

        MockHttpSession session = login(admin.getEmail());

        JsonNode firstPage = readDateSortedPosts(
                session, sort, 0, 2
        );

        JsonNode secondPage = readDateSortedPosts(
                session, sort, 1, 2
        );

        JsonNode outsidePage = readDateSortedPosts(
                session, sort, 2, 2
        );

        List<Long> expectedIds = switch (sort) {
            case "LATEST" -> List.of(
                    hiddenPostId,
                    visiblePostId,
                    atStartId
            );
            case "OLDEST" -> List.of(
                    atStartId,
                    visiblePostId,
                    hiddenPostId
            );
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 정렬: " + sort
            );
        };

        assertThat(postIds(firstPage))
                .containsExactlyElementsOf(expectedIds.subList(0, 2));

        assertThat(postIds(secondPage))
                .containsExactly(expectedIds.get(2));

        assertThat(postIds(outsidePage)).isEmpty();

        // 범위 밖 페이지에서도 같은 검색 조건의 전체 개수를 유지한다.
        for (JsonNode page : List.of(
                firstPage,
                secondPage,
                outsidePage
        )) {
            assertThat(page.path("totalElements").asLong())
                    .isEqualTo(3);

            assertThat(page.path("totalPages").asInt())
                    .isEqualTo(2);

            assertThat(page.path("size").asInt())
                    .isEqualTo(2);
        }

        assertThat(firstPage.path("page").asInt()).isZero();
        assertThat(firstPage.path("hasNext").asBoolean()).isTrue();
        assertThat(firstPage.path("hasPrevious").asBoolean()).isFalse();

        assertThat(secondPage.path("page").asInt()).isEqualTo(1);
        assertThat(secondPage.path("hasNext").asBoolean()).isFalse();
        assertThat(secondPage.path("hasPrevious").asBoolean()).isTrue();

        assertThat(outsidePage.path("page").asInt()).isEqualTo(2);
        assertThat(outsidePage.path("hasNext").asBoolean()).isFalse();
        assertThat(outsidePage.path("hasPrevious").asBoolean()).isTrue();
    }

    private ResultActions readList(
            MockHttpSession session,
            String status,
            int page,
            int size
    ) throws Exception {
        return mockMvc.perform(
                get(LIST_PATH)
                        .session(session)
                        .param("status", status)
                        .param("keyword", keyword)
                        .param("page", Integer.toString(page))
                        .param("size", Integer.toString(size))
        );
    }

    private ResultActions readDetail(
            MockHttpSession session,
            Long postId
    ) throws Exception {
        return mockMvc.perform(
                get(LIST_PATH + "/{postId}", postId)
                        .session(session)
        );
    }

    private String targetPath(boolean detail) {
        return detail
                ? LIST_PATH + "/" + hiddenPostId
                : LIST_PATH;
    }

    private ResultActions expectOk(
            ResultActions response
    ) throws Exception {
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

    private MockHttpSession login(String email) throws Exception {
        // 실제 CSRF API로 토큰과 세션을 발급받는다.
        MvcResult csrfResult = mockMvc.perform(
                        get("/api/v1/auth/csrf")
                )
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession csrfSession =
                (MockHttpSession) csrfResult
                        .getRequest()
                        .getSession(false);

        assertThat(csrfSession).isNotNull();

        JsonNode csrfBody = objectMapper.readTree(
                csrfResult.getResponse().getContentAsString()
        );

        String headerName = csrfBody.path("headerName").asString();
        String token = csrfBody.path("token").asString();

        assertThat(headerName).isEqualTo("X-CSRF-TOKEN");
        assertThat(token).isNotBlank();

        MvcResult loginResult = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .session(csrfSession)
                                .header(headerName, token)
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

        MockHttpSession authenticatedSession =
                (MockHttpSession) loginResult
                        .getRequest()
                        .getSession(false);

        assertThat(authenticatedSession).isNotNull();

        // 이후 요청은 GET이므로 새 CSRF 토큰을 발급받을 필요가 없다.
        return authenticatedSession;
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

    private Long savePost(
            String label,
            boolean hidden,
            boolean deleted
    ) {
        Post post = Post.create(
                author,
                PostType.GENERAL,
                PostTopic.DAILY,
                keyword + " " + label,
                ORIGINAL_CONTENT
        );

        if (hidden) {
            post.hide(HIDDEN_AT);
        }

        if (deleted) {
            post.delete(DELETED_AT);
        }

        Post saved = postRepository.saveAndFlush(post);

        // 작성 시각을 동일하게 만들어 ID 정렬까지 확정적으로 검증한다.
        int updatedRows = jdbcTemplate.update("""
                UPDATE community_posts
                SET created_at = ?
                WHERE id = ?
                """,
                Timestamp.from(CREATED_AT),
                saved.getId()
        );

        assertThat(updatedRows).isEqualTo(1);

        return saved.getId();
    }

    private JsonNode readMultipleStatusPosts(
            MockHttpSession session,
            int page,
            int size,
            String... statuses
    ) throws Exception {
        MvcResult result = expectOk(
                mockMvc.perform(
                        get(LIST_PATH)
                                .session(session)
                                .queryParam("statuses", statuses)
                                .queryParam("keyword", "  " + keyword + "  ")
                                .queryParam("page", Integer.toString(page))
                                .queryParam("size", Integer.toString(size))
                )
        ).andReturn();

        return objectMapper.readTree(
                result.getResponse().getContentAsByteArray()
        );
    }

    private List<Long> postIds(JsonNode response) {
        List<Long> ids = new ArrayList<>();

        for (JsonNode item : response.path("items")) {
            ids.add(item.path("postId").asLong());
        }

        return ids;
    }

    /**
     * 테스트 데이터의 작성 시각을 고정한다.
     * JPA Auditing의 현재 시각과 무관하게 날짜 경계와 정렬을 검증한다.
     */
    private void setPostCreatedAt(
            Long postId,
            Instant createdAt
    ) {
        int updatedRows = jdbcTemplate.update("""
            UPDATE community_posts
            SET created_at = ?
            WHERE id = ?
            """,
                Timestamp.from(createdAt),
                postId
        );

        assertThat(updatedRows).isEqualTo(1);
    }

    private JsonNode readDateSortedPosts(
            MockHttpSession session,
            String sort,
            int page,
            int size
    ) throws Exception {
        MvcResult result = expectOk(
                mockMvc.perform(
                        get(LIST_PATH)
                                .session(session)
                                .queryParam(
                                        "statuses",
                                        "VISIBLE",
                                        "HIDDEN"
                                )
                                .queryParam(
                                        "keyword",
                                        "  " + keyword + "  "
                                )
                                .queryParam(
                                        "startDate",
                                        "2026-10-30"
                                )
                                .queryParam(
                                        "endDate",
                                        "2026-10-30"
                                )
                                .queryParam("sort", sort)
                                .queryParam(
                                        "page",
                                        Integer.toString(page)
                                )
                                .queryParam(
                                        "size",
                                        Integer.toString(size)
                                )
                )
        ).andReturn();

        return objectMapper.readTree(
                result.getResponse().getContentAsByteArray()
        );
    }
}