'use client';

import { useState, useSyncExternalStore } from 'react';
import Link from './AppLink';
import { writtenTime } from '@/lib/written-time';
import { writtenClock } from '@/lib/written-clock';
import { getFeed, type FeedItem } from '@/lib/community-api';
import { HOME_RECENT_HREF, HOME_RECENT_QUERY } from '@/lib/community-posts';

export default function HomeRecentPosts({ initialPosts }: { initialPosts: FeedItem[] | null }) {
  const [retriedPosts, setRetriedPosts] = useState<FeedItem[] | null>(null);
  const [loading, setLoading] = useState(false);
  const now = useSyncExternalStore(writtenClock.subscribe, writtenClock.getSnapshot, writtenClock.getServerSnapshot);
  const posts = initialPosts ?? retriedPosts;

  async function retry() {
    setLoading(true);
    try {
      const page = await getFeed(HOME_RECENT_QUERY);
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
      <Link href={`/community/posts/${post.id}?from=${encodeURIComponent(HOME_RECENT_HREF)}`} prefetch={false}>
        <span className="iyum-post-copy">
          <span className="iyum-post-byline">
            <span>{post.author.nickname}</span>
            <time dateTime={post.createdAt}>{writtenTime(post.createdAt, now).label}</time>
          </span>
          <strong className="iyum-post-title">{post.title}</strong>
          {post.contentPreview.trim() && <span className="iyum-post-preview">{post.contentPreview}</span>}
        </span>
        <span className="iyum-post-reactions">
          <span>좋아요 {post.likeCount}개</span>
          <span>댓글 {post.commentCount}개</span>
        </span>
      </Link>
    </li>)}
  </ol>;
}
