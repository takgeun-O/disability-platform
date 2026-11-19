'use client';

import { useState } from 'react';
import Link from './AppLink';
import { getPosts } from '@/lib/community-api';
import { HOME_RECENT_QUERY } from '@/lib/community-posts';

type RecentPost = { id: number; title: string; commentCount: number };

export default function HomeRecentPosts({ initialPosts }: { initialPosts: RecentPost[] | null }) {
  const [retriedPosts, setRetriedPosts] = useState<RecentPost[] | null>(null);
  const [loading, setLoading] = useState(false);
  const posts = initialPosts ?? retriedPosts;

  async function retry() {
    setLoading(true);
    try {
      const page = await getPosts(HOME_RECENT_QUERY);
      setRetriedPosts(page.items);
    } catch {
      setRetriedPosts(null);
    } finally {
      setLoading(false);
    }
  }

  if (loading) return <p role="status">최근 글을 불러오는 중입니다.</p>;
  if (!posts) return <div>
    <p role="status">최근 글을 불러오지 못했어요.</p>
    <button type="button" className="iyum-button iyum-button-outline" onClick={retry}>다시 시도</button>
  </div>;
  if (!posts.length) return <p role="status">아직 등록된 게시글이 없어요.</p>;

  return <ol className="iyum-posts" aria-label="최근 게시글 목록">
    {posts.map(post => <li key={post.id}>
      <Link href={`/community/posts/${post.id}`} prefetch={false}>
        <strong className="iyum-post-title">{post.title}</strong>
        <span className="iyum-post-comments">댓글 {post.commentCount}</span>
      </Link>
    </li>)}
  </ol>;
}
