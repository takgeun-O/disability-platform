package io.github.takgeun.iyum.admin.moderation.infrastructure;

import io.github.takgeun.iyum.admin.moderation.domain.ContentModerationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentModerationLogRepository
        extends JpaRepository<ContentModerationLog, Long> {
}
