import {notFound} from 'next/navigation';
export const dynamic='force-dynamic';
export default async function Page(){await Promise.resolve();notFound()}
export async function generateMetadata(){return {title:'Missing metadata'}}
