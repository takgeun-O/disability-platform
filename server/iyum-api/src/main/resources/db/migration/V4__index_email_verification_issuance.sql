-- 회원별 최근 발급 시각 및 최근 1시간 발급 횟수 조회용. 기존 이력은 그대로 유지한다.
CREATE INDEX idx_email_verification_tokens_member_created
    ON email_verification_tokens (member_id, created_at DESC);
