package io.github.takgeun.iyum.admin.content.api;

import io.github.takgeun.iyum.admin.content.application.AdminPostStatus;
import io.github.takgeun.iyum.community.comment.domain.Comment;
import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true"
})
@AutoConfigureMockMvc(addFilters = true)
@Testcontainers
class AdminCommentReadSessionIntegrationTest {

    private static final String PATH =
            "/api/v1/admin/posts/{postId}/comments";

    private static final String PASSWORD =
            "Test-password123!";

    private static final Instant BASE_TIME =
            Instant.parse("2026-10-30T10:00:00Z");

    private static final Instant HIDDEN_AT =
            Instant.parse("2026-10-30T11:00:00Z");

    private static final Instant DELETED_AT =
            Instant.parse("2026-10-30T12:00:00Z");

    private static final String FULL_CONTENT =
            "첫 번째 줄\n\n두 번째 줄\n" + "😀".repeat(201);

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
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Member admin;
    private Member author;
    private Post targetPost;

    @BeforeEach
    void setUp() {
        // 테스트마다 고유한 회원과 게시글을 만든다.
        // 전체 테이블을 삭제하지 않아 다른 데이터의 FK에 영향을 주지 않는다.
        admin = saveActiveMember();
        author = saveActiveMember();

        // 관리자 계정 준비: 실제 로그인 전에 DB에 권한과 인증 버전을 설정.
        jdbcTemplate.update(
                """
                update members
                   set role = 'ADMIN',
                       credential_version = 3
                 where id = ?
                """,
                admin.getId()
        );

        targetPost = savePost(author);
    }

