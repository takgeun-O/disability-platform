package io.github.takgeun.iyum.admin.member.api;

import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
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
class AdminMemberReadSessionIntegrationTest {

    private static final String LIST_PATH =
            "/api/v1/admin/members";

    private static final String DETAIL_PATH =
            "/api/v1/admin/members/{memberId}";

    private static final String PASSWORD =
            "AdminMemberTest123!";

    private static final Instant CREATED_AT =
            Instant.parse("2026-10-30T10:00:00Z");

    private static final Instant WITHDRAWN_AT =
            Instant.parse("2026-11-01T11:00:00Z");

    private static final Instant UPDATED_AT =
            Instant.parse("2026-11-02T12:00:00Z");

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
    JdbcTemplate jdbcTemplate;

    private Member admin;
    private Member target;

    private String token;
    private String passwordHash;

    /*
     * 클래스에 @Transactional을 붙이지 않는다.
     *
     * 테스트에서 변경한 회원 정보가 다음 HTTP 요청의 인증 필터와
     * 조회 서비스에 실제로 반영되는지 검증한다.
     *
     * 테스트마다 고유한 이메일과 닉네임 검색 접두사를 사용하므로
     * 다른 테스트에서 저장한 회원을 전체 삭제할 필요가 없다.
     */
    @BeforeEach
    void setUp() {
        token = "m" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 10);

        passwordHash = passwordEncoder.encode(PASSWORD);

        admin = saveMember(
                "Admin",
                MemberStatus.ACTIVE,
                MemberRole.ADMIN,
                CREATED_AT
        );

        target = saveMember(
                "Target",
                MemberStatus.ACTIVE,
                MemberRole.USER,
                CREATED_AT
        );

        // 로그인 시 실제 DB에서 이 인증 버전을 읽도록 설정한다.
        int updatedRows = jdbcTemplate.update("""
                UPDATE members
                   SET credential_version = 3
                 WHERE id = ?
                """,
                admin.getId()
        );

