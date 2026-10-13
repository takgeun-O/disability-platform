'use client';
import { useRef, useState } from 'react';
import { useRouter } from 'next/navigation';

export default function HomeSearch() {
  const router = useRouter();
  const [searchQuery, setSearchQuery] = useState('');
  const [searchError, setSearchError] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);
  return <form role="search" aria-label="통합 검색" className="iyum-home-search" onSubmit={event => {
    event.preventDefault();
    if (!searchQuery.trim()) {
      setSearchError('검색어를 입력해 주세요.');
      inputRef.current?.focus();
      return;
    }
    setSearchError('');
    router.push(`/search?q=${encodeURIComponent(searchQuery.trim())}&area=전체`);
  }}>
    <label htmlFor="hero-search" className="sr-only">통합 검색</label>
    <div className="iyum-search-field">
      <svg aria-hidden="true" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7"><circle cx="10" cy="10" r="7" /><path d="m15 15 6 6" /></svg>
      <input ref={inputRef} id="hero-search" type="search" value={searchQuery} placeholder="궁금한 정보나 경험을 검색해 보세요"
        aria-invalid={!!searchError} aria-describedby={`hero-search-note${searchError ? ' hero-search-error' : ''}`}
        onChange={event => { setSearchQuery(event.target.value); if (searchError) setSearchError(''); }} />
      <button type="submit" className="iyum-button iyum-button-primary">검색</button>
    </div>
    {searchError && <p id="hero-search-error" role="alert" className="iyum-field-error">{searchError}</p>}
    <p id="hero-search-note" className="iyum-search-note">현재 검색 결과는 예시 데이터로 제공됩니다.</p>
  </form>;
}