    @ParameterizedTest
    @EnumSource(
            value = AdminPostStatus.class,
            names = {"VISIBLE", "HIDDEN", "DELETED"}
    )
    void 관리자는_게시글_상태와_무관하게_댓글을_조회한다(
            AdminPostStatus postStatus
    ) throws Exception {
        Long visibleId = saveComment(
                targetPost,
                author,
                "정상 댓글",
                BASE_TIME.minusSeconds(60)
        );

        // 두 댓글은 작성 시각이 같으므로 ID 오름차순으로 정렬되어야 한다.
        Long hiddenId = saveComment(
                targetPost,
                author,
                FULL_CONTENT,
                BASE_TIME
        );
        hideComment(hiddenId);

        Long deletedId = saveComment(
                targetPost,
                author,
                "숨김 이후 삭제한 댓글",
                BASE_TIME
        );
        hideComment(deletedId);
        deleteComment(deletedId);

        Post otherPost = savePost(author);

        Long otherCommentId = saveComment(
                otherPost,
                author,
                "다른 게시글의 댓글",
                BASE_TIME.minusSeconds(120)
        );

        changePostStatus(targetPost.getId(), postStatus);

        MockHttpSession session = login(admin.getEmail());

        MvcResult response = mockMvc.perform(
                        get(PATH, targetPost.getId())
                                .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(jsonPath("$.postId")
                        .value(is(targetPost.getId()), Long.class))
                .andExpect(jsonPath("$.postStatus")
                        .value(postStatus.name()))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].status").value("VISIBLE"))
                .andExpect(jsonPath("$.items[1].status").value("HIDDEN"))
                .andExpect(jsonPath("$.items[1].content").value(FULL_CONTENT))
                .andExpect(jsonPath("$.items[1].createdAt")
                        .value(BASE_TIME.toString()))
                .andExpect(jsonPath("$.items[1].hiddenAt")
                        .value(HIDDEN_AT.toString()))
                .andExpect(jsonPath("$.items[2].status").value("DELETED"))
                .andExpect(jsonPath("$.items[2].content")
                        .value("숨김 이후 삭제한 댓글"))
                .andExpect(jsonPath("$.items[2].hiddenAt")
                        .value(HIDDEN_AT.toString()))
                .andExpect(jsonPath("$.items[2].deletedAt")
                        .value(DELETED_AT.toString()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andReturn();

        assertThat(commentIds(readJson(response)))
                .containsExactly(visibleId, hiddenId, deletedId)
                .doesNotContain(otherCommentId);

        // 관리자 조회가 허용되어도 일반 공개 조회 정책은 그대로 유지된다.
        if (postStatus != AdminPostStatus.VISIBLE) {
            mockMvc.perform(
                            get(
                                    "/api/v1/community/posts/{postId}/comments",
                                    targetPost.getId()
                            )
                    )
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void 댓글_상태와_본문_검색을_적용하고_등록순으로_페이징한다()
            throws Exception {
        // 숨김 게시글에서도 댓글 자체의 HIDDEN 상태로 필터링한다.
        changePostStatus(targetPost.getId(), AdminPostStatus.HIDDEN);

        Long firstId = saveComment(
                targetPost, author,
                "AbC 100%_! 첫 번째",
                BASE_TIME
        );
        hideComment(firstId);

        Long secondId = saveComment(
                targetPost, author,
                "abc 100%_! 두 번째",
                BASE_TIME
        );
        hideComment(secondId);

        Long thirdId = saveComment(
                targetPost, author,
                "ABC 100%_! 세 번째",
                BASE_TIME.plusSeconds(60)
        );
        hideComment(thirdId);

        // 같은 검색어지만 정상 댓글이므로 HIDDEN 검색에서는 제외.
        saveComment(
                targetPost, author,
                "abc 100%_! 정상 댓글",
                BASE_TIME
        );

        // 숨김 이후 삭제한 댓글은 DELETED이므로 HIDDEN 검색에서는 제외.
        Long deletedId = saveComment(
                targetPost, author,
                "abc 100%_! 삭제 댓글",
                BASE_TIME
        );
        hideComment(deletedId);
        deleteComment(deletedId);

        // %와 _가 와일드카드로 처리된다면 잘못 포함될 수 있는 데이터.
        Long wildcardId = saveComment(
                targetPost, author,
                "abc 100XYZ! 검색 제외",
                BASE_TIME
        );
        hideComment(wildcardId);

        // 다른 게시글의 동일한 본문·상태도 제외되어야 한다.
        Post otherPost = savePost(author);
        Long otherId = saveComment(
                otherPost, author,
                "abc 100%_! 다른 게시글",
                BASE_TIME
        );
        hideComment(otherId);

        MockHttpSession session = login(admin.getEmail());

        JsonNode firstPage = readFilteredPage(session, 0, 2);

        assertThat(commentIds(firstPage))
                .containsExactly(firstId, secondId);
        assertThat(firstPage.path("postStatus").asString())
                .isEqualTo("HIDDEN");
        assertThat(firstPage.path("totalElements").asLong())
                .isEqualTo(3);
        assertThat(firstPage.path("totalPages").asInt())
                .isEqualTo(2);
        assertThat(firstPage.path("hasNext").asBoolean()).isTrue();
        assertThat(firstPage.path("hasPrevious").asBoolean()).isFalse();

        JsonNode secondPage = readFilteredPage(session, 1, 2);

        assertThat(commentIds(secondPage))
                .containsExactly(thirdId);
        assertThat(secondPage.path("totalElements").asLong())
                .isEqualTo(3);
        assertThat(secondPage.path("hasNext").asBoolean()).isFalse();
        assertThat(secondPage.path("hasPrevious").asBoolean()).isTrue();

        JsonNode outOfRange = readFilteredPage(session, 5, 2);

        assertThat(commentIds(outOfRange)).isEmpty();
        assertThat(outOfRange.path("page").asInt()).isEqualTo(5);
        assertThat(outOfRange.path("totalElements").asLong())
                .isEqualTo(3);
        assertThat(outOfRange.path("totalPages").asInt())
                .isEqualTo(2);
        assertThat(outOfRange.path("hasNext").asBoolean()).isFalse();
    }

    @Test
    void 댓글이_없는_삭제_게시글도_빈_목록으로_조회한다()
            throws Exception {
        changePostStatus(targetPost.getId(), AdminPostStatus.DELETED);

        MockHttpSession session = login(admin.getEmail());

        mockMvc.perform(
                        get(PATH, targetPost.getId())
                                .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postStatus").value("DELETED"))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(false));
    }

    @Test
    void 작성자가_탈퇴해도_댓글과_작성자_표시를_반환한다()
            throws Exception {
        Long commentId = saveComment(
                targetPost,
                author,
                FULL_CONTENT,
                BASE_TIME
        );

        Member withdrawingAuthor = memberRepository
                .findById(author.getId())
                .orElseThrow();

        withdrawingAuthor.withdraw(DELETED_AT);
        memberRepository.saveAndFlush(withdrawingAuthor);

        MockHttpSession session = login(admin.getEmail());

        mockMvc.perform(
                        get(PATH, targetPost.getId())
                                .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].commentId")
                        .value(is(commentId), Long.class))
                .andExpect(jsonPath("$.items[0].content").value(FULL_CONTENT))
                .andExpect(jsonPath("$.items[0].author.memberId")
                        .value(is(author.getId()), Long.class))
                .andExpect(jsonPath("$.items[0].author.nickname")
                        .value("탈퇴한 회원"))
                .andExpect(jsonPath("$.items[0].author.withdrawn")
                        .value(true))
                .andExpect(jsonPath("$.items[0].author.email")
                        .doesNotExist())
                .andExpect(jsonPath("$.items[0].author.passwordHash")
                        .doesNotExist());
    }

    @Test
    void 존재하지_않는_게시글은_404를_반환한다()
            throws Exception {
        MockHttpSession session = login(admin.getEmail());

        mockMvc.perform(
                        get(PATH, Long.MAX_VALUE)
                                .session(session)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ));
    }

    @Test
    void 비로그인과_일반_회원은_관리자_댓글_조회가_불가능하다()
            throws Exception {
        mockMvc.perform(get(PATH, targetPost.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        MockHttpSession userSession = login(author.getEmail());

        mockMvc.perform(
                        get(PATH, targetPost.getId())
                                .session(userSession)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE", "VERSION", "WITHDRAW"})
    void 관리자_인증_정보가_변경되면_기존_세션을_차단한다(
            String change
    ) throws Exception {
        MockHttpSession session = login(admin.getEmail());

        // 변경 전에는 같은 세션으로 정상 조회 가능.
        mockMvc.perform(
                        get(PATH, targetPost.getId())
                                .session(session)
                )
                .andExpect(status().isOk());

        switch (change) {
            case "ROLE" -> jdbcTemplate.update(
                    """
                    update members
                       set role = 'USER'
                     where id = ?
                    """,
                    admin.getId()
            );

            case "VERSION" -> jdbcTemplate.update(
                    """
                    update members
                       set credential_version = credential_version + 1
                     where id = ?
                    """,
                    admin.getId()
            );

            case "WITHDRAW" -> {
                Member withdrawingAdmin = memberRepository
                        .findById(admin.getId())
                        .orElseThrow();

                withdrawingAdmin.withdraw(DELETED_AT);
                memberRepository.saveAndFlush(withdrawingAdmin);
            }

            default -> throw new IllegalArgumentException(change);
        }

        mockMvc.perform(
                        get(PATH, targetPost.getId())
                                .session(session)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        assertThat(session.isInvalid()).isTrue();

        if ("ROLE".equals(change)) {
            // 다시 로그인하면 유효한 USER 세션이므로 403.
            MockHttpSession userSession = login(admin.getEmail());

            mockMvc.perform(
                            get(PATH, targetPost.getId())
                                    .session(userSession)
                    )
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = AdminPostStatus.class,
            names = {"VISIBLE", "HIDDEN", "DELETED"}
    )
    void 실제_관리자_세션으로_부모_상태와_관계없이_다중_댓글_상태를_조회한다(
            AdminPostStatus parentStatus
    ) throws Exception {
        String searchKeyword = "다중상태검증";

        Long visibleId = saveComment(
                targetPost,
                author,
                searchKeyword + " 정상 첫 번째",
                BASE_TIME
        );

        Long hiddenId = saveComment(
                targetPost,
                author,
                searchKeyword + " 숨김",
                BASE_TIME
        );
        hideComment(hiddenId);

        Long laterVisibleId = saveComment(
                targetPost,
                author,
                searchKeyword + " 정상 두 번째",
                BASE_TIME.plusSeconds(60)
        );

        // 검색어가 같아도 선택하지 않은 삭제 상태는 제외한다.
        Long deletedId = saveComment(
                targetPost,
                author,
                searchKeyword + " 삭제",
                BASE_TIME
        );
        deleteComment(deletedId);

        Long hiddenAndDeletedId = saveComment(
                targetPost,
                author,
                searchKeyword + " 숨김 후 삭제",
                BASE_TIME
        );
        hideComment(hiddenAndDeletedId);
        deleteComment(hiddenAndDeletedId);

        // 상태가 같아도 검색어가 다르면 제외한다.
        saveComment(
                targetPost,
                author,
                "검색 대상이 아닌 정상 댓글",
                BASE_TIME
        );

        Long unrelatedHiddenId = saveComment(
                targetPost,
                author,
                "검색 대상이 아닌 숨김 댓글",
                BASE_TIME
        );
        hideComment(unrelatedHiddenId);

        // 상태와 검색어가 모두 일치해도 다른 게시글의 댓글은 제외한다.
        Post otherPost = savePost(author);

        saveComment(
                otherPost,
                author,
                searchKeyword + " 다른 게시글 정상",
                BASE_TIME.minusSeconds(60)
        );

        Long otherHiddenId = saveComment(
                otherPost,
                author,
                searchKeyword + " 다른 게시글 숨김",
                BASE_TIME.minusSeconds(60)
        );
        hideComment(otherHiddenId);

        changePostStatus(targetPost.getId(), parentStatus);

        MockHttpSession session = login(admin.getEmail());

        String[] statuses = {"HIDDEN", "VISIBLE", "HIDDEN"};

        JsonNode firstPage = readMultipleStatusComments(
                session, searchKeyword, 0, 2, statuses
        );
        JsonNode secondPage = readMultipleStatusComments(
                session, searchKeyword, 1, 2, statuses
        );
        JsonNode outOfRange = readMultipleStatusComments(
                session, searchKeyword, 2, 2, statuses
        );

        // コメントは作成時刻の昇順、同時刻ならIDの昇順。
        assertThat(commentIds(firstPage))
                .containsExactly(visibleId, hiddenId);

        assertThat(commentIds(secondPage))
                .containsExactly(laterVisibleId);

        assertThat(commentIds(outOfRange)).isEmpty();

        assertThat(firstPage.path("items").path(0)
                .path("status").asString())
                .isEqualTo("VISIBLE");

        assertThat(firstPage.path("items").path(1)
                .path("status").asString())
                .isEqualTo("HIDDEN");

        for (JsonNode page : List.of(
                firstPage,
                secondPage,
                outOfRange
        )) {
            assertThat(page.path("postId").asLong())
                    .isEqualTo(targetPost.getId());

            // 부모 상태는 댓글 상태 필터와 별도로 응답한다.
            assertThat(page.path("postStatus").asString())
                    .isEqualTo(parentStatus.name());

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

        assertThat(outOfRange.path("page").asInt()).isEqualTo(2);
        assertThat(outOfRange.path("hasNext").asBoolean()).isFalse();
    }

    private JsonNode readFilteredPage(
            MockHttpSession session,
            int page,
            int size
    ) throws Exception {
        MvcResult result = mockMvc.perform(
                        get(PATH, targetPost.getId())
                                .session(session)
                                .param("status", "HIDDEN")
                                .param("keyword", "  aBc 100%_!  ")
                                .param("page", Integer.toString(page))
                                .param("size", Integer.toString(size))
                )
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andReturn();

        return readJson(result);
    }

    private MockHttpSession login(String email) throws Exception {
        MvcResult csrfResult = mockMvc.perform(
                        get("/api/v1/auth/csrf")
                )
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession csrfSession =
                (MockHttpSession) csrfResult.getRequest()
                        .getSession(false);

        assertThat(csrfSession).isNotNull();

        JsonNode csrf = readJson(csrfResult);

        String headerName = csrf.path("headerName").asString();
        String token = csrf.path("token").asString();

        assertThat(headerName).isNotBlank();
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

        // 세션 ID 교체 이후의 로그인 세션을 사용한다.
        MockHttpSession authenticatedSession =
                (MockHttpSession) loginResult.getRequest()
                        .getSession(false);

        assertThat(authenticatedSession).isNotNull();

        return authenticatedSession;
    }

    private Member saveActiveMember() {
        String unique = UUID.randomUUID()
                .toString()
                .replace("-", "");

        Member member = Member.createPending(
                "admin-comment-" + unique + "@example.com",
                passwordEncoder.encode(PASSWORD),
                "조회" + unique.substring(0, 12)
        );

        member.activeAfterEmailVerification();

        return memberRepository.saveAndFlush(member);
    }

    private Post savePost(Member writer) {
        return postRepository.saveAndFlush(
                Post.create(
                        writer,
                        PostType.QUESTION,
                        PostTopic.HEARING_AID,
                        "관리자 댓글 조회 검증",
                        "댓글 조회 통합 테스트용 게시글입니다."
                )
        );
    }

    private Long saveComment(
            Post post,
            Member writer,
            String content,
            Instant createdAt
    ) {
        Comment comment = commentRepository.saveAndFlush(
                Comment.create(post, writer, content)
        );

        // 실행 속도와 무관하게 정렬 순서를 검증하기 위한 고정 시각.
        jdbcTemplate.update(
                """
                update community_comments
                   set created_at = ?,
                       updated_at = ?
                 where id = ?
                """,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt),
                comment.getId()
        );

        return comment.getId();
    }

    private void hideComment(Long commentId) {
        jdbcTemplate.update(
                """
                update community_comments
                   set hidden_at = ?
                 where id = ?
                """,
                Timestamp.from(HIDDEN_AT),
                commentId
        );
    }

    private void deleteComment(Long commentId) {
        jdbcTemplate.update(
                """
                update community_comments
                   set deleted_at = ?
                 where id = ?
                """,
                Timestamp.from(DELETED_AT),
                commentId
        );
    }

    private void changePostStatus(
            Long postId,
            AdminPostStatus status
    ) {
        switch (status) {
            case VISIBLE -> {
                // 새로 생성한 게시글의 기본 상태를 유지한다.
            }

            case HIDDEN -> jdbcTemplate.update(
                    """
                    update community_posts
                       set hidden_at = ?
                     where id = ?
                    """,
                    Timestamp.from(HIDDEN_AT),
                    postId
            );

            case DELETED -> jdbcTemplate.update(
                    """
                    update community_posts
                       set hidden_at = ?,
                           deleted_at = ?
                     where id = ?
                    """,
                    Timestamp.from(HIDDEN_AT),
                    Timestamp.from(DELETED_AT),
                    postId
            );

            case ALL -> throw new IllegalArgumentException(
                    "ALL은 조회 조건이며 실제 게시글 상태가 아닙니다."
            );
        }
    }

    private JsonNode readJson(MvcResult result) {
        return objectMapper.readTree(
                result.getResponse().getContentAsByteArray()
        );
    }

    private List<Long> commentIds(JsonNode response) {
        List<Long> ids = new ArrayList<>();

        for (JsonNode item : response.path("items")) {
            ids.add(item.path("commentId").asLong());
        }

        return ids;
    }

    private JsonNode readMultipleStatusComments(
            MockHttpSession session,
            String keyword,
            int page,
            int size,
            String... statuses
    ) throws Exception {
        MvcResult result = mockMvc.perform(
                        get(PATH, targetPost.getId())
                                .session(session)
                                .queryParam("statuses", statuses)
                                .queryParam("keyword", "  " + keyword + "  ")
                                .queryParam("page", Integer.toString(page))
                                .queryParam("size", Integer.toString(size))
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andReturn();

        return readJson(result);
    }
}