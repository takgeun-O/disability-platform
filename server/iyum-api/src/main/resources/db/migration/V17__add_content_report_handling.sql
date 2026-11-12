-- 기존 종결 기록에 가짜 처리 관리자·시각·사유를 채우지 않는다.
-- 기존 데이터가 발견되면 이행 방법을 정한 뒤 적용한다.
-- DO $$ ... $$: 이름 있는 함수를 만들지 않고 절차형 코드를 한번 실행
DO $$
BEGIN
--     조건에 맞는 행이 하나라도 있는가?
    IF EXISTS (
        SELECT 1
        FROM content_reports
        WHERE status IN ('RESOLVED', 'DISMISSED')
    ) THEN
--         오류를 발생시켜 진행 중단시키기
        RAISE EXCEPTION
            'Existing terminal content reports require an explicit handling-data migration.';
END IF;
END
$$;

ALTER TABLE content_reports
    ADD COLUMN handled_by_member_id BIGINT,
    ADD COLUMN handled_at TIMESTAMPTZ,
    ADD COLUMN handling_reason VARCHAR(500);

ALTER TABLE content_reports
    ADD CONSTRAINT fk_content_reports_handler
        FOREIGN KEY (handled_by_member_id)
            REFERENCES members (id)
            ON DELETE RESTRICT,

    ADD CONSTRAINT ck_content_reports_handling_state
        CHECK (
            (
--              status가 RECEIVED라면 처리 정보는 모두 null이어야 통과
                status <> 'RECEIVED'
                OR (
                    handled_by_member_id IS NULL
                    AND handled_at IS NULL
                    AND handling_reason IS NULL
                )
            )
            AND
            (
--              status가 RESOLVED나 DISMISSED라면 처리 정보가 세 개 모두 있어야 통과
                status NOT IN ('RESOLVED', 'DISMISSED')
                OR (
                    handled_by_member_id IS NOT NULL
                    AND handled_at IS NOT NULL
                    AND handling_reason IS NOT NULL
                )
            )
        ),

    ADD CONSTRAINT ck_content_reports_handling_reason
        CHECK (
--             처리 사유는 공백만으로 작성할 수 없다.
            handling_reason IS NULL
            OR (
                char_length(handling_reason) BETWEEN 1 AND 500
                AND char_length(
--                  btrim: 탭, 줄바꿈, 일반 공백 등 공백으로 취급할 문자들을 문자열 양 끝에서 제거
                    btrim(
                        handling_reason,
                        U&'\0009\000A\000B\000C\000D\0020\0085\00A0\1680\2000\2001\2002\2003\2004\2005\2006\2007\2008\2009\200A\2028\2029\202F\205F\3000'
                    )
                ) > 0
            )
        );

-- 처리자가 있는 신고에만 인덱스 생성 (처리자가 없는 접수 신고는 인덱스에서 제외)
-- SELECT *
-- FROM content_reports
-- WHERE handled_by_member_id = 7;
-- 위처럼 특정 관리자가 처리한 신고를 찾는 데에 도움이 된다.
CREATE INDEX idx_content_reports_handler
    ON content_reports (handled_by_member_id)
    WHERE handled_by_member_id IS NOT NULL;