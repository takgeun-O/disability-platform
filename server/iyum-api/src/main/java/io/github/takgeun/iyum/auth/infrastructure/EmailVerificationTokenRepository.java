package io.github.takgeun.iyum.auth.infrastructure;

import io.github.takgeun.iyum.auth.domain.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationToken, Long> {

    /**
     * 사용자가 원본 토큰을 제출하면 서버가 그 값을 해시하고,
     * findByTokenHash()로 해당 기록을 찾는다.
     */
    Optional<EmailVerificationToken> findByTokenHash(
            String tokenHash
    );
}
