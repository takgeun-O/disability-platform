-- 본문 수정 시각을 엔티티 전체 변경 시각(updated_at)과 분리한다.
-- 신규 댓글과 과거 본문 수정 시각을 확인할 수 없는 댓글은 NULL로 둔다.
-- 기존 updated_at에는 숨김·복원 등 운영 조치 시각도 포함되므로 복사하지 않는다.
ALTER TABLE community_comments
    ADD COLUMN content_updated_at TIMESTAMPTZ;

COMMENT ON COLUMN community_comments.content_updated_at IS
        '별도로 기록한 마지막 본문 수정 시각. 신규 댓글 및 과거 수정 시각 미확인 데이터는 NULL';
