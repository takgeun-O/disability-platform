package io.github.takgeun.iyum.admin.report.application;

import io.github.takgeun.iyum.admin.report.infrastructure.AdminReportReadRepository;
import io.github.takgeun.iyum.admin.report.infrastructure.AdminReportTargetReadRepository;
import io.github.takgeun.iyum.community.report.domain.ContentReport;
import io.github.takgeun.iyum.community.report.domain.ContentReportReasonType;
import io.github.takgeun.iyum.community.report.domain.ContentReportStatus;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminReportReadServiceTest {

    private static final Long ACTOR_ID = 40L;
    private static final Long REPORTER_ID = 99L;
    private static final long VERSION = 3L;

    private static final Instant NOW =
            Instant.parse("2026-11-12T03:00:00Z");

    @Mock
    MemberRepository memberRepository;

    @Mock
    AdminReportReadRepository reportReadRepository;

    @Mock
    AdminReportTargetReadRepository targetReadRepository;

    @InjectMocks
    AdminReportReadService service;

    @Test
    void 현재_페이지의_대상을_종류별로_중복_없이_조회한다() {
        allowAdmin();

        AdminReportReadQuery query = query(1, 3);

        // 게시글과 댓글의 숫자 ID가 같아도 별개 대상으로 취급한다.
        ContentReport firstPost = report(
                true, 103L, 7L, "접수 당시\n  첫 본문"
        );
        ContentReport comment = report(
                false, 102L, 7L, "접수 당시 댓글"
        );
        ContentReport secondPost = report(
                true, 101L, 7L, "접수 당시 둘째 본문"
        );

        PageRequest pageable = PageRequest.of(
                1,
                3,
                Sort.by(Sort.Direction.DESC, "createdAt", "id")
        );

        when(reportReadRepository.findReports(query))
                .thenReturn(new PageImpl<>(
                        List.of(firstPost, comment, secondPost),
                        pageable,
                        10
                ));

        AdminReportCurrentTargetResult postTarget =
                currentTarget(true, 7L);
        AdminReportCurrentTargetResult commentTarget =
                currentTarget(false, 7L);

        when(targetReadRepository.findPosts(Set.of(7L)))
                .thenReturn(Map.of(7L, postTarget));
        when(targetReadRepository.findComments(Set.of(7L)))
                .thenReturn(Map.of(7L, commentTarget));

        Page<AdminReportItemResult> result =
                service.getReports(ACTOR_ID, VERSION, query);

        assertThat(result.getContent())
                .extracting(AdminReportItemResult::reportId)
                .containsExactly(103L, 102L, 101L);

        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(3);
        assertThat(result.getTotalElements()).isEqualTo(10);
        assertThat(result.getTotalPages()).isEqualTo(4);
        assertThat(result.getSort()).isEqualTo(pageable.getSort());
        assertThat(result.hasNext()).isTrue();
        assertThat(result.hasPrevious()).isTrue();

        AdminReportItemResult first = result.getContent().get(0);
        AdminReportItemResult second = result.getContent().get(1);

        assertThat(first.targetType()).isEqualTo(AdminReportTargetType.POST);
        assertThat(first.targetId()).isEqualTo(7L);
        assertThat(first.titleSnapshot()).isEqualTo("접수 당시 제목");
        assertThat(first.contentSnapshotPreview())
                .isEqualTo("접수 당시 첫 본문");
        assertThat(first.currentAuthor()).isEqualTo(postTarget.author());
        assertThat(first.status()).isEqualTo(ContentReportStatus.RECEIVED);
        assertThat(first.reasonType()).isEqualTo(ContentReportReasonType.OTHER);
        assertThat(first.createdAt()).isEqualTo(NOW);

        assertThat(second.targetType())
                .isEqualTo(AdminReportTargetType.COMMENT);
        assertThat(second.targetId()).isEqualTo(7L);
        assertThat(second.titleSnapshot()).isNull();
        assertThat(second.currentAuthor()).isEqualTo(commentTarget.author());

        assertThat(result.getContent().get(2).currentAuthor())
                .isEqualTo(postTarget.author());

        var order = inOrder(
                memberRepository,
                reportReadRepository,
                targetReadRepository
        );

        order.verify(memberRepository).findById(ACTOR_ID);
        order.verify(reportReadRepository).findReports(query);
        order.verify(targetReadRepository).findPosts(Set.of(7L));
        order.verify(targetReadRepository).findComments(Set.of(7L));

        verifyNoMoreInteractions(targetReadRepository);
    }

    @Test
    void 현재_대상이_없어도_신고_행과_전체_개수는_유지한다() {
        allowAdmin();

        AdminReportReadQuery query = query(0, 2);

        when(reportReadRepository.findReports(query))
                .thenReturn(new PageImpl<>(
                        List.of(
                                report(true, 2L, 10L, "게시글 스냅샷"),
                                report(false, 1L, 20L, "댓글 스냅샷")
                        ),
                        PageRequest.of(0, 2),
                        9
                ));

        when(targetReadRepository.findPosts(Set.of(10L)))
                .thenReturn(Map.of());
        when(targetReadRepository.findComments(Set.of(20L)))
                .thenReturn(Map.of());

        Page<AdminReportItemResult> result =
                service.getReports(ACTOR_ID, VERSION, query);

        assertThat(result.getContent())
                .extracting(AdminReportItemResult::reportId)
                .containsExactly(2L, 1L);

        assertThat(result.getContent())
                .allSatisfy(item ->
                        assertThat(item.currentAuthor()).isNull()
                );

        assertThat(result.getContent())
                .extracting(AdminReportItemResult::contentSnapshotPreview)
                .containsExactly("게시글 스냅샷", "댓글 스냅샷");

        assertThat(result.getTotalElements()).isEqualTo(9);
        assertThat(result.getTotalPages()).isEqualTo(5);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 한_종류의_대상만_있으면_해당_저장소_메서드만_호출한다(
            boolean postTarget
    ) {
        allowAdmin();

        AdminReportReadQuery query = query(0, 20);
        ContentReport report = report(postTarget, 1L, 10L, "본문");

        when(reportReadRepository.findReports(query))
                .thenReturn(new PageImpl<>(List.of(report)));

        if (postTarget) {
            when(targetReadRepository.findPosts(Set.of(10L)))
                    .thenReturn(Map.of());
        } else {
            when(targetReadRepository.findComments(Set.of(10L)))
                    .thenReturn(Map.of());
        }

        service.getReports(ACTOR_ID, VERSION, query);

        if (postTarget) {
            verify(targetReadRepository).findPosts(Set.of(10L));
        } else {
            verify(targetReadRepository).findComments(Set.of(10L));
        }

        verifyNoMoreInteractions(targetReadRepository);
    }

    @Test
    void 범위를_벗어난_빈_페이지는_대상을_조회하지_않는다() {
        allowAdmin();

        AdminReportReadQuery query = query(3, 20);

        when(reportReadRepository.findReports(query))
                .thenReturn(new PageImpl<>(
                        List.of(),
                        PageRequest.of(3, 20),
                        45
                ));

        Page<AdminReportItemResult> result =
                service.getReports(ACTOR_ID, VERSION, query);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getNumber()).isEqualTo(3);
        assertThat(result.getTotalElements()).isEqualTo(45);
        assertThat(result.getTotalPages()).isEqualTo(3);

        verifyNoInteractions(targetReadRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 상세에서_신고_스냅샷과_현재_콘텐츠를_구분한다(
            boolean postTarget
    ) {
        allowAdmin();

        String snapshot = "신고 당시 본문\n" + "가".repeat(130);

        ContentReport report =
                report(postTarget, 100L, 7L, snapshot);
        AdminReportCurrentTargetResult target =
                currentTarget(postTarget, 7L);

        when(reportReadRepository.findById(100L))
                .thenReturn(Optional.of(report));

        if (postTarget) {
            when(targetReadRepository.findPosts(Set.of(7L)))
                    .thenReturn(Map.of(7L, target));
        } else {
            when(targetReadRepository.findComments(Set.of(7L)))
                    .thenReturn(Map.of(7L, target));
        }

        AdminReportDetailResult result =
                service.getReport(ACTOR_ID, VERSION, 100L);

        assertThat(result.reportId()).isEqualTo(100L);
        assertThat(result.reporterMemberId()).isEqualTo(REPORTER_ID);
        assertThat(result.targetId()).isEqualTo(7L);
        assertThat(result.targetType()).isEqualTo(
                postTarget
                        ? AdminReportTargetType.POST
                        : AdminReportTargetType.COMMENT
        );
        assertThat(result.status()).isEqualTo(ContentReportStatus.RECEIVED);
        assertThat(result.reasonType()).isEqualTo(ContentReportReasonType.OTHER);
        assertThat(result.reasonDetail()).isEqualTo("검토 요청");
        assertThat(result.createdAt()).isEqualTo(NOW);

        assertThat(result.titleSnapshot()).isEqualTo(
                postTarget ? "접수 당시 제목" : null
        );
        assertThat(result.contentSnapshot()).isEqualTo(snapshot);
        assertThat(result.currentTarget()).isEqualTo(target);
        assertThat(result.currentTarget().content())
                .isEqualTo("현재 본문\n수정된 내용");

        if (postTarget) {
            assertThat(result.currentTarget().parentPostId()).isNull();
            verify(targetReadRepository).findPosts(Set.of(7L));
        } else {
            assertThat(result.currentTarget().parentPostId()).isEqualTo(500L);
            verify(targetReadRepository).findComments(Set.of(7L));
        }

        verifyNoMoreInteractions(targetReadRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 상세의_현재_대상이_없어도_신고는_반환한다(
            boolean postTarget
    ) {
        allowAdmin();

        when(reportReadRepository.findById(100L))
                .thenReturn(Optional.of(
                        report(postTarget, 100L, 7L, "보존된 스냅샷")
                ));

        if (postTarget) {
            when(targetReadRepository.findPosts(Set.of(7L)))
                    .thenReturn(Map.of());
        } else {
            when(targetReadRepository.findComments(Set.of(7L)))
                    .thenReturn(Map.of());
        }

        AdminReportDetailResult result =
                service.getReport(ACTOR_ID, VERSION, 100L);

        assertThat(result.reportId()).isEqualTo(100L);
        assertThat(result.contentSnapshot()).isEqualTo("보존된 스냅샷");
        assertThat(result.currentTarget()).isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void 잘못된_신고_ID는_관리자_검증_후_거부한다(Long reportId) {
        allowAdmin();

        assertThatThrownBy(() ->
                service.getReport(ACTOR_ID, VERSION, reportId)
        ).isInstanceOfSatisfying(
                AdminReportReadException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                AdminReportReadException.Code.REPORT_NOT_FOUND
                        )
        );

        verify(memberRepository).findById(ACTOR_ID);
        verifyNoInteractions(reportReadRepository, targetReadRepository);
    }

    @Test
    void 신고가_없으면_현재_대상을_조회하지_않는다() {
        allowAdmin();

        when(reportReadRepository.findById(100L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.getReport(ACTOR_ID, VERSION, 100L)
        ).isInstanceOfSatisfying(
                AdminReportReadException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(
                                AdminReportReadException.Code.REPORT_NOT_FOUND
                        )
        );

        verifyNoInteractions(targetReadRepository);
    }

    @ParameterizedTest
    @MethodSource("invalidAccessCases")
    void 유효하지_않은_요청자는_신고_조회_전에_거부한다(
            AccessCase access,
            boolean detail
    ) {
        boolean validPrincipalInput =
                access.actorId() != null
                        && access.actorId() > 0
                        && access.requestVersion() >= 0;

        if (validPrincipalInput) {
            if (!access.memberExists()) {
                when(memberRepository.findById(access.actorId()))
                        .thenReturn(Optional.empty());
            } else {
                Member actor = mock(Member.class);

                when(memberRepository.findById(access.actorId()))
                        .thenReturn(Optional.of(actor));
                when(actor.getStatus()).thenReturn(access.status());

                if (access.status() == MemberStatus.ACTIVE) {
                    when(actor.getCredentialVersion())
                            .thenReturn(access.actualVersion());

                    if (access.actualVersion() == access.requestVersion()) {
                        when(actor.getRole()).thenReturn(access.role());
                    }
                }
            }
        }

        assertThatThrownBy(() -> {
            if (detail) {
                // 잘못된 대상 ID보다 요청자 인증을 먼저 검증해야 한다.
                service.getReport(
                        access.actorId(),
                        access.requestVersion(),
                        null
                );
            } else {
                service.getReports(
                        access.actorId(),
                        access.requestVersion(),
                        query(0, 20)
                );
            }
        }).isInstanceOfSatisfying(
                AdminReportReadException.class,
                exception -> assertThat(exception.getCode())
                        .isEqualTo(access.expectedCode())
        );

        verifyNoInteractions(reportReadRepository, targetReadRepository);

        if (validPrincipalInput) {
            verify(memberRepository).findById(access.actorId());
        } else {
            verifyNoInteractions(memberRepository);
        }
    }

    private static Stream<Arguments> invalidAccessCases() {
        var authenticationRequired =
                AdminReportReadException.Code.AUTHENTICATION_REQUIRED;
        var adminRequired =
                AdminReportReadException.Code.ADMIN_REQUIRED;

        List<AccessCase> cases = new ArrayList<>();

        for (Long actorId : new Long[]{null, 0L, -1L}) {
            cases.add(new AccessCase(
                    actorId, VERSION, true,
                    MemberStatus.ACTIVE, VERSION, MemberRole.ADMIN,
                    authenticationRequired
            ));
        }

        cases.add(new AccessCase(
                ACTOR_ID, -1L, true,
                MemberStatus.ACTIVE, VERSION, MemberRole.ADMIN,
                authenticationRequired
        ));

        cases.add(new AccessCase(
                ACTOR_ID, VERSION, false,
                MemberStatus.ACTIVE, VERSION, MemberRole.ADMIN,
                authenticationRequired
        ));

        for (MemberStatus status : MemberStatus.values()) {
            if (status != MemberStatus.ACTIVE) {
                cases.add(new AccessCase(
                        ACTOR_ID, VERSION, true,
                        status, VERSION, MemberRole.ADMIN,
                        authenticationRequired
                ));
            }
        }

        cases.add(new AccessCase(
                ACTOR_ID, VERSION, true,
                MemberStatus.ACTIVE, VERSION + 1, MemberRole.ADMIN,
                authenticationRequired
        ));

        // 권한도 없고 버전도 오래된 경우에는 인증 실패가 우선이다.
        cases.add(new AccessCase(
                ACTOR_ID, VERSION, true,
                MemberStatus.ACTIVE, VERSION + 1, MemberRole.USER,
                authenticationRequired
        ));

        cases.add(new AccessCase(
                ACTOR_ID, VERSION, true,
                MemberStatus.ACTIVE, VERSION, MemberRole.USER,
                adminRequired
        ));

        return cases.stream()
                .flatMap(access -> Stream.of(
                        Arguments.of(access, false),
                        Arguments.of(access, true)
                ));
    }

    private void allowAdmin() {
        Member actor = mock(Member.class);

        when(memberRepository.findById(ACTOR_ID))
                .thenReturn(Optional.of(actor));
        when(actor.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(actor.getCredentialVersion()).thenReturn(VERSION);
        when(actor.getRole()).thenReturn(MemberRole.ADMIN);
    }

    private AdminReportReadQuery query(int page, int size) {
        return new AdminReportReadQuery(
                null,
                null,
                null,
                null,
                null,
                page,
                size,
                AdminReportSort.LATEST
        );
    }

    private ContentReport report(
            boolean postTarget,
            Long reportId,
            Long targetId,
            String snapshot
    ) {
        ContentReport report = postTarget
                ? ContentReport.forPost(
                REPORTER_ID,
                targetId,
                ContentReportReasonType.OTHER,
                "  검토 요청  ",
                "접수 당시 제목",
                snapshot,
                NOW
        )
                : ContentReport.forComment(
                REPORTER_ID,
                targetId,
                ContentReportReasonType.OTHER,
                "  검토 요청  ",
                snapshot,
                NOW
        );

        ReflectionTestUtils.setField(report, "id", reportId);

        return report;
    }

    private AdminReportCurrentTargetResult currentTarget(
            boolean postTarget,
            Long targetId
    ) {
        return new AdminReportCurrentTargetResult(
                targetId,
                postTarget ? null : 500L,
                new AdminReportTargetAuthorResult(
                        postTarget ? 11L : 12L,
                        postTarget ? "현재게시글작성자" : "현재댓글작성자",
                        false
                ),
                postTarget ? "현재 제목" : null,
                "현재 본문\n수정된 내용",
                NOW.plusSeconds(60),
                null
        );
    }

    private record AccessCase(
            Long actorId,
            long requestVersion,
            boolean memberExists,
            MemberStatus status,
            long actualVersion,
            MemberRole role,
            AdminReportReadException.Code expectedCode
    ) {
    }
}