import AdminPostDetail from "@/features/AdminPostDetail"
import { adminReturnHref } from "@/lib/admin-navigation"
export default async function Page({params,searchParams}: {params:Promise<{postId:string}>;searchParams:Promise<Record<string,string|string[]|undefined>>}) {
  const {postId}=await params, search=await searchParams
  const id=/^[1-9]\d*$/.test(postId)&&Number.isSafeInteger(Number(postId))?Number(postId):0
  return <AdminPostDetail key={postId} postId={id} returnHref={adminReturnHref(search.from)}/>
}
