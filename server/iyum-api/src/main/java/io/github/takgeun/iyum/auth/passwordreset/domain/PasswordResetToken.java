package io.github.takgeun.iyum.auth.passwordreset.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "password_reset_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken {

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

    // 같은 토큰 행에 대한 동시 수정 충돌 감지
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    private PasswordResetToken(
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
                    "토튼 해시는 소문자 16진수 64자리여야 합니다."
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

    public static PasswordResetToken issue(
            Long memberId,
            String tokenHash,
            Instant createdAt,
            Instant expiresAt
    ) {
        return new PasswordResetToken(
                memberId,
                tokenHash,
                createdAt,
                expiresAt
        );
    }

    public boolean isUsable(Instant now) {
        if(now == null) {
            throw new IllegalArgumentException(
                    "검사 시각이 필요합니다."
            );
        }

        // 현재가 발급 시각보다 이전이 아니어야 하고
        // 현재가 만료 시각보다 이전이어야 하고
        // 아직 사용하지 않았으며, 아직 폐기되지 않아야 한다.
        return !now.isBefore(createdAt)
                && now.isBefore(expiresAt)
                && usedAt == null
                && revokedAt == null;
    }

    // 비밀번호 변경에 사용했음을 기록한다. -> usedAt 설정
    // 참고로 use()는 링크를 열었을 떄 호출하지 않고
    // 비밀번호 변경 서비스의 트랜잭션 안에서 호출될 것
    public void use(Instant now) {
        if (!isUsable(now)) {
            throw new IllegalStateException(
                    "사용할 수 없는 비밀번호 재설정 토큰입니다."
            );
        }
        this.usedAt = now;
    }

    // 재발급 등 이유로 폐기 -> revoked 설정
    public void revoke(Instant now) {
        if(now == null || now.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "폐기 시각은 발급 시각보다 빠를 수 없습니다."
            );
        }

        // 이미 사용하거나 폐기한 토큰의 기록은 변경하지 않음
        if(usedAt != null || revokedAt != null) {
            return;
        }

        this.revokedAt = now;
    }
}