        assertThat(updatedRows).isEqualTo(1);
    }

    // ---------------------------------------------------------------------
    // 실제 관리자 로그인 및 기본 응답 계약
    // ---------------------------------------------------------------------

    @Test
    void 실제_관리자_세션으로_회원_목록과_상세를_조회한다()
            throws Exception {
        MockHttpSession session = login(admin.getEmail());

        // 다른 테스트 데이터와 섞이지 않도록 대상 ID로 검색한다.
        // page, size, sort는 생략하여 기본값 적용도 확인한다.
        JsonNode list = readList(
                session,
                "searchType", "MEMBER_ID",
                "keyword", target.getId().toString()
        );

        assertThat(memberIds(list))
                .containsExactly(target.getId());

        assertThat(list.path("page").asInt()).isZero();
        assertThat(list.path("size").asInt()).isEqualTo(20);
        assertThat(list.path("totalElements").asLong()).isEqualTo(1);
        assertThat(list.path("totalPages").asInt()).isEqualTo(1);
        assertThat(list.path("hasNext").asBoolean()).isFalse();
        assertThat(list.path("hasPrevious").asBoolean()).isFalse();

        JsonNode item = list.path("items").path(0);

        assertThat(item.path("nickname").asString())
                .isEqualTo(target.getNickname());
        assertThat(item.path("email").asString())
                .isEqualTo(target.getEmail());
        assertThat(item.path("status").asString())
                .isEqualTo("ACTIVE");
        assertThat(item.path("role").asString())
                .isEqualTo("USER");
        assertThat(item.path("createdAt").asString())
                .isEqualTo(CREATED_AT.toString());

        assertSensitiveFieldsAbsent(item);

        // 실제 로그인 세션만 전달한다. GET에는 CSRF 토큰이 필요 없다.
        expectOk(readDetail(session, target.getId()))
                .andExpect(jsonPath("$.memberId")
                        .value(is(target.getId()), Long.class))
                .andExpect(jsonPath("$.nickname")
                        .value(target.getNickname()))
                .andExpect(jsonPath("$.email")
                        .value(target.getEmail()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.createdAt")
                        .value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.updatedAt")
                        .value(UPDATED_AT.toString()))
                .andExpect(jsonPath("$.withdrawnAt").hasJsonPath())
                .andExpect(jsonPath("$.withdrawnAt").value(nullValue()))
                .andExpect(jsonPath("$.passwordHash")
                        .doesNotHaveJsonPath())
                .andExpect(jsonPath("$.credentialVersion")
                        .doesNotHaveJsonPath());
    }

    @Test
    void 요청_파라미터로_관리자_ID와_인증_버전을_덮어쓸_수_없다()
            throws Exception {
        MockHttpSession session = login(admin.getEmail());

        JsonNode list = readList(
                session,
                "searchType", "MEMBER_ID",
                "keyword", target.getId().toString(),
                "actorMemberId", target.getId().toString(),
                "credentialVersion", "999999"
        );

        assertThat(memberIds(list))
                .containsExactly(target.getId());

        expectOk(mockMvc.perform(
                get(DETAIL_PATH, target.getId())
                        .session(session)
                        .queryParam(
                                "actorMemberId",
                                target.getId().toString()
                        )
                        .queryParam("credentialVersion", "999999")
        ))
                .andExpect(jsonPath("$.memberId")
                        .value(is(target.getId()), Long.class));
    }

    // ---------------------------------------------------------------------
    // 검색 정책
    // ---------------------------------------------------------------------

    @Test
    void 회원_ID는_정확히_일치하는_대상만_조회한다()
            throws Exception {
        saveMember(
                "Other",
                MemberStatus.ACTIVE,
                MemberRole.USER,
                CREATED_AT
        );

        MockHttpSession session = login(admin.getEmail());

        JsonNode response = readList(
                session,
                "searchType", "MEMBER_ID",
                "keyword", "  00" + target.getId() + "  "
        );

        assertThat(memberIds(response))
                .containsExactly(target.getId());
        assertThat(response.path("totalElements").asLong())
                .isEqualTo(1);
    }

    @Test
    void 닉네임은_대소문자를_구분하지_않고_부분_검색한다()
            throws Exception {
        Member matched = saveActiveMember(
                uniqueEmail(),
                "Aa" + token + "End",
                CREATED_AT
        );

        saveMember(
                "Other",
                MemberStatus.ACTIVE,
                MemberRole.USER,
                CREATED_AT
        );

        MockHttpSession session = login(admin.getEmail());

        JsonNode response = readList(
                session,
                "searchType", "NICKNAME",
                "keyword",
                "  AA" + token.toUpperCase(Locale.ROOT) + "  "
        );

        assertThat(memberIds(response))
                .containsExactly(matched.getId());
        assertThat(response.path("totalElements").asLong())
                .isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!"})
    void 닉네임의_특수문자를_LIKE_와일드카드로_해석하지_않는다(
            String symbol
    ) throws Exception {
        Member matched = saveMember(
                "Literal",
                MemberStatus.ACTIVE,
                MemberRole.USER,
                CREATED_AT
        );

        String prefix = token + "X";

        /*
         * 현재 가입 정책은 %와 !가 포함된 닉네임을 허용하지 않는다.
         * 검색 계층의 리터럴 검색을 검증하기 위한 기존 데이터 상황을
         * JDBC로 구성한다. 실제 가입 정책을 변경하는 것은 아니다.
         */
        setNickname(matched.getId(), prefix + symbol + "A");

        // %나 _를 와일드카드로 잘못 처리하면 함께 조회될 수 있는 데이터.
        Member wildcardCandidate = saveMember(
                "Wild",
                MemberStatus.ACTIVE,
                MemberRole.USER,
                CREATED_AT
        );
        setNickname(wildcardCandidate.getId(), prefix + "ZA");

        // !를 escape 문자로만 취급하여 누락하면 조회될 수 있는 데이터.
        Member escapeCandidate = saveMember(
                "Escape",
                MemberStatus.ACTIVE,
                MemberRole.USER,
                CREATED_AT
        );
        setNickname(escapeCandidate.getId(), prefix + "A");

        MockHttpSession session = login(admin.getEmail());

        JsonNode response = readList(
                session,
                "searchType", "NICKNAME",
                "keyword", prefix + symbol + "A"
        );

        assertThat(memberIds(response))
                .containsExactly(matched.getId());
        assertThat(response.path("totalElements").asLong())
                .isEqualTo(1);
    }

    @Test
    void 이메일은_정규화한_전체_주소가_일치할_때만_조회한다()
            throws Exception {
        // 부분 검색으로 구현했다면 함께 조회될 수 있는 이메일.
        saveActiveMember(
                "x" + target.getEmail(),
                token + "Similar",
                CREATED_AT
        );

        MockHttpSession session = login(admin.getEmail());

        JsonNode exact = readList(
                session,
                "searchType", "EMAIL",
                "keyword",
                "  " + target.getEmail().toUpperCase(Locale.ROOT) + "  "
        );

        assertThat(memberIds(exact))
                .containsExactly(target.getId());

        String localPart = target.getEmail().substring(
                0,
                target.getEmail().indexOf('@')
        );

        // 이메일 검색어의 전체 형식을 검증하는 정책은 적용하지 않는다.
        // 부분 주소는 오류가 아니라 정확히 일치하는 대상이 없는 결과다.
        JsonNode partial = readList(
                session,
                "searchType", "EMAIL",
                "keyword", localPart
        );

        assertThat(memberIds(partial)).isEmpty();
        assertThat(partial.path("totalElements").asLong()).isZero();
    }

    @Test
    void 검색과_상태와_역할_필터는_AND로_결합한다()
            throws Exception {
        Member matched = saveMember(
                "Mix1",
                MemberStatus.ACTIVE,
                MemberRole.USER,
                CREATED_AT
        );

        // 검색어와 역할은 같지만 상태가 다르다.
        saveMember(
                "Mix2",
                MemberStatus.SUSPENDED,
                MemberRole.USER,
                CREATED_AT
        );

        // 검색어와 상태는 같지만 역할이 다르다.
        saveMember(
                "Mix3",
                MemberStatus.ACTIVE,
                MemberRole.ADMIN,
                CREATED_AT
        );

        // 상태와 역할은 같지만 검색어가 다르다.
        saveMember(
                "Else",
                MemberStatus.ACTIVE,
                MemberRole.USER,
                CREATED_AT
        );

        MockHttpSession session = login(admin.getEmail());

        JsonNode response = readList(
                session,
                "searchType", "NICKNAME",
                "keyword", token + "Mix",
                "status", "ACTIVE",
                "role", "USER"
        );

        assertThat(memberIds(response))
                .containsExactly(matched.getId());
        assertThat(response.path("totalElements").asLong())
                .isEqualTo(1);
        assertThat(response.path("totalPages").asInt())
                .isEqualTo(1);
    }

    // ---------------------------------------------------------------------
    // 회원 상태 및 표시 정책
    // ---------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(MemberStatus.class)
    void 관리자는_모든_상태의_회원을_목록과_상세에서_조회한다(
            MemberStatus memberStatus
    ) throws Exception {
        Member member = saveMember(
                "State",
                memberStatus,
                MemberRole.USER,
                CREATED_AT
        );

        MockHttpSession session = login(admin.getEmail());

        // 상태 필터를 생략해도 비활성·탈퇴·삭제 대상이 제외되지 않는다.
        JsonNode unfiltered = readList(
                session,
                "searchType", "MEMBER_ID",
                "keyword", member.getId().toString()
        );

        assertThat(memberIds(unfiltered))
                .containsExactly(member.getId());

        // 해당 상태를 명시한 경우도 조회된다.
        JsonNode filtered = readList(
                session,
                "searchType", "MEMBER_ID",
                "keyword", member.getId().toString(),
                "status", memberStatus.name()
        );

        assertThat(memberIds(filtered))
                .containsExactly(member.getId());

        JsonNode item = filtered.path("items").path(0);

        assertThat(item.path("status").asString())
                .isEqualTo(memberStatus.name());
        assertSensitiveFieldsAbsent(item);

        boolean withdrawn =
                memberStatus == MemberStatus.WITHDRAWN
                        || memberStatus == MemberStatus.DELETED;

        String expectedNickname = withdrawn
                ? "탈퇴한 회원"
                : member.getNickname();

        assertThat(item.path("nickname").asString())
                .isEqualTo(expectedNickname);

        assertThat(item.has("email")).isTrue();

        if (memberStatus == MemberStatus.DELETED) {
            assertThat(item.path("email").isNull()).isTrue();
        } else {
            assertThat(item.path("email").asString())
                    .isEqualTo(member.getEmail());
        }

        ResultActions detail = expectOk(
                readDetail(session, member.getId())
        )
                .andExpect(jsonPath("$.memberId")
                        .value(is(member.getId()), Long.class))
                .andExpect(jsonPath("$.status")
                        .value(memberStatus.name()))
                .andExpect(jsonPath("$.nickname")
                        .value(expectedNickname))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.createdAt")
                        .value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.updatedAt")
                        .value(UPDATED_AT.toString()))
                .andExpect(jsonPath("$.email").hasJsonPath())
                .andExpect(jsonPath("$.withdrawnAt").hasJsonPath())
                .andExpect(jsonPath("$.passwordHash")
                        .doesNotHaveJsonPath())
                .andExpect(jsonPath("$.credentialVersion")
                        .doesNotHaveJsonPath());

        // String과 Matcher를 삼항 연산자로 섞지 않는다.
        if (memberStatus == MemberStatus.DELETED) {
            detail.andExpect(jsonPath("$.email").value(nullValue()));
        } else {
            detail.andExpect(jsonPath("$.email")
                    .value(member.getEmail()));
        }

        if (withdrawn) {
            detail.andExpect(jsonPath("$.withdrawnAt")
                    .value(WITHDRAWN_AT.toString()));
        } else {
            detail.andExpect(jsonPath("$.withdrawnAt")
                    .value(nullValue()));
        }
    }

    @Test
    void 탈퇴_처리된_회원은_개인정보가_제거되어도_ID로_조회된다()
            throws Exception {
        MockHttpSession session = login(admin.getEmail());

        Member withdrawing = memberRepository.findById(target.getId())
                .orElseThrow();

        withdrawing.withdraw(WITHDRAWN_AT);
        memberRepository.saveAndFlush(withdrawing);

        setTimes(target.getId(), CREATED_AT, UPDATED_AT);

        JsonNode response = readList(
                session,
                "searchType", "MEMBER_ID",
                "keyword", target.getId().toString()
        );

        assertThat(memberIds(response))
                .containsExactly(target.getId());

        JsonNode item = response.path("items").path(0);

        assertThat(item.path("status").asString())
                .isEqualTo("DELETED");
        assertThat(item.path("nickname").asString())
                .isEqualTo("탈퇴한 회원");
        assertThat(item.has("email")).isTrue();
        assertThat(item.path("email").isNull()).isTrue();

        // target 객체에는 탈퇴 전 이메일이 남아 있다.
        // DB에서는 삭제되었으므로 기존 이메일 검색으로 찾을 수 없다.
        JsonNode emailSearch = readList(
                session,
                "searchType", "EMAIL",
                "keyword", target.getEmail()
        );

        assertThat(memberIds(emailSearch)).isEmpty();

        expectOk(readDetail(session, target.getId()))
                .andExpect(jsonPath("$.status").value("DELETED"))
                .andExpect(jsonPath("$.nickname").value("탈퇴한 회원"))
                .andExpect(jsonPath("$.email").hasJsonPath())
                .andExpect(jsonPath("$.email").value(nullValue()))
                .andExpect(jsonPath("$.withdrawnAt")
                        .value(WITHDRAWN_AT.toString()));
    }

    // ---------------------------------------------------------------------
    // 정렬 및 페이지
    // ---------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"LATEST", "OLDEST"})
    void 가입_시각과_ID로_전체_결과를_정렬한_후_페이지를_적용한다(
            String sort
    ) throws Exception {
        Member firstTie = saveMember(
                "Sort1",
                MemberStatus.ACTIVE,
                MemberRole.USER,
                CREATED_AT.plusSeconds(10)
        );

        Member secondTie = saveMember(
                "Sort2",
                MemberStatus.ACTIVE,
                MemberRole.USER,
                CREATED_AT.plusSeconds(10)
        );

        Member newest = saveMember(
                "Sort3",
                MemberStatus.ACTIVE,
                MemberRole.USER,
                CREATED_AT.plusSeconds(20)
        );

        // ID는 가장 크지만 가입 시각은 가장 오래된 회원.
        // ID만으로 정렬하는 오류를 검출한다.
        Member oldest = saveMember(
                "Sort4",
                MemberStatus.ACTIVE,
                MemberRole.USER,
                CREATED_AT
        );

        MockHttpSession session = login(admin.getEmail());

        JsonNode firstPage = readSortedPage(session, sort, 0);
        JsonNode secondPage = readSortedPage(session, sort, 1);
        JsonNode outsidePage = readSortedPage(session, sort, 2);

        List<Long> expectedIds = "LATEST".equals(sort)
                ? List.of(
                newest.getId(),
                secondTie.getId(),
                firstTie.getId(),
                oldest.getId()
        )
                : List.of(
                oldest.getId(),
                firstTie.getId(),
                secondTie.getId(),
                newest.getId()
        );

        assertThat(memberIds(firstPage))
                .containsExactlyElementsOf(expectedIds.subList(0, 2));

        assertThat(memberIds(secondPage))
                .containsExactlyElementsOf(expectedIds.subList(2, 4));

        assertThat(memberIds(outsidePage)).isEmpty();

        for (JsonNode page : List.of(
                firstPage,
                secondPage,
                outsidePage
        )) {
            assertThat(page.path("totalElements").asLong())
                    .isEqualTo(4);
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

    @Test
    void 검색_결과가_없으면_빈_목록과_0건을_반환한다()
            throws Exception {
        MockHttpSession session = login(admin.getEmail());

        JsonNode response = readList(
                session,
                "searchType", "EMAIL",
                "keyword", "missing-" + UUID.randomUUID() + "@example.com"
        );

        assertThat(memberIds(response)).isEmpty();
        assertThat(response.path("page").asInt()).isZero();
        assertThat(response.path("totalElements").asLong()).isZero();
        assertThat(response.path("totalPages").asInt()).isZero();
        assertThat(response.path("hasNext").asBoolean()).isFalse();
        assertThat(response.path("hasPrevious").asBoolean()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MAX_VALUE})
    void 조회할_수_없는_상세_회원_ID는_404다(
            long memberId
    ) throws Exception {
        MockHttpSession session = login(admin.getEmail());

        readDetail(session, memberId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ));
    }

    // ---------------------------------------------------------------------
    // 실제 세션의 인증 및 인가
    // ---------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 비로그인은_회원_목록과_상세를_조회할_수_없다(
            boolean detail
    ) throws Exception {
        mockMvc.perform(get(targetPath(detail)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.items").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.email").doesNotHaveJsonPath());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 실제_일반_회원_세션은_관리자_조회가_403이다(
            boolean detail
    ) throws Exception {
        MockHttpSession session = login(target.getEmail());

        mockMvc.perform(
                        get(targetPath(detail))
                                .session(session)
                                // 관리자 ID를 보내도 권한을 얻을 수 없다.
                                .queryParam(
                                        "actorMemberId",
                                        admin.getId().toString()
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.items").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.email").doesNotHaveJsonPath());

        // 관리자 기능 접근 거부가 정상 일반 회원 세션을 무효화하지 않는다.
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
    void 로그인_후_권한이나_회원_정보가_변경되면_기존_세션을_차단한다(
            String change,
            boolean detail
    ) throws Exception {
        MockHttpSession session = login(admin.getEmail());

        // 변경 전 동일한 세션으로 조회가 가능함을 먼저 확인한다.
        expectOk(mockMvc.perform(
                get(targetPath(detail))
                        .session(session)
        ));

        switch (change) {
            case "ROLE" -> {
                int rows = jdbcTemplate.update("""
                        UPDATE members
                           SET role = 'USER'
                         WHERE id = ?
                        """,
                        admin.getId()
                );

                assertThat(rows).isEqualTo(1);
            }

            case "VERSION" -> {
                int rows = jdbcTemplate.update("""
                        UPDATE members
                           SET credential_version = credential_version + 1
                         WHERE id = ?
                        """,
                        admin.getId()
                );

                assertThat(rows).isEqualTo(1);
            }

            case "STATUS" -> {
                int rows = jdbcTemplate.update("""
                        UPDATE members
                           SET status = 'SUSPENDED'
                         WHERE id = ?
                        """,
                        admin.getId()
                );

                assertThat(rows).isEqualTo(1);
            }

            case "WITHDRAWAL" -> {
                Member withdrawingAdmin =
                        memberRepository.findById(admin.getId())
                                .orElseThrow();

                withdrawingAdmin.withdraw(WITHDRAWN_AT);
                memberRepository.saveAndFlush(withdrawingAdmin);
            }

            default -> throw new IllegalArgumentException(
                    "지원하지 않는 테스트 조건: " + change
            );
        }

        mockMvc.perform(
                        get(targetPath(detail))
                                .session(session)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.items").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.email").doesNotHaveJsonPath());

        assertThat(session.isInvalid()).isTrue();
    }

    // ---------------------------------------------------------------------
    // HTTP 요청 도우미
    // ---------------------------------------------------------------------

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

        MvcResult result = expectOk(
                mockMvc.perform(request)
        ).andReturn();

        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsByteArray()
        );

        assertThat(response.path("items").isArray()).isTrue();

        for (JsonNode item : response.path("items")) {
            assertSensitiveFieldsAbsent(item);
        }

        return response;
    }

    private JsonNode readSortedPage(
            MockHttpSession session,
            String sort,
            int page
    ) throws Exception {
        return readList(
                session,
                "searchType", "NICKNAME",
                "keyword", token + "Sort",
                "sort", sort,
                "page", Integer.toString(page),
                "size", "2"
        );
    }

    private ResultActions readDetail(
            MockHttpSession session,
            Long memberId
    ) throws Exception {
        return mockMvc.perform(
                get(DETAIL_PATH, memberId)
                        .session(session)
                        .accept(MediaType.APPLICATION_JSON)
        );
    }

    private String targetPath(boolean detail) {
        return detail
                ? LIST_PATH + "/" + target.getId()
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

    private List<Long> memberIds(JsonNode response) {
        List<Long> ids = new ArrayList<>();

        for (JsonNode item : response.path("items")) {
            ids.add(item.path("memberId").asLong());
        }

        return ids;
    }

    private void assertSensitiveFieldsAbsent(JsonNode member) {
        assertThat(member.has("passwordHash")).isFalse();
        assertThat(member.has("password")).isFalse();
        assertThat(member.has("credentialVersion")).isFalse();
    }

    // ---------------------------------------------------------------------
    // 실제 로그인
    // ---------------------------------------------------------------------

    private MockHttpSession login(String email) throws Exception {
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
                csrfResult.getResponse().getContentAsByteArray()
        );

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

        // 로그인 과정에서 세션이 교체될 수 있으므로 로그인 결과에서 받는다.
        MockHttpSession authenticatedSession =
                (MockHttpSession) loginResult
                        .getRequest()
                        .getSession(false);

        assertThat(authenticatedSession).isNotNull();
        assertThat(authenticatedSession.isInvalid()).isFalse();

        return authenticatedSession;
    }

    // ---------------------------------------------------------------------
    // 테스트 데이터 준비
    // ---------------------------------------------------------------------

    private Member saveMember(
            String label,
            MemberStatus status,
            MemberRole role,
            Instant createdAt
    ) {
        Member member = saveActiveMember(
                uniqueEmail(),
                token + label,
                createdAt
        );

        if (status == MemberStatus.DELETED) {
            Member deleting = memberRepository.findById(member.getId())
                    .orElseThrow();

            // 실제 탈퇴 동작으로 개인정보를 제거한다.
            deleting.withdraw(WITHDRAWN_AT);
            memberRepository.saveAndFlush(deleting);

        } else {
            Timestamp withdrawnAt =
                    status == MemberStatus.WITHDRAWN
                            ? Timestamp.from(WITHDRAWN_AT)
                            : null;

            /*
             * 정지·제한 등은 이번 읽기 기능의 조회 테스트용 데이터다.
             * 상태 변경 기능 자체를 구현하거나 호출하지 않는다.
             *
             * WITHDRAWN에는 탈퇴 시각이 필요하고,
             * 다른 비삭제 상태에는 탈퇴 시각이 없어야 한다.
             */
            int rows = jdbcTemplate.update("""
                    UPDATE members
                       SET status = ?,
                           withdrawn_at = ?
                     WHERE id = ?
                    """,
                    status.name(),
                    withdrawnAt,
                    member.getId()
            );

            assertThat(rows).isEqualTo(1);
        }

        int roleRows = jdbcTemplate.update("""
                UPDATE members
                   SET role = ?
                 WHERE id = ?
                """,
                role.name(),
                member.getId()
        );

        assertThat(roleRows).isEqualTo(1);

        // 상태 변경 시 Auditing이 갱신한 시각도 테스트 기준으로 고정한다.
        setTimes(member.getId(), createdAt, UPDATED_AT);

        /*
         * 반환 객체는 생성 당시 이메일/닉네임을 비교하는 용도로 사용한다.
         * JDBC로 변경한 status/role/timestamp는 이 객체에서 읽지 않는다.
         * 실제 API는 새로운 조회로 DB의 현재 값을 읽는다.
         */
        return member;
    }

    private Member saveActiveMember(
            String email,
            String nickname,
            Instant createdAt
    ) {
        Member member = Member.createPending(
                email,
                passwordHash,
                nickname
        );

        member.activeAfterEmailVerification();

        Member saved = memberRepository.saveAndFlush(member);

        setTimes(saved.getId(), createdAt, UPDATED_AT);

        return saved;
    }

    private String uniqueEmail() {
        return "member-"
                + UUID.randomUUID().toString().replace("-", "")
                + "@example.com";
    }

    private void setTimes(
            Long memberId,
            Instant createdAt,
            Instant updatedAt
    ) {
        int rows = jdbcTemplate.update("""
                UPDATE members
                   SET created_at = ?,
                       updated_at = ?
                 WHERE id = ?
                """,
                Timestamp.from(createdAt),
                Timestamp.from(updatedAt),
                memberId
        );

        assertThat(rows).isEqualTo(1);
    }

    private void setNickname(
            Long memberId,
            String nickname
    ) {
        int rows = jdbcTemplate.update("""
                UPDATE members
                   SET nickname = ?
                 WHERE id = ?
                """,
                nickname,
                memberId
        );

        assertThat(rows).isEqualTo(1);
    }
}