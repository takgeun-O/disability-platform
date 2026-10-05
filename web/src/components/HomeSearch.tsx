"use client";
import {useRef,useState} from "react";
import {useRouter} from "next/navigation";
export default function HomeSearch(){
 const router=useRouter();
 const [searchQuery,setSearchQuery]=useState("");
 const [searchError,setSearchError]=useState("");
 const searchInputRef=useRef<HTMLInputElement>(null);
 return (          <form
            role="search"
            aria-label="통합 검색"
            onSubmit={(e) => {
              e.preventDefault()
              if (!searchQuery.trim()) {
                setSearchError('검색어를 입력해 주세요.')
                searchInputRef.current?.focus()
                return
              }
              setSearchError('')
              router.push(`/search?q=${encodeURIComponent(searchQuery.trim())}&area=전체`)
            }}
            className="flex flex-col items-stretch"
            style={{ maxWidth: 600 }}
          >
            <label htmlFor="hero-search" className="sr-only">통합 검색</label>
            <div className="flex items-stretch">
              <input
                ref={searchInputRef}
                id="hero-search"
                type="search"
                value={searchQuery}
                onChange={(e) => {
                  setSearchQuery(e.target.value)
                  if (searchError) setSearchError('')
                }}
                placeholder="복지, 병원, 보조기기, 커뮤니티 검색"
                aria-describedby={searchError ? 'hero-search-error' : undefined}
                style={{
                  flex: 1,
                  borderTop: searchError ? '1.5px solid #B91C1C' : '1.5px solid #1A1918',
                  borderBottom: searchError ? '1.5px solid #B91C1C' : '1.5px solid #1A1918',
                  borderLeft: searchError ? '1.5px solid #B91C1C' : '1.5px solid #1A1918',
                  borderRight: 'none',
                  borderRadius: '2px 0 0 2px',
                  padding: '10px 14px',
                  fontSize: 14,
                  backgroundColor: '#FAFAF8',
                  color: '#1A1918',
                  outline: 'none',
                }}
              />
              <button
                type="submit"
                style={{
                  backgroundColor: '#1A1918',
                  color: '#FAFAF8',
                  border: '1.5px solid #1A1918',
                  borderRadius: '0 2px 2px 0',
                  padding: '10px 20px',
                  fontSize: 14,
                  fontWeight: 600,
                  cursor: 'pointer',
                  whiteSpace: 'nowrap',
                }}
              >
                검색
              </button>
            </div>
            {searchError && (
              <p id="hero-search-error" role="alert" style={{ fontSize: 12, color: '#B91C1C', marginTop: 6 }}>
                {searchError}
              </p>
            )}
          </form>);
}
