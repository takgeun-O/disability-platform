'use client';
import { createContext, useContext, useState, useSyncExternalStore, type ReactNode } from 'react';
import { useParams } from 'next/navigation';

export interface CreatedPost {
  type: string; topic: string; title: string; bodyText: string;
  author: string; date: string; previewImage: string | null;
}
type Entry = {id: string; post: CreatedPost};
const Context = createContext<{posts: Record<string, CreatedPost>; add: (id: string, post: CreatedPost) => void} | null>(null);
export function MockPostsProvider({children}: {children: ReactNode}) {
  const [posts, setPosts] = useState<Record<string, CreatedPost>>({});
  return <Context.Provider value={{posts, add:(id,post)=>setPosts(prev=>({...prev,[id]:post}))}}>{children}</Context.Provider>;
}
export function useMockPosts() {
  const value = useContext(Context);
  if (!value) throw new Error('MockPostsProvider is required');
  return value;
}
const subscribe = (notify: () => void) => {window.addEventListener('popstate', notify); return () => window.removeEventListener('popstate', notify);};
const serverSnapshot = () => null;
const historySnapshot = (): Entry | null => history.state?.iyumMockPost ?? null;
export function useCreatedPost() {
  const {postId} = useParams<{postId:string}>();
  const {posts} = useMockPosts();
  const saved = useSyncExternalStore(subscribe, historySnapshot, serverSnapshot);
  return posts[postId] ?? (saved?.id === postId ? saved.post : null);
}
