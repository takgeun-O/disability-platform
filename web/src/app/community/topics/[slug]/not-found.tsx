import Link from "@/components/AppLink"
export default function TopicNotFound() {
  return (
    <main
      id="main-content"
      className="community-screen iyum-container community-topic-missing"
    >
      <h1>주제 공간을 찾을 수 없어요.</h1>
      <p>주소를 확인하거나 전체 커뮤니티에서 주제를 선택해 주세요.</p>
      <Link className="iyum-button iyum-button-primary" href="/community">
        전체 커뮤니티로
      </Link>
    </main>
  )
}
