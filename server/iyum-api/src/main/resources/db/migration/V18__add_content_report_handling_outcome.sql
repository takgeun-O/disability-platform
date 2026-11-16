ALTER TABLE content_reports
    ADD COLUMN handling_outcome VARCHAR(32);

-- 기존 종결 기록의 처리 관리자·시각·사유는 그대로 유지한다.
-- 현재 콘텐츠 상태를 근거로 과거 조치 결과를 추정하지 않는다.
UPDATE content_reports
SET handling_outcome = 'LEGACY_UNKNOWN'
WHERE status IN ('RESOLVED', 'DISMISSED');

ALTER TABLE content_reports
    ADD CONSTRAINT ck_content_reports_handling_outcome
        CHECK (
            (
                status = 'RECEIVED'
                    AND handling_outcome IS NULL
                )
                OR
            (
                status = 'RESOLVED'
                    AND handling_outcome IS NOT NULL
                    AND handling_outcome IN (
                                             'CONTENT_HIDDEN',
                                             'CONTENT_ALREADY_HIDDEN',
                                             'NO_CONTENT_ACTION',
                                             'LEGACY_UNKNOWN'
                    )
                )
                OR
            (
                status = 'DISMISSED'
                    AND handling_outcome IS NOT NULL
                    AND handling_outcome IN (
                                             'NO_CONTENT_ACTION',
                                             'LEGACY_UNKNOWN'
                    )
                )
            );