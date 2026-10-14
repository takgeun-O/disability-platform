import Image from 'next/image';
import Link from '@/components/AppLink';

const values = [
  ['01 · 정보 찾기', '필요한 정보를 이해하기 쉽게', '정책과 제도, 기기와 기관에 관한 정보를 출처와 함께 살펴볼 수 있는 길을 만듭니다.'],
  ['02 · 질문과 경험 공유', '서로의 경험이 도움이 되도록', '일상에서 마주한 질문과 직접 겪은 경험을 나눕니다. 커뮤니티는 IYUM의 중심입니다.'],
  ['03 · 지식 축적', '한 사람의 경험이 다음 사람에게', '나눈 이야기를 필요할 때 다시 찾아 활용할 수 있는 지식으로 쌓아가는 것을 지향합니다.'],
];
const topics = [
  ['복지·지원', '장애 관련 정책과 지원사업, 제도 이용 과정'],
  ['보조기기', '보청기·인공와우를 포함한 기기 정보와 사용 경험'],
  ['병원·기관', '병원·전문기관·복지기관에 관한 정보와 경험'],
  ['취업·교육·생활', '직장생활, 교육, 의사소통과 일상의 질문'],
];

// Main copy is a Server Component: no browser request is needed to read the introduction.
export default function AboutPage() {
  return <main className="iyum-info iyum-info-container">
    <nav className="info-breadcrumb" aria-label="현재 위치"><Link href="/">홈</Link><span aria-hidden="true">›</span><span aria-current="page">IYUM 소개</span></nav>
    <section className="info-about-lead">
      <div><p className="iyum-eyebrow">장애 관련 정보와 경험을 연결하는 플랫폼</p>
        <h1>정보와 경험이 이어져,<br />더 나은 선택을 돕는 곳.</h1>
        <p className="info-intro">IYUM은 장애 당사자와 가족이 필요한 정보를 찾고, 서로의 질문과 경험을 나누며, 함께 지식을 쌓아가는 정보·커뮤니티 플랫폼을 만들어갑니다.</p>
        <div className="info-actions"><Link className="iyum-button iyum-button-primary" href="/community">커뮤니티 둘러보기</Link><Link className="iyum-button iyum-button-outline" href="/welfare">정보 찾아보기</Link></div>
      </div>
      <div className="info-brand-art" aria-hidden="true"><Image src="/images/logo/iyum-symbol-reference-v1.svg" width={100} height={112} alt="" /><strong>IYUM</strong><span>EXPERIENCES CREATE KNOWLEDGE</span></div>
    </section>
    <section className="info-section info-problem"><p className="iyum-eyebrow">우리가 풀고 싶은 문제</p><h2>흩어진 정보와 경험을,<br />함께 이해할 수 있도록</h2><p className="info-intro">필요한 정보는 여러 곳에 흩어져 있고, 설명은 낯설고 어렵습니다. 공식 안내를 찾은 뒤에도 실제로 겪은 사람의 경험은 따로 찾아야 합니다. IYUM은 이 사이를 연결해, 각자의 상황에서 더 나은 선택을 준비하도록 돕고자 합니다.</p></section>
    <section className="info-section" aria-labelledby="info-values"><h2 id="info-values">정보를 찾고, 경험을 나누고,<br />다시 꺼내 쓸 지식으로</h2><div className="info-values">{values.map(([number, title, copy]) => <article className="iyum-card" key={number}><p className="iyum-eyebrow">{number}</p><h3>{title}</h3><p>{copy}</p></article>)}</div></section>
    <section className="info-section" aria-labelledby="info-topics"><h2 id="info-topics">함께 다루려는 이야기</h2><span className="iyum-badge">넓혀갈 주제</span><p>지원금뿐 아니라, 생활의 다양한 선택과 경험을 연결해 갑니다. 아래는 현재 제공 목록이 아닌 서비스의 확장 방향입니다.</p><div className="info-topics">{topics.map(([title, copy]) => <article key={title}><h3>{title}</h3><p>{copy}</p></article>)}</div></section>
    <section className="info-section info-about-bottom">
      <div className="info-brand-story"><p className="iyum-eyebrow">로고에 담은 의미</p><h2>경험과 정보가 모여<br />자라는 지식의 나무</h2><p>각자의 경험과 정보가 모여 하나의 지식의 나무로 성장합니다. Y는 그 안에서 이어지는 연결과 성장을 표현합니다.</p><blockquote>Experiences Create Knowledge</blockquote><p>사람들의 경험이 모여, 새로운 지식을 만듭니다.</p><a className="iyum-text-link" href="/images/logo/IYUM_logo.png" target="_blank" rel="noopener noreferrer">브랜드 설명 원본 보기 ↗ <span className="sr-only">(새 탭)</span></a></div>
      <div><h2>지금의 시작,<br />앞으로 넓혀갈 방향</h2><p className="info-intro">현재는 청각장애 관련 정보와 커뮤니티부터 시작하며, 다루는 주제와 장애 유형을 단계적으로 넓혀갑니다.</p>
        <div className="info-availability"><span className="iyum-badge">현재 이용 가능</span><h3>질문·경험 게시글과 보청기 지원 안내</h3><p>커뮤니티 게시글을 읽고, 로그인 후 글을 작성할 수 있습니다. 보청기 지원 안내에서는 내 상황에 따른 절차를 이해하고 상담 질문을 준비할 수 있습니다.</p><p>회원가입, 이메일 인증과 로그인 기능을 제공합니다.</p><div className="info-actions"><Link className="iyum-text-link" href="/community">게시글 둘러보기 →</Link><Link className="iyum-text-link" href="/validation/hearing-aid-health-insurance">보청기 안내 보기 →</Link></div></div>
        <div className="info-availability"><span className="iyum-badge">앞으로의 방향</span><h3>더 다양한 장애 유형과 생활의 주제로</h3><p>시각·지체·발달장애 등을 포함한 다양한 필요로 범위를 넓혀가고자 합니다. 기기·기관 탐색과 더 많은 복지·지원정보는 아직 준비 중입니다.</p><p>출처 기반 AI 검색과 개인화는 장기 방향이며, 현재 제공하는 기능은 아닙니다.</p></div>
      </div>
    </section>
    <section className="info-section info-notice"><h2>공식 정보와 개인 경험은 구분합니다</h2><p>서로 연결하되, 같은 성격의 정보로 취급하지 않습니다. 제도 안내는 공식 출처와 확인 기준을 살피고, 개인의 경험은 각자의 상황에서 나온 이야기로 읽을 수 있도록 합니다.</p></section>
  </main>;
}
