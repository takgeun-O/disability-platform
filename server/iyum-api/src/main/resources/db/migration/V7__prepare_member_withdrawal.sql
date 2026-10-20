-- 탈퇴한 회원의 계정 식별·인증 정보를 제거할 수 있도록 허용
-- NOT NULL 제약을 없애더라도 활성 회원의 필수값 검증이 사라지지 않게 하는 것이 핵심
ALTER TABLE members
    ALTER COLUMN email DROP NOT NULL,
    ALTER COLUMN password_hash DROP NOT NULL,
    ALTER COLUMN nickname DROP NOT NULL;

-- 탈퇴 처리 시각 추가
ALTER TABLE members
    ADD COLUMN withdrawn_at TIMESTAMPTZ;

-- 개인정보 정리 완료 상태에서는 계정 정보를 남기지 않는다.
-- 그 외 상태에서는 기존과 동일하게 계정 정보가 필요하다.
ALTER TABLE members
    ADD CONSTRAINT ck_members_identity_by_status
        CHECK (
            (
                status = 'DELETED'
                    AND email IS NULL
                    AND password_hash IS NULL
                    AND nickname IS NULL
                )
                OR
            (
                status <> 'DELETED'
                    AND email IS NOT NULL
                    AND password_hash IS NOT NULL
                    AND nickname IS NOT NULL
                )
            );

-- 탈퇴 관련 상태와 탈퇴 시각의 일관성을 보장
ALTER TABLE members
    ADD CONSTRAINT ck_members_withdrawn_at_by_status
        CHECK (
            (
                status IN ('WITHDRAWN', 'DELETED')
                AND withdrawn_at IS NOT NULL
            )
            OR
            (
                status NOT IN ('WITHDRAWN', 'DELETED')
                AND withdrawn_at IS NULL
            )
        );