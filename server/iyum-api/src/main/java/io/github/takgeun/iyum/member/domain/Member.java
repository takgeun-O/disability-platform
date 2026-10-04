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
    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "nickname", nullable = false, length = 20)
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
        if(status != MemberStatus.PENDING) {
            throw new IllegalStateException(
                    "이메일 인증 대기 상태에서만 활성화할 수 있습니다."
            );
        }

        this.status = MemberStatus.ACTIVE;
    }
}
