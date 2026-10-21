package io.github.takgeun.iyum.auth.security;

import io.github.takgeun.iyum.member.domain.MemberRole;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import lombok.Getter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.io.Serial;
import java.util.List;

/**
 * Principal : 인증 대상 사용자를 나타내는 객체
 * 이 객체는 인증 내부에서 사용할 객체다. 컨트롤러 응답으로 그대로 반환하지 않고
 * 응답 전용 DTO를 사용할 것.
 * 참고) User : Spring Security가 제공하는 클래스
 */
@Getter
public class MemberPrincipal extends User {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long memberId;
    private final String nickname;
    private final MemberStatus status;
    private final MemberRole role;

    private final long credentialVersion;

    public MemberPrincipal(
            Long memberId,
            String email,
            String passwordHash,
            String nickname,
            MemberStatus status,
            MemberRole role,
            // 이 값은 로그인 당시 버전이다. 이후 DB값이 바뀌어도 기존 세션 안의 버전은 그대로 남아 있어야
            // 이전 로그인인지 구분할 수 있음.
            long credentialVersion
    ) {
        super(
                email,
                passwordHash,
                status == MemberStatus.ACTIVE,  // 계정 활성 여부
                true,                                   // 계정 만료 정책 미적용
                true,                                   // 비밀번호 만료 정책 미적용
                true,                                   // 별도의 계정 잠금 정책 미적용
                List.of(
                        // Spring Security에서 사용할 권한
                        new SimpleGrantedAuthority(
                                // DB의 권한을 변환한다.
                                // DB의 권한 USER -> ROLE_USER 이런 식으로
                                "ROLE_" + role.name()
                        )
                )
        );

        if(credentialVersion < 0) {
            throw new IllegalArgumentException(
                    "인증 버전은 0 이상이어야 합니다."
            );
        }

        this.memberId = memberId;
        this.nickname = nickname;
        this.status = status;
        this.role = role;
        this.credentialVersion = credentialVersion;
    }
}
