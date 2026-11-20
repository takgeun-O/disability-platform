'use client';

import { useState, useSyncExternalStore } from 'react';
import Link from './AppLink';
import { writtenTime } from '@/lib/written-time';
import { writtenClock } from '@/lib/written-clock';
import { getFeed, getWeeklyBest, type FeedItem } from '@/lib/community-api';
import { HOME_RECENT_HREF, HOME_WEEKLY_HREF, HOME_RECENT_QUERY } from '@/lib/community-posts';

export default function HomeRecentPosts({ initialPosts, weekly = false }: { initialPosts: FeedItem[] | null; weekly?: boolean }) {
  const [retriedPosts, setRetriedPosts] = useState<FeedItem[] | null>(null);
  const [loading, setLoading] = useState(false);
  const now = useSyncExternalStore(writtenClock.subscribe, writtenClock.getSnapshot, writtenClock.getServerSnapshot);
  const posts = initialPosts ?? retriedPosts;

  const subject = weekly ? '주간 인기 글을' : '최근 글을';
  const returnHref = weekly ? HOME_WEEKLY_HREF : HOME_RECENT_HREF;

  async function retry() {
    setLoading(true);
    try {
      const items = weekly
        ? await getWeeklyBest('', undefined, HOME_RECENT_QUERY.size)
        : (await getFeed(HOME_RECENT_QUERY)).items;
      setRetriedPosts(items);
    } catch {
      setRetriedPosts(null);
    } finally {
      setLoading(false);
    }
  }

  if (loading) return <p role="status">{subject} 불러오는 중입니다.</p>;
  if (!posts) return <div>
    <p role="status">{subject} 불러오지 못했어요.</p>
    <button type="button" className="iyum-button iyum-button-outline" onClick={retry}>다시 시도</button>
  </div>;
  if (!posts.length) return <p role="status">{weekly ? '최근 7일 동안 작성된 글 중 아직 좋아요를 받은 글이 없어요.' : '아직 등록된 게시글이 없어요.'}</p>;

  return <ol className="iyum-posts" aria-label={weekly ? "주간 인기 게시글 목록" : "최근 게시글 목록"}>
    {posts.map((post, index) => <li key={post.id}>
      <Link href={`/community/posts/${post.id}?from=${encodeURIComponent(returnHref)}`} prefetch={false}>
        <span className="iyum-post-copy">
          <span className="iyum-post-byline">
            <span>{post.author.nickname}</span>
            <time dateTime={post.createdAt}>{writtenTime(post.createdAt, now).label}</time>
          </span>
          <strong className="iyum-post-title">{weekly && <span className="iyum-post-rank">{index + 1}위 </span>}{post.title}</strong>
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
