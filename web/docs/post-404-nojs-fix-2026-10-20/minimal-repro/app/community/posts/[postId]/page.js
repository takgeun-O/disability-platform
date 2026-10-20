import {notFound} from 'next/navigation';
export const dynamic='force-dynamic';
export async function generateMetadata({params}) {const {postId}=await params;return postId==='995'?{title:'Metadata case'}:{}}
export default async function Page(){await Promise.resolve();notFound()}
