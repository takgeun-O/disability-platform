import Link from '@/components/AppLink';

const POST_TYPES = [
  { label: '일반', desc: '자유롭게 글을 올리는 공간' },
  { label: '질문·답변', desc: '궁금한 점을 묻고 답하는 공간' },
  { label: '경험·후기', desc: '실제 경험과 후기를 나누는 공간' },
]

const TOPICS = [
  '자유·일상',
  '보청기',
  '인공와우',
  '치료·재활',
  '의사소통',
  '취업·직장',
  '복지·생활',
]

const RECENT_POSTS = [
  {
    id: 1,
    title: '보청기 보험 급여 기준이 바뀐 것 같은데, 혹시 아시는 분 계세요?',
    type: '질문·답변',
    topic: '보청기',
    author: '이○○',
    date: '2026.08.15',
    comments: 14,
  },
  {
    id: 2,
    title: '세브란스병원 인공와우 수술 후기 공유합니다 (6개월 경과)',
    type: '경험·후기',
    topic: '인공와우',
    author: '김○○',
    date: '2026.08.15',
    comments: 23,
  },
  {
    id: 3,
    title: '직장에서 청각장애를 고지해야 할 때 어떻게 하셨나요?',
    type: '질문·답변',
    topic: '취업·직장',
    author: '박○○',
    date: '2026.08.14',
    comments: 31,
  },
  {
    id: 4,
    title: '2026 장애인 보조기기 교부사업 신청 완료했어요. 생각보다 간단했습니다.',
    type: '경험·후기',
    topic: '복지·생활',
    author: '최○○',
    date: '2026.08.14',
    comments: 8,
  },
  {
    id: 5,
    title: '수어 통역 앱 중에 실제로 쓸 만한 게 있나요?',
    type: '질문·답변',
    topic: '의사소통',
    author: '정○○',
    date: '2026.08.13',
    comments: 17,
  },
  {
    id: 6,
    title: '청능재활 치료 몇 회쯤 받으면 효과가 느껴지나요?',
    type: '질문·답변',
    topic: '치료·재활',
    author: '윤○○',
    date: '2026.08.13',
    comments: 9,
  },
  {
    id: 7,
    title: '오티콘 모어 vs 스타키 에볼브 AI, 어느 쪽 쓰시나요?',
    type: '일반',
    topic: '보청기',
    author: '강○○',
    date: '2026.08.12',
    comments: 20,
  },
  {
    id: 8,
    title: '오늘 아이 첫 보청기 착용했어요. 많이 낯설어하더라고요.',
    type: '일반',
    topic: '자유·일상',
    author: '조○○',
    date: '2026.08.12',
    comments: 12,
  },
]

