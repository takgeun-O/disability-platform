// Next의 router에는 검증된 내부 경로만 전달한다.
export function safeReturnPath(value: string): string {
  if (!value.startsWith('/') || value.startsWith('//') || /[\\\u0000-\u001f\u007f]/.test(value)) return '/';
  try {
    const base = 'https://iyum.invalid';
    const url = new URL(value, base);
    if (url.origin !== base) return '/';
    let pathname = url.pathname;
    // 인코딩된 슬래시·역슬래시·로그인 경로도 확인한다. 검색어와 해시는 그대로 보존한다.
    for (let count = 0; count < 5; count++) {
      const decoded = decodeURIComponent(pathname);
      if (decoded === pathname) break;
      pathname = decoded;
      if (count === 4) return '/';
    }
    if (pathname.startsWith('//') || /[\\\u0000-\u001f\u007f]/.test(pathname)) return '/';
    const normalized = new URL(pathname, base);
    if (normalized.origin !== base || /^\/(?:login|reset-password)(?:\/|$)/i.test(normalized.pathname)) return '/';
    return `${url.pathname}${url.search}${url.hash}`;
  } catch { return '/'; }
}
