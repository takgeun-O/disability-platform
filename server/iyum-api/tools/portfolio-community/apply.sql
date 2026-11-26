-- Manual only; requires PostgreSQL 17, V19 schema and psql.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL search_path = pg_catalog, public;
SELECT pg_advisory_xact_lock(hashtextextended('iyum-portfolio-community-v1', 0));
CREATE TABLE IF NOT EXISTS public.iyum_portfolio_seed_runs (
    seed_key text PRIMARY KEY,
    applied_at timestamptz NOT NULL DEFAULT transaction_timestamp(),
    member_ids bigint[] NOT NULL,
    post_ids bigint[] NOT NULL,
    comment_ids bigint[] NOT NULL,
    post_like_ids bigint[] NOT NULL,
    comment_like_ids bigint[] NOT NULL
);
SELECT NOT EXISTS (SELECT FROM public.iyum_portfolio_seed_runs
                   WHERE seed_key = 'community-v1') AS fresh \gset
\if :fresh
-- Derive a Spring-compatible hash from a one-time random secret; discard the secret.
\set demo_hash `python3 -c "import secrets,hashlib; s=secrets.token_bytes(16); print('{pbkdf2@SpringSecurity_v5_8}'+(s+hashlib.pbkdf2_hmac('sha256',secrets.token_bytes(32),s,310000)).hex())"`
SELECT length(:'demo_hash') = 124 AS hash_ok \gset
\if :hash_ok
\else
  DO $$ BEGIN RAISE EXCEPTION 'Could not generate demo password hash'; END $$;
\endif
CREATE TEMP TABLE demo_members (n int PRIMARY KEY, id bigint UNIQUE) ON COMMIT DROP;
WITH names AS (
    SELECT nickname, n FROM unnest(ARRAY['모눈','물결','여울','다래','보늬','해솔','마루',
        '풀잎','소보로','가람','연두','해바람','산책길','별숲']) WITH ORDINALITY AS t(nickname,n)
), inserted AS (
    INSERT INTO public.members(email,password_hash,nickname,status,role,created_at,updated_at)
    SELECT 'portfolio-v1-' || n || '@iyum.example.invalid', :'demo_hash', '데모_' || nickname,
           'ACTIVE','USER',now()-interval '30 days',now()-interval '30 days' FROM names
    RETURNING id,nickname
)
INSERT INTO demo_members SELECT n,id FROM names JOIN inserted ON inserted.nickname = '데모_' || names.nickname;

-- Stable fixture numbers identify rows in this transaction. Public IDs come from the DB.
CREATE TEMP TABLE demo_posts (
    n int PRIMARY KEY, writer int NOT NULL, kind text NOT NULL, topic text NOT NULL,
    title text UNIQUE NOT NULL, body text NOT NULL, votes int NOT NULL
) ON COMMIT DROP;
INSERT INTO demo_posts VALUES
(1,1,'QUESTION','EMPLOYMENT','여러 명이 동시에 말하는 회의, 실시간 자막은 어떻게 활용하세요?',
 '한 사람이 발표할 때는 화면을 따라가기 쉬운데 토론으로 넘어가면 누가 한 말인지 자주 놓쳐요. 어제는 결정 사항을 반대로 이해했다가 회의록을 보고 알았습니다. 발언 순서나 기록 방법을 팀에 제안해 보신 분 계신가요?',12),
(2,2,'REVIEW','DAILY','소극장 맨 뒷줄에서 두 장면을 다 본 날',
 '예매할 때는 무대와 가까울수록 좋다고 생각했어요. 그런데 좌석표를 들고 매표소에 물어보니 맨 앞에서는 무대 양옆을 번갈아 봐야 한다고 하더라고요. 뒤쪽 통로석에서는 배우의 얼굴과 자막 화면이 한눈에 들어왔습니다. 다음 공연도 예매 전에 화면 위치와 난간 높이부터 물어보려고요.',10),
(3,3,'QUESTION','COMMUNICATION','강의 필기와 화면 읽기, 손을 언제 멈추시나요?',
 '칠판을 베끼는 사이 다음 설명을 놓쳐요. 이번 주부터 슬라이드 번호와 물음표만 적어 두려는데 복습할 때 맥락이 기억날지 걱정됩니다. 오래 써 본 필기 규칙이 궁금해요.',7),
(4,4,'GENERAL','WELFARE_LIFE','처음 가는 도서관에 미리 물어본 세 가지',
 '예약 확인은 문자로 받을 수 있는지, 휴관 안내는 어디에 뜨는지, 안내 방송을 놓치면 어느 데스크에 가면 되는지 물었습니다. 메모 한 장 덕분에 길을 헤매도 마음이 덜 급했어요.',3),
(5,5,'REVIEW','DAILY','영상 자막 위치를 바꾸니 손동작이 보였어요',
 '뜨개질 영상을 보는데 실을 넘기는 순간마다 손이 글씨에 가려졌어요. 재생기의 글씨 위치를 위로 옮기니 같은 장면을 되감을 일이 줄었습니다. 소리 설명이 필요한 구간은 시간도 함께 적어 뒀어요.',5),
