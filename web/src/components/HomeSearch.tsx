'use client';
import { useRef, useState } from 'react';
import { useRouter } from 'next/navigation';

import { normalizeTitle, searchHref, searchKeywordError, type PostType } from '@/lib/community-posts';

export default function HomeSearch({ initialQuery = '', type = '', keywordError = '' }: {
  initialQuery?: string;
  type?: PostType | '';
  keywordError?: string;
}) {
  const router = useRouter();
  const [searchQuery, setSearchQuery] = useState(initialQuery);
  const [localError, setSearchError] = useState('');
  const searchError = localError || (normalizeTitle(searchQuery) === initialQuery ? keywordError : '');
  const inputRef = useRef<HTMLInputElement>(null);
  return <form role="search" aria-label="커뮤니티 게시글 검색" className="iyum-home-search" onSubmit={event => {
    event.preventDefault();
    const keyword = normalizeTitle(searchQuery);
    const error = searchKeywordError(keyword);
    if (error) {
      setSearchError(error);
      inputRef.current?.focus();
      return;
    }
    setSearchError('');
    router.push(searchHref(keyword, 1, type));
  }}>
    <label htmlFor="hero-search" className="sr-only">커뮤니티 게시글 검색</label>
    <div className="iyum-search-field">
      <svg aria-hidden="true" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7"><circle cx="10" cy="10" r="7" /><path d="m15 15 6 6" /></svg>
      <input ref={inputRef} id="hero-search" type="search" value={searchQuery} placeholder="게시글 제목·본문 검색"
        aria-invalid={!!searchError} aria-describedby={`hero-search-note${searchError ? ' hero-search-error' : ''}`}
        onChange={event => { setSearchQuery(event.target.value); if (searchError) setSearchError(''); }} />
      <button type="submit" className="iyum-button iyum-button-primary">검색</button>
    </div>
    {searchError && <p id="hero-search-error" role="alert" className="iyum-field-error">{searchError}</p>}
    <p id="hero-search-note" className="iyum-search-note">커뮤니티 게시글의 제목과 본문을 검색합니다.</p>
  </form>;
}
