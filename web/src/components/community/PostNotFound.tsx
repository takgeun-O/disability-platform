"use client"
import { postListChanged } from "@/lib/community-mutations"
import { useEffect, useLayoutEffect } from "react"
import Link from "@/components/AppLink"
import { requestListScrollRestore, listenForListReturn } from "@/lib/community-posts"
// 안내 본문은 서버 컴포넌트에 두고 쿼리 기반 복귀 동작만 클라이언트에서 보강한다.
export default function PostNotFoundReturnLink({ back }: { back: string }) {
  useEffect(listenForListReturn, [])
  // Only the not-found boundary mounts this link; API outages use the error UI.
  // Mark lists stale, never optimistically remove rows or adjust counts.
  useLayoutEffect(() => { postListChanged() }, [])
  return <Link className="iyum-button iyum-button-outline" href={back} scroll={false}
    onNavigate={() => requestListScrollRestore(back)}>목록으로 돌아가기</Link>
}
