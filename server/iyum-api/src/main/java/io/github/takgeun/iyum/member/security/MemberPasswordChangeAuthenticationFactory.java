package io.github.takgeun.iyum.member.security;

import io.github.takgeun.iyum.auth.security.MemberPrincipal;
import io.github.takgeun.iyum.member.application.MemberPasswordChangeResult;
import io.github.takgeun.iyum.member.domain.MemberStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 비밀번호를 변경한 현재 사용자가 로그인을 유지하도록,
 * 변경된 인증 버전을 담은 새 인증 객체를 만드는 역할
 * <p>
 * MemberPasswordChangeService가 DB의 빔리번호와 인증 버전을 변경했다면,
 * 이 클래스는 그 결과를 받아 현재 세션에 넣을 인증 정보를 준비하는 단계임
 * <p>
 * 왜 새 인증 객체가 필요할까?
 * 같은 계정으로 PC와 휴대폰에 로그인했다고 가정.
 * 변경 전
 * - DB 인증 버전 : 3
 * - PC 인증 버전 : 3
 * - 휴대폰 인증 버전 : 3
 * PC에서 비밀번호 변경 직후
 * - DB 인증 버전 : 4
 * - PC 인증 버전 : 3 -> 이 때 그대로 냅두면 PC 환경에서 다음 요청 때 인증 버전 불일치로 로그아웃될 수 있음.
 * - 휴대폰 인증 버전 : 3
 * PC 인증 정보 갱신 후
 * - DB 인증 버전 : 4
 * - PC 인증 버전 : 4 -> PC 버전 로그인 유지
 * - 휴대폰 인증 버전 : 3 -> 여전히 버전 불일치가 있으므로 최종적으로 휴대폰 버전에서는 보안 필터에서 거절되어 로그아웃처리되도록 한다.
 * <p>
 * 다만 이 클래스가 직접 세션에 저장하거나 다른 기기를 로그아웃시키지는 않고
 * 새 인증 객체를 만들어 반환하는 데까지만 담당한다.
 */
@Component
public class MemberPasswordChangeAuthenticationFactory {

    public UsernamePasswordAuthenticationToken create(
            Authentication currentAuthentication,
            MemberPasswordChangeResult.Changed changed
    ) {
        // 전달된 인증 객체의 상태 확인 (DB를 다시 조회하는 검사는 서비스에서 이미 담당함)
        if (currentAuthentication == null
                || !currentAuthentication.isAuthenticated()
                || !(currentAuthentication.getPrincipal()
                instanceof MemberPrincipal principal)
                || principal.getStatus() != MemberStatus.ACTIVE
                || !principal.isEnabled()) {
            throw new IllegalArgumentException(
                    "비밀번호 변경 전의 유효한 회원 인증 정보가 필요합니다."
            );
        }

        if (changed == null
                || changed.memberId() == null
                || changed.memberId() <= 0

                // 변경 결과가 바로 이 요청의 결과인지 확인
                // 기존 로그인 회원과 비밀번호 변경에 성공한 회원이 같은지 확인
                || !Objects.equals(principal.getMemberId(), changed.memberId())

                // 이어서 버전도 비교한다
                || principal.getCredentialVersion() != changed.previousCredentialVersion()
        ) {
            throw new IllegalArgumentException(
                    "요청 인증 정보와 비밀번호 변경 결과가 일치하지 않습니다."
            );
        }

        long previousVersion = changed.previousCredentialVersion();

        // 이번 요청이 만든 바로 다음 버전만 허용한다.
        // 다른 변경으로 만들어진 더 최신 버전까지 임의로 받아들이지 않는다.
        // 최댓값 검사를 먼저 해서 덧셈 오버플로도 방지한다.
        if (previousVersion < 0
                // 숫자가 표현 범위를 넘어가는 오버플로를 막기 위한 Long.MAX_VALUE 검사
                || previousVersion == Long.MAX_VALUE

                // 정확히 1 증가한 버전인지 확인
                // 이번 변경이 만든 버전만 현재 인증 정보에 반영하겠다는 의미 (3 -> 5 같은 결과는 반영하지 않음)
                || changed.credentialVersion() != previousVersion + 1
        ) {
            throw new IllegalArgumentException(
                    "비밀번호 변경 결과의 인증 버전이 올바르지 않습니다."
            );
        }

        // Principal은 누가 로그인했는가를 담는 회원 신원 정보다.
        // 기존 Principal은 다른 실행 흐름에서도 참조할 수 있으므로
        // 직접 수정하지 않고 새로운 객체를 생성한다.

        // 새 비밀번호 원문이나 해시를 다시 가져오지 않는다.
        // 이미 비밀번호 검증과 변경이 끝나서 새 인증 객체를 만드는 데에
        // 비밀번호 원문과 해시가 필요하지 않기 때문.
        // 비밀번호 필드는 빈 값으로 생성하고, 반환 전에 제거한다.
        MemberPrincipal refreshPrincipal = new MemberPrincipal(
                changed.memberId(),
                changed.email(),
                "",
                changed.nickname(),
                MemberStatus.ACTIVE,
                changed.role(),
                changed.credentialVersion()
        );

        // 현재 비밀번호 검증과 변경이 성공한 서버 내부 결과를
        // 세션용 인증 객체로 변환하는 단계다.
        // 클라이언트 요청 본문을 Changed로 받아 이 메서드에 전달하면 안된다.
        UsernamePasswordAuthenticationToken refreshAuthentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        refreshPrincipal,   // 새 인증 버전이 담긴 회원 정보
                        null,   // 비밀번호 같은 인증 자격 증명을 보관하지 않음
                        refreshPrincipal.getAuthorities()   // 회원의 권한
                );

        // 인증 객체 뿐만 아니라 Principal에 보관한 비밀번호도 제거한다.
        // MemberPrincipal에 저장된 비밀번호까지 지우려면
        // MemberPrincipal이 CredentialsContainer를 구현하고
        // eraseCredentials()에서 비밀번호를 제거하도록 해야 한다.
        refreshAuthentication.eraseCredentials();

        return refreshAuthentication;
        // 이후 호출자가 새 인증 객체를 SecurityContext에 반영하고 저장해야
        // 현재 세션의 갱신이 완료된다.
    }
}
