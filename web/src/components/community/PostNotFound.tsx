"use client"
import { useEffect } from "react"
import { useSearchParams } from "next/navigation"
import Link from "@/components/AppLink"
import { safeListReturn, requestListScrollRestore, listenForListReturn } from "@/lib/community-posts"
// 안내 본문은 서버 컴포넌트에 두고 쿼리 기반 복귀 동작만 클라이언트에서 보강한다.
export default function PostNotFoundReturnLink() {
  useEffect(listenForListReturn, [])
  const back = safeListReturn(useSearchParams().get("from"))
  return <Link className="iyum-button iyum-button-outline" href={back} scroll={false}
    onNavigate={() => requestListScrollRestore(back)}>목록으로 돌아가기</Link>
}