(6,6,'QUESTION','HEARING_AID','비 오는 날 외출 가방에는 무엇을 챙기세요?',
 '갑자기 소나기가 와서 가방 속 보관함을 찾느라 한참 서 있었어요. 젖은 우산과 작은 물건을 따로 두는 자기만의 수납법이 있다면 듣고 싶어요.',2),
(7,7,'GENERAL','EMPLOYMENT','첫 출근 전에 팀 채팅방에서 나눈 작은 약속',
 '자리에서 불러도 반응이 없으면 채팅으로 이름을 한번 남겨 달라고 했어요. 동료도 집중할 때는 같은 방식이 편하다며 웃더라고요. 첫날 먼저 꺼내길 잘한 이야기였습니다.',6),
(8,8,'REVIEW','COMMUNICATION','카페에서 주문 번호를 놓치지 않았던 자리',
 '직원이 보이는 자리보다 번호판이 잘 보이는 쪽이 제게는 편했어요. 창문 빛이 반사되는 시간도 있어서 주문하고 나서 잠깐 앉아 보고 자리를 골랐습니다.',2),
(9,9,'QUESTION','DAILY','야외 영화제 좌석을 고를 때 무엇부터 보세요?',
 '돗자리를 펼칠 수 있는 구역과 의자 구역이 따로 있더라고요. 뒤쪽에서도 안내 화면이 보이는지 궁금해 운영팀에 문의하려는데 어떤 질문을 더하면 좋을까요?',1),
(10,10,'GENERAL','COMMUNICATION','동아리 첫 모임에서 이름을 기억하는 방법',
 '자기소개가 끝나고 종이에 이름과 오늘 이야기하고 싶은 주제를 같이 적었어요. 다시 묻기 미안할 때 종이를 한번 보면 돼서 두 번째 대화가 훨씬 편했습니다.',4),
(11,11,'REVIEW','WELFARE_LIFE','전시 해설을 글로 읽고 나만의 속도로 걸었어요',
 '입구에서 종이 해설을 받아 작품 번호를 표시하며 걸었습니다. 사람 많은 방은 건너뛰었다 돌아와도 이야기가 끊기지 않아 좋았어요. 반납 장소는 출구 옆이었어요.',2),
(12,12,'QUESTION','DAILY','산책 모임에서 길이 갈라지면 어떻게 만나세요?',
 '걸음 속도가 달라 모퉁이에서 일행을 놓친 적이 있어요. 휴대폰을 계속 보지 않고도 합류하기 좋은 약속을 정해 보고 싶습니다.',0);
