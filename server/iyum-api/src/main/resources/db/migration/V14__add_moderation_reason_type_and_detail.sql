-- 기존 자유 입력 사유(reason)는 보존한다.
ALTER TABLE admin_content_moderation_logs
    ADD COLUMN reason_type VARCHAR(40),
    ADD COLuMN reason_detail VARCHAR(500);

-- 기존 기록은 자동 분류하지 않고 LEGACY로 구분한다.
UPDATE admin_content_moderation_logs
SET reason_type = 'LEGACY';

ALTER TABLE admin_content_moderation_logs
    ALTER COLUMN reason_type SET NOT NULL;

-- DB 기본값은 지정하지 않는다.
-- 이후 저장하는 코드가 사유 유형을 명시하도록 한다.
ALTER TABLE admin_content_moderation_logs
    ADD CONSTRAINT ck_moderation_log_reason_type
        CHECK (
            reason_type IN (
                            'SPAM_ADVERTISING',
                            'ABUSE_HATE',
                            'PERSONAL_INFORMATION',
                            'DUPLICATE_FLOODING',
                            'OFF_TOPIC',
                            'MODERATION_ERROR',
                            'NO_VIOLATION',
                            'OTHER',
                            'LEGACY'
                )
            );
