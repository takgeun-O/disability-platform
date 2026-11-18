package io.github.takgeun.iyum.community.post.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.domain.PostTopic;
import io.github.takgeun.iyum.community.post.domain.PostType;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.config.import=",
        "springdoc.api-docs.enabled=true",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=true",
        "iyum.auth.password-reset.mail.enabled=false",
        "iyum.auth.email-verification.mail.enabled=false"
})
@AutoConfigureMockMvc(addFilters = true)
@Testcontainers
class PostSearchIntegrationTest {

    private static final String BASE = "/api/v1/community/posts";

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
    MockMvc mockMvc;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PostRepository postRepository;

    @Test
    void 비로그인_검색은_제목이나_본문이_일치하는_공개_게시글만_페이지로_반환한다()
            throws Exception {
        Member author = Member.createPending(
                "public-post-search@example.test",
                "unused-password-hash",
                "검색작성자"
        );
        author.activeAfterEmailVerification();
        memberRepository.saveAndFlush(author);

        Post titleMatch = question(author, "보청기 착용 후기", "첫날의 경험입니다.");
        Post bodyMatch = question(author, "착용 질문", "보청기 설정이 궁금합니다.");
        Post unrelated = question(author, "센터 방문 후기", "예약하고 방문했습니다.");

        Post hidden = question(author, "보청기 숨김 글", "숨겨진 게시글입니다.");
        hidden.hide(Instant.now());

        Post deleted = question(author, "삭제한 질문", "보청기 관련 질문입니다.");
        deleted.delete(Instant.now());

        postRepository.saveAllAndFlush(List.of(
                titleMatch,
                bodyMatch,
                unrelated,
                hidden,
                deleted
        ));

        // 세션과 CSRF 토큰 없이 실제 공개 목록 경로로 요청한다.
        mockMvc.perform(
                        get(BASE)
                                .param("q", "보청기")
                                .param("size", "1")
                                .param("page", "0")
                )
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(bodyMatch.getId()))
                .andExpect(jsonPath("$.items[0].matchedContentExcerpt")
                        .value("보청기 설정이 궁금합니다."))
                .andExpect(jsonPath("$.items[0].content")
                        .doesNotHaveJsonPath())
                .andExpect(jsonPath("$.items[0].bodyMatchPosition")
                        .doesNotHaveJsonPath())
                .andExpect(jsonPath("$.items[0].liked").value(false))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true));

        mockMvc.perform(
                        get(BASE)
                                .param("q", "보청기")
                                .param("size", "1")
                                .param("page", "1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(titleMatch.getId()))
                .andExpect(jsonPath("$.items[0].matchedContentExcerpt")
                        .hasJsonPath())
                .andExpect(jsonPath("$.items[0].matchedContentExcerpt")
                        .value(nullValue()))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void 본문_뒤쪽의_영문과_특수문자를_DB에서_찾아_같은_위치의_원문을_발췌한다()
            throws Exception {
        Member author = Member.createPending(
                "excerpt@example.test", "unused-password-hash", "발췌작성자"
        );
        author.activeAfterEmailVerification();
        memberRepository.saveAndFlush(author);

        String match = "[FiT.50%_]  (A+B)";
        String tail = "\n둘째 줄";
        Post post = postRepository.saveAndFlush(question(
                author, "착용 기록", "😀".repeat(60) + match + tail
        ));

        mockMvc.perform(get(BASE).param("q", "  [fit.50%_]  (a+b)  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(post.getId()))
                .andExpect(jsonPath("$.items[0].matchedContentExcerpt")
                        .value("…" + "😀".repeat(40) + match + tail));
    }

    @Test
    void 생성된_목록_문서에는_선택적_검색어와_성공_오류_응답이_있다() throws Exception {
        var result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/community/posts'].get.parameters[?(@.name == 'q')]")
                        .value(org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.paths['/api/v1/community/posts'].get.parameters[?(@.name == 'q')].in")
                        .value(org.hamcrest.Matchers.contains("query")))
                .andExpect(jsonPath("$.paths['/api/v1/community/posts'].get.parameters[?(@.name == 'q')].required")
                        .value(org.hamcrest.Matchers.contains(false)))
                .andExpect(jsonPath("$.paths['/api/v1/community/posts'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/community/posts'].get.responses['400']").exists())
                .andReturn();
        var evidence = java.nio.file.Path.of("build/reports/public-post-search-openapi.json");
        java.nio.file.Files.createDirectories(evidence.getParent());
        java.nio.file.Files.write(evidence, result.getResponse().getContentAsByteArray());
    }

    @Test
    void 목록_항목_스키마는_발췌_문자열과_null만_허용하고_누락을_거부한다()
            throws Exception {
        byte[] response = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode document = mapper.readTree(response);
        String listRef = document.at("/paths/~1api~1v1~1community~1posts/get"
                + "/responses/200/content/application~1json/schema/$ref").asText();
        assertThat(listRef).startsWith("#/components/schemas/");
        String itemRef = document.at(listRef.substring(1))
                .at("/properties/items/items/$ref").asText();
        assertThat(itemRef).startsWith("#/components/schemas/");
        assertThat(document.at(itemRef.substring(1)).path("required"))
                .anyMatch(field -> field.asText().equals("matchedContentExcerpt"));

        ObjectNode root = mapper.createObjectNode();
        root.put("$ref", itemRef);
        root.set("components", document.path("components"));
        assertThat(root.findValues("$ref"))
                .allSatisfy(ref -> assertThat(ref.asText()).startsWith("#/"));
        var schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(root);
        ObjectNode item = (ObjectNode) mapper.readTree("""
                {
                  "id": 2,
                  "type": "QUESTION",
                  "topic": "HEARING_AID",
                  "title": "상담 후기",
                  "author": { "memberId": 1, "nickname": "예시회원" },
                  "createdAt": "2026-11-18T01:00:00Z",
                  "commentCount": 0,
                  "likeCount": 0,
                  "liked": false,
                  "matchedContentExcerpt": "보청기를 착용했습니다."
                }
                """);
        assertThat(schema.validate(item)).isEmpty();
        item.putNull("matchedContentExcerpt");
        assertThat(schema.validate(item)).isEmpty();
        item.remove("matchedContentExcerpt");
        assertThat(schema.validate(item)).isNotEmpty();
        item.put("matchedContentExcerpt", 123);
        assertThat(schema.validate(item)).isNotEmpty();
    }

    @Test
    void 본문의_대소문자_차이로_검색된_글은_일치한_원문을_발췌한다()
            throws Exception {
        Member author = Member.createPending(
                "unicode-excerpt@example.test",
                "unused-password-hash",
                "원문발췌작성자"
        );
        author.activeAfterEmailVerification();
        memberRepository.saveAndFlush(author);

        Post post = postRepository.saveAndFlush(
                question(
                        author,
                        "상담 후기",
                        "CAFÉ"
                )
        );

        mockMvc.perform(
                        get(BASE)
                                .param("q", "café")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(post.getId()))
                .andExpect(jsonPath("$.items[0].matchedContentExcerpt")
                        .value("CAFÉ"));
    }

    @Test
    void 점이_있는_대문자_I_뒤의_일치는_원문_경계에서_발췌한다()
            throws Exception {
        Member author = Member.createPending(
                "excerpt-boundary@example.test",
                "unused-password-hash",
                "문맥작성자"
        );
        author.activeAfterEmailVerification();
        memberRepository.saveAndFlush(author);

        String context = "가".repeat(38) + "\n😀";
        Post post = postRepository.saveAndFlush(question(
                author, "발췌 경계", "İ" + context + "An_ChOr"
        ));

        mockMvc.perform(get(BASE).param("q", "an_chor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(post.getId()))
                .andExpect(jsonPath("$.items[0].matchedContentExcerpt")
                        .value("…" + context + "An_ChOr"));
    }

    private Post question(Member author, String title, String content) {
        return Post.create(
                author,
                PostType.QUESTION,
                PostTopic.HEARING_AID,
                title,
                content
        );
    }
}