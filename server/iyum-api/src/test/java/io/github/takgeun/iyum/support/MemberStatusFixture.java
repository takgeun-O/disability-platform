package io.github.takgeun.iyum.support;

import io.github.takgeun.iyum.member.domain.MemberStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/** Test database fixtures obey V7 even when preparing non-loginable enum states. */
public final class MemberStatusFixture {
    private MemberStatusFixture() {}

    public static void setStatus(JdbcTemplate jdbc, Long memberId, MemberStatus status) {
        if (status == MemberStatus.DELETED) {
            jdbc.update("UPDATE members SET status = ?, withdrawn_at = CURRENT_TIMESTAMP, "
                    + "email = NULL, password_hash = NULL, nickname = NULL WHERE id = ?",
                    status.name(), memberId);
        } else if (status == MemberStatus.WITHDRAWN) {
            jdbc.update("UPDATE members SET status = ?, withdrawn_at = CURRENT_TIMESTAMP WHERE id = ?",
                    status.name(), memberId);
        } else {
            jdbc.update("UPDATE members SET status = ?, withdrawn_at = NULL WHERE id = ?",
                    status.name(), memberId);
        }
    }
}
