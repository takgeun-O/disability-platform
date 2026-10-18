import type { Metadata } from "next"
export const metadata: Metadata = {
  title: "게시글을 찾을 수 없어요 | IYUM",
  robots: { index: false, follow: false },
}
// proxy가 실제 404를 설정하고 URL을 유지한다. 정상 서버 렌더로 무JS 본문/링크를 제공한다.
export { default } from "../posts/[postId]/not-found"
