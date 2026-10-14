import type { Metadata } from "next"

export const metadata: Metadata = {
  title: "주제 공간을 찾을 수 없어요 | IYUM",
  robots: { index: false, follow: false },
}

// 기존 안내를 서버에서 한 번 렌더한다. HTTP 404는 주제 전용 proxy에서 설정한다.
export { default } from "../topics/[slug]/not-found"
