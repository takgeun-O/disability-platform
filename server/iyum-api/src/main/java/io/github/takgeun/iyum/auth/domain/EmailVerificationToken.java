package io.github.takgeun.iyum.auth.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "email_verification_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(
            name = "token_hash",
            nullable = false,
            updatable = false,
            length = 64
    )
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Version    // 두 요청이 같은 토큰을 읽은 뒤 수정할 때, 먼저 반영된 변경을 나중 요청이 오래된 값으로 덮어쓰는 것을 감지한다.
    @Column(name = "version", nullable = false)
    private long version;

    private EmailVerificationToken(
            Long memberId,
            String tokenHash,
            Instant createdAt,
            Instant expiresAt
    ) {
        if (memberId == null || memberId <= 0) {
            throw new IllegalArgumentException(
                    "유효한 회원 ID가 필요합니다."
            );
        }

        if (tokenHash == null
                || !tokenHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "토큰 해시는 소문자 16진수 64자리여야 합니다."
            );
        }

        if(createdAt == null || expiresAt == null) {
            throw new IllegalArgumentException(
                    "발급 시각과 만료 시각이 필요합니다."
            );
        }

        if(!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException(
                    "만료 시각은 발급 시각보다 늦어야 합니다."
            );
        }

        this.memberId = memberId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public static EmailVerificationToken issue(
            Long memberId,
            String tokenHash,
            Instant createdAt,
            Instant expiresAt
    ) {
        return new EmailVerificationToken(
                memberId, tokenHash, createdAt, expiresAt
        );
    }

    public boolean isUsable(Instant now) {
        if(now == null) {
            throw new IllegalArgumentException(
                    "검사 시각이 필요합니다."
            );
        }

        return !now.isBefore(createdAt)
                && now.isBefore(expiresAt)
                && usedAt == null
                && revokedAt == null;
    }

    public void use(Instant now) {
        if(!isUsable(now)) {
            throw new IllegalStateException(
                    "사용할 수 없는 이메일 인증 토큰입니다."
            );
        }

        this.usedAt = now;
    }

    public void revoke(Instant now) {
        if(now == null || now.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "폐기 시각은 발급 시각보다 빠를 수 없습니다."
            );
        }

        // 이미 사용했거나 폐기한 기록은 그대로 유지합니다.
        if(usedAt != null || revokedAt != null) {
            return;
        }

        this.revokedAt = now;
    }
}
