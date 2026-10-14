package io.github.takgeun.iyum.community.post.application;

import io.github.takgeun.iyum.community.post.domain.Post;
import io.github.takgeun.iyum.community.post.infrastructure.PostRepository;
import io.github.takgeun.iyum.member.domain.Member;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import io.github.takgeun.iyum.member.infrastructure.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostCreateService {

    private final MemberRepository memberRepository;
    private final PostRepository postRepository;

    // 회원 조회부터 게시글 저장까지 모두 같은 트랜잭션으로 처리한다.
    @Transactional
    public PostCreateResult create(
            Long authorId,
            PostCreateCommand command
    ) {
        if(authorId == null) {
            throw new PostCreateException(
                    PostCreateException.Code.AUTHOR_NOT_FOUND
            );
        }

        // 1. DB에서 작성자를 다시 조회
        //  로그인 세션에는 로그인 당시 정보가 들어 있을 수 있음.
        //  로그인 후 회원 상태가 정지 상태로 바뀌었을 수도 있으므로
        //  게시글 작성 시에는 DB의 현재 상태를 확인하는 것이 맞음.
        Member author = memberRepository

                // 회원 행에 비관적 쓰기 잠금을 걸기 떄문에
                // 이번 트랜잭션이 끝날 떄까지 다른 트랜잭션의 해당 회원 행 수정·삭제가 대기하게 된다.
                // 회원 상태를 확인한 직후 상태가 바뀌는 상황을 제어하기 위한 선택
                .findByIdForUpdate(authorId)
                .orElseThrow(() ->
                        new PostCreateException(
                                PostCreateException.Code.AUTHOR_NOT_FOUND
                        )
                );

        // 현재 상태가 ACTIVE인지 검사
        // ACTIVE 외 모두 거절
        if(author.getStatus() != MemberStatus.ACTIVE) {
            throw new PostCreateException(
                    PostCreateException.Code.AUTHOR_NOT_ACTIVE
            );
        }

        // 엔티티 생성 후 저장
        // Post.create() 안에서 기존 PostInputPolicy를 사용
        // -> 제목·본문 규칙도 다시 적용됨
        Post post = Post.create(
                author,
                command.type(),
                command.topic(),
                command.title(),
                command.content()
        );

        Post savedPost = postRepository.save(post);

        return new PostCreateResult(savedPost.getId());
    }
}
