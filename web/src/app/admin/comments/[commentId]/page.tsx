import AdminReportedComment from "@/features/AdminReportedComment"
import { adminReturnHref } from "@/lib/admin-navigation"
export default async function Page({params,searchParams}:{params:Promise<{commentId:string}>;searchParams:Promise<Record<string,string|string[]|undefined>>}) {
  const {commentId}=await params, search=await searchParams
  const id=/^[1-9]\d*$/.test(commentId)&&Number.isSafeInteger(Number(commentId))?Number(commentId):0
  return <AdminReportedComment key={commentId} id={id} returnHref={adminReturnHref(search.from)}/>
}