export default function CommunityHome() {
  return (
    <main id="main-content">

      {/* ─── PAGE HEADER ────────────────────────────────────────── */}
      <div style={{ borderBottom: '1px solid #D0CEC9', backgroundColor: '#F0EEE9', paddingTop: 36, paddingBottom: 36 }}>
        <div className="max-w-6xl mx-auto px-6">
          <div className="flex items-start justify-between gap-6">
            <div>
              <h1 className="font-bold mb-1.5" style={{ fontSize: 26, lineHeight: 1.25, color: '#1A1918' }}>
                커뮤니티
              </h1>
              <p style={{ fontSize: 14, color: '#908D88', lineHeight: 1.5 }}>
                질문, 경험, 일상을 나누는 공간입니다. 누구나 자유롭게 읽을 수 있습니다.
              </p>
            </div>
            <Link
              href="/community/posts/create"
              style={{
                flexShrink: 0,
                backgroundColor: '#1E3A8A',
                color: '#FFFFFF',
                border: 'none',
                borderRadius: 2,
                padding: '10px 20px',
                fontSize: 14,
                fontWeight: 600,
                cursor: 'pointer',
                marginTop: 2,
                textDecoration: 'none',
                display: 'inline-block',
              }}
            >
              글쓰기
            </Link>
          </div>
        </div>
      </div>

      {/* ─── POST TYPE + TOPIC DISCOVERY ────────────────────────── */}
      <div style={{ borderBottom: '1px solid #D0CEC9', paddingTop: 32, paddingBottom: 32 }}>
        <div className="max-w-6xl mx-auto px-6">

          {/* Post Types */}
          <div style={{ marginBottom: 28 }}>
            <h2
              className="font-semibold"
              style={{ fontSize: 12, letterSpacing: '0.06em', textTransform: 'uppercase', color: '#908D88', marginBottom: 4 }}
            >
              글 유형
            </h2>
            <p style={{ fontSize: 13, color: '#908D88', marginBottom: 12 }}>어떤 형태의 글을 찾고 있나요?</p>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 0 }}>
              {POST_TYPES.map((pt, i) => (
                <Link
                  key={pt.label}
                  href={`/community/posts?type=${encodeURIComponent(pt.label)}`}
                  style={{
                    display: 'block',
                    padding: '14px 16px',
                    borderLeft: i > 0 ? '1px solid #D0CEC9' : 'none',
                    textDecoration: 'none',
                    color: '#1A1918',
                    transition: 'background-color 0.15s',
                  }}
                  data-hover="iyum-hover-8"
                >
                  <span className="block font-semibold" style={{ fontSize: 14, marginBottom: 3 }}>{pt.label}</span>
                  <span className="block" style={{ fontSize: 12, color: '#908D88', lineHeight: 1.4 }}>{pt.desc}</span>
                </Link>
              ))}
            </div>
          </div>

          {/* Topics */}
          <div>
            <h2
              className="font-semibold"
              style={{ fontSize: 12, letterSpacing: '0.06em', textTransform: 'uppercase', color: '#908D88', marginBottom: 4 }}
            >
              주요 주제
            </h2>
            <p style={{ fontSize: 13, color: '#908D88', marginBottom: 12 }}>관심 있는 주제로 둘러보세요.</p>
            <ul
              className="flex flex-wrap gap-2"
              aria-label="주요 주제 목록"
              style={{ listStyle: 'none', padding: 0, margin: 0 }}
            >
              {TOPICS.map((topic) => (
                <li key={topic}>
                  <Link
                    href={`/community/posts?topic=${encodeURIComponent(topic)}`}
                    style={{
                      display: 'inline-block',
                      fontSize: 13,
                      fontWeight: 500,
                      padding: '5px 12px',
                      border: '1px solid #C8C5BF',
                      borderRadius: 2,
                      textDecoration: 'none',
                      color: '#1A1918',
                      backgroundColor: '#FAFAF8',
                      transition: 'background-color 0.15s, border-color 0.15s',
                    }}
                    data-hover="iyum-hover-9"
                  >
                    {topic}
                  </Link>
                </li>
              ))}
            </ul>
          </div>

        </div>
      </div>

      {/* ─── RECENT POSTS ────────────────────────────────────────── */}
      <div className="max-w-6xl mx-auto px-6" style={{ paddingTop: 40, paddingBottom: 56 }}>

        <div className="flex items-baseline justify-between mb-5">
          <h2 className="font-semibold" style={{ fontSize: 15 }}>최근 게시글</h2>
          <Link
            href="/community/posts"
            style={{ fontSize: 13, color: '#1E3A8A', textDecoration: 'none', fontWeight: 500 }}
          >
            전체 게시글 보기 →
          </Link>
        </div>

        <ol aria-label="최근 게시글 목록">
          {RECENT_POSTS.map((post, i) => (
            <li
              key={post.id}
              style={{ borderTop: i === 0 ? '1px solid #D0CEC9' : '1px solid #E8E6E1', borderBottom: i === RECENT_POSTS.length - 1 ? '1px solid #D0CEC9' : 'none' }}
            >
              <div style={{ padding: '14px 0' }}>
                {/* Type + Topic label */}
                <div className="flex items-center gap-1.5 mb-1.5">
                  <span
                    className="font-medium"
                    style={{ fontSize: 12, color: '#1E3A8A' }}
                  >
                    {post.type}
                  </span>
                  <span style={{ color: '#D0CEC9', fontSize: 11 }} aria-hidden="true">·</span>
                  <span style={{ fontSize: 12, color: '#908D88' }}>{post.topic}</span>
                </div>

                {/* Title as link */}
                <Link
                  href={`/community/posts/${post.id}`}
                  className="block font-medium leading-snug"
                  style={{ fontSize: 15, color: '#1A1918', textDecoration: 'none', marginBottom: 6 }}
                  data-hover="iyum-hover-10"
                >
                  {post.title}
                </Link>

                {/* Metadata */}
                <div className="flex items-center gap-1.5">
                  <span style={{ fontSize: 12, color: '#908D88' }}>{post.author}</span>
                  <span style={{ color: '#D0CEC9', fontSize: 11 }} aria-hidden="true">·</span>
                  <time style={{ fontSize: 12, color: '#908D88' }} dateTime={post.date.replace(/\./g, '-')}>{post.date}</time>
                  <span style={{ color: '#D0CEC9', fontSize: 11 }} aria-hidden="true">·</span>
                  <span style={{ fontSize: 12, color: '#908D88' }} aria-label={`댓글 ${post.comments}개`}>댓글 {post.comments}</span>
                </div>
              </div>
            </li>
          ))}
        </ol>

      </div>

    </main>
  )
}
