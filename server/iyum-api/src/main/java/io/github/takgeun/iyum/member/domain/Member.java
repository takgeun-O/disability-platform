package io.github.takgeun.iyum.member.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// 엔티티에서 저장 및 수정 같은 이벤트가 발생할 때 실행할 리스너 클래스를 지정
// AuditingEntityListener : Spring Data JPA가 제공하며,
// 엔티티의 저장 및 수정 시점을 감지해서 생성일시와 수정일시 등을 자동으로 채워주는 역할을 함
@EntityListeners(AuditingEntityListener.class)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 기본키 값을 데이터베이스가 자동으로 생성하도록 맡기는 전략
    private Long id;

    @Email
    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "nickname", length = 20)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MemberStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private MemberRole role;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "credential_version", nullable = false)
    private long credentialVersion;

    private Member(
            String email,
            String passwordHash,
            String nickname
    ) {
        this.email = MemberInputPolicy.normalizeEmail(email);
        this.nickname = MemberInputPolicy.normalizedNickname(nickname);

        if (passwordHash == null
                || passwordHash.isBlank()
                || passwordHash.length() > 255) {
            throw new IllegalArgumentException(
                    "유효한 비밀번호 해시가 필요합니다."
            );
        }

        this.passwordHash = passwordHash;
        this.status = MemberStatus.PENDING;
        this.role = MemberRole.USER;
    }

    public static Member createPending(
            String email,
            String passwordHash,
            String nickname
    ) {
        return new Member(email, passwordHash, nickname);
    }

    public void activeAfterEmailVerification() {
        if (status != MemberStatus.PENDING) {
            throw new IllegalStateException(
                    "이메일 인증 대기 상태에서만 활성화할 수 있습니다."
            );
        }

        this.status = MemberStatus.ACTIVE;
    }

    public void changeNickname(String nickname) {
        // 서비스에서도 상태를 확인하지만
        // 엔티티 자체에서도 탈퇴한 회원에게 닉네임이 다시 저장되는 것을 막음
        if (this.status != MemberStatus.ACTIVE) {
            throw new IllegalStateException(
                    "활성 회원만 닉네임을 변경할 수 있습니다."
            );
        }
        // 단순 setter와 달리 가입할 때와 동일한 규칙으로 검증한 값만 저장
        // 우변의 검증이 성공해야 대입이 실행되므로 잘못된 닉네임 전달하면 예외 발생 + 기존 닉네임 유지
        this.nickname = MemberInputPolicy.normalizedNickname(nickname);
    }

    public void withdraw(Instant withdrawnAt) {
        // 모든 검증을 먼저 끝내고 필드를 변경한다.
        if (withdrawnAt == null) {
            throw new IllegalArgumentException(
                    "탈퇴 시각은 필수입니다."
            );
        }

        // 정지 계정의 탈퇴 경로는 추후 별도 확장 예정
        if (this.status != MemberStatus.ACTIVE) {
            throw new IllegalStateException(
                    "활성 회원만 탈퇴할 수 있습니다."
            );
        }

        this.email = null;
        this.passwordHash = null;
        this.nickname = null;
        // 서비스에서 정한 시각을 전달하고 테스트에서는 고정된 시각으로 검증하기 위해
        // 메서드 내부에서 Instant.now() 호출하지 않음
        this.withdrawnAt = withdrawnAt;
        this.status = MemberStatus.DELETED;
    }

    // 비밀번호 재설정 메서드
    // 새로운 비밀번호 해시를 저장한다
    // 기존 로그인을 구분하는 credentialVersion을 1 증가시킨다.
    public void resetPassword(String newPasswordHash) {
        if (this.status != MemberStatus.ACTIVE) {
            throw new IllegalStateException(
                    "활성 회원만 비밀번호를 재설정할 수 있습니다."
            );
        }

        if (newPasswordHash == null
                || newPasswordHash.isBlank()
                || newPasswordHash.length() > 255) {
            throw new IllegalArgumentException(
                    "유효한 비밀번호 해시가 필요합니다."
            );
        }

        // 증가할 수 있는지 먼저 계산한다.
        // 범위를 초과하면 필드를 변경하기 전에 예외가 발생한다.
        long nextCredentialVersion =
                Math.addExact(this.credentialVersion, 1L);

        this.passwordHash = newPasswordHash;
        this.credentialVersion = nextCredentialVersion;
    }
}
