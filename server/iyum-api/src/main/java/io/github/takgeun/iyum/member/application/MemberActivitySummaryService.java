package io.github.takgeun.iyum.member.application;

import io.github.takgeun.iyum.community.comment.infrastructure.CommentRepository;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberActivitySummaryService {

    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    @Transactional(readOnly = true)
    public MemberActivitySummaryResult getSummary(Long memberId) {

        if(memberId == null) {
            throw new MemberActivityReadException(
                    MemberActivityReadException.Code.MEMBER_NOT_FOUND
            );
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new MemberActivityReadException(
                                MemberActivityReadException.Code.MEMBER_NOT_FOUND
                        )
                );

        if(member.getStatus() != MemberStatus.ACTIVE) {
            throw new MemberActivityReadException(
                    MemberActivityReadException.Code.MEMBER_NOT_ACTIVE
            );
        }

        long postCount =
                postRepository.countNotDeletedPostsByAuthorId(memberId);

        // 댓글 수는 기존 조회를 유지한다,.
        // 이렇게 해야 내 게시글 탭의 N에도 숨김 항목이 포함된다.
        long commentCount =
                commentRepository.countNotDeletedCommentsByAuthorId(memberId);

        return new MemberActivitySummaryResult(
                postCount,
                commentCount
        );
    }
}
