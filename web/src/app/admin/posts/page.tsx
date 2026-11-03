import AdminPosts from "@/features/AdminPosts"
import { adminListQuery } from "@/lib/admin-navigation"
export default async function Page({searchParams}: {searchParams: Promise<Record<string,string|string[]|undefined>>}) {
  const query=adminListQuery(await searchParams)
  return <AdminPosts key={JSON.stringify(query)} initialQuery={query} />
}