CREATE TEMP TABLE post_ids (n int PRIMARY KEY,id bigint UNIQUE) ON COMMIT DROP;
WITH inserted AS (
    INSERT INTO public.community_posts(author_id,post_type,topic,title,content,created_at,updated_at)
    SELECT m.id,p.kind,p.topic,p.title,p.body,now()-p.n*interval '1 hour',now()-p.n*interval '1 hour'
    FROM demo_posts p JOIN demo_members m ON m.n=p.writer RETURNING id,title
)
INSERT INTO post_ids SELECT p.n,i.id FROM demo_posts p JOIN inserted i USING(title);
CREATE TEMP TABLE demo_comments (
    n int PRIMARY KEY, post_n int NOT NULL REFERENCES post_ids(n), parent_n int REFERENCES demo_comments(n),
    writer int NOT NULL REFERENCES demo_members(n), body text UNIQUE NOT NULL, votes int NOT NULL
) ON COMMIT DROP;
INSERT INTO demo_comments VALUES
(1,1,NULL,4,'우리 팀은 진행자가 다음 발언자를 이름으로 불러요. 글자가 조금 늦게 떠도 누구 이야기인지 따라가기 한결 쉬웠어요.',6),
(2,1,NULL,7,'기록을 혼자 맡지 않는 것도 중요했어요. 돌아가며 결정 사항을 채팅창에 한 줄씩 남기니 저도 토론에 참여할 여유가 생겼습니다.',10),
(3,1,NULL,3,'화면 한쪽에 자료까지 띄우면 너무 좁아서 저는 발표 자료를 종이로 받아요. 화면은 말하는 사람과 글씨에만 쓰고요.',3),
(4,1,1,1,'발언자가 갑자기 바뀔 때도 이름을 부르나요? 매번 흐름을 끊는 느낌이 들까 봐 망설여져요.',4),
(5,1,1,4,'저희는 손을 든 사람의 이름을 채팅에 먼저 적어요. 화면에 “다음은 지수님”이 남아 있어서 말이 겹쳐도 어느 줄부터 다시 읽을지 보여요. 회의 끝에는 결정 문장만 따로 모읍니다.',12),
(6,1,1,6,'오늘 짧은 회의에서 해 봤어요. 진행자가 손들기 목록을 읽어 주니 제가 차례를 묻는 횟수도 줄었어요.',2),
(7,2,NULL,8,'저도 옆 벽에 화면이 붙은 공연에서 비슷했어요. 같은 열이어도 가운데와 끝자리 차이가 크더라고요.',5),
(8,2,NULL,9,'예매 전에 화면 사진을 요청해도 될까요? 좌석표에는 표시가 없는 공연이 많네요.',3),
(9,2,8,2,'저는 빈 객석에서 찍은 사진이 있는지만 물었어요. 사진이 없어도 직원분이 어느 쪽을 봐야 하는지 설명해 주셨어요.',6),
(10,3,NULL,11,'저는 이해한 부분에는 한 단어만 적고 질문에는 별표를 붙여요. 나중에 별표부터 찾아보면 복습 시간이 줄어요.',4),
(11,3,10,3,'슬라이드 번호 옆에 별표를 적는 방식이면 손을 오래 쓰지 않아도 되겠네요. 내일 수업에서 해 볼게요.',1),
(12,4,NULL,1,'문자 안내 여부는 생각 못 했어요. 다음에 강연 예약할 때 물어볼 목록에 넣었습니다.',1),
(13,5,NULL,10,'요리 영상에서도 손이 가려져서 답답했는데 다음번에 위쪽 배치를 찾아봐야겠어요.',2),
(14,6,NULL,12,'저는 보관함을 항상 같은 지퍼 주머니에 넣어요. 비 맞은 물건은 반대쪽 주머니에 두고요.',1),
(15,7,NULL,5,'서로 편한 방법을 같이 정했다는 점이 좋네요. 저도 새 프로젝트 시작할 때 이야기해 볼래요.',3),
(16,8,NULL,2,'번호판에 창문이 비치는 건 앉아 보기 전엔 잘 모르겠더라고요. 공감해요.',0),
(17,10,NULL,13,'관심 주제가 보이면 이름을 다시 묻지 않아도 자연스럽게 말을 걸 수 있겠어요.',1),
(18,11,NULL,14,'작품 번호가 있는 해설이라니 순서를 바꿔 봐도 돼서 좋네요.',1);
CREATE TEMP TABLE comment_ids (n int PRIMARY KEY,id bigint UNIQUE) ON COMMIT DROP;
-- Roots must exist before inserting their children. Use returned IDs, never assumed sequence values.
DO $$
DECLARE row_data record; new_id bigint;
BEGIN
    FOR row_data IN SELECT c.*, p.id AS post_id, m.id AS author_id,
            now()-p.n*interval '1 hour'+c.n*interval '1 minute' AS stamp
        FROM demo_comments c JOIN post_ids p ON p.n=c.post_n JOIN demo_members m ON m.n=c.writer
        ORDER BY c.n
    LOOP
        INSERT INTO public.community_comments(post_id,author_id,parent_id,content,created_at,updated_at)
        VALUES(row_data.post_id,row_data.author_id,(SELECT id FROM comment_ids WHERE n=row_data.parent_n),
               row_data.body,row_data.stamp,row_data.stamp) RETURNING id INTO new_id;
        INSERT INTO comment_ids VALUES(row_data.n,new_id);
    END LOOP;
END $$;
CREATE TEMP TABLE vote_ids (kind text, id bigint) ON COMMIT DROP;
WITH added AS (
    INSERT INTO public.community_post_likes(post_id,member_id,created_at)
    SELECT i.id,v.id,now() FROM demo_posts p JOIN post_ids i USING(n)
    CROSS JOIN LATERAL (SELECT id FROM demo_members WHERE n<>p.writer ORDER BY n LIMIT p.votes) v
    RETURNING id
) INSERT INTO vote_ids SELECT 'post',id FROM added;
WITH added AS (
    INSERT INTO public.community_comment_likes(comment_id,member_id,created_at)
    SELECT i.id,v.id,now() FROM demo_comments c JOIN comment_ids i USING(n)
    CROSS JOIN LATERAL (SELECT id FROM demo_members WHERE n<>c.writer ORDER BY n LIMIT c.votes) v
    RETURNING id
) INSERT INTO vote_ids SELECT 'comment',id FROM added;
INSERT INTO public.iyum_portfolio_seed_runs(seed_key,member_ids,post_ids,comment_ids,post_like_ids,comment_like_ids)
SELECT 'community-v1',(SELECT array_agg(id ORDER BY n) FROM demo_members),
       (SELECT array_agg(id ORDER BY n) FROM post_ids),(SELECT array_agg(id ORDER BY n) FROM comment_ids),
       (SELECT array_agg(id ORDER BY id) FROM vote_ids WHERE kind='post'),
       (SELECT array_agg(id ORDER BY id) FROM vote_ids WHERE kind='comment');
\endif
SELECT seed_key, applied_at, cardinality(member_ids) AS members, cardinality(post_ids) AS posts,
       cardinality(comment_ids) AS comments_including_replies, cardinality(post_like_ids) AS post_likes,
       cardinality(comment_like_ids) AS comment_likes, post_ids[1] AS meeting_post_id,
       post_ids[2] AS theatre_post_id, comment_ids[1] AS parent_id, comment_ids[5] AS best_reply_id
FROM public.iyum_portfolio_seed_runs WHERE seed_key='community-v1';
COMMIT;
