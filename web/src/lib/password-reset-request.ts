// no-referrer인 문서의 네이티브 POST는 Origin: null을 보낼 수 있다.
// Next의 전역 allowedOrigins를 넓히지 않고, 브라우저가 보장하는 동일 출처
// 문서 폼 제출에만 원래 출처를 복원한다. JS fetch와 외부/누락 출처는 해당하지 않는다.
export function resetFormOrigin(method: string, path: string, headers: Headers, origin: string): Headers | null {
  if (method !== 'POST' || !['/forgot-password', '/reset-password'].includes(path)
      || headers.get('origin') !== 'null'
      || headers.get('sec-fetch-site') !== 'same-origin'
      || headers.get('sec-fetch-mode') !== 'navigate'
      || headers.get('sec-fetch-dest') !== 'document'
      || !headers.get('content-type')?.startsWith('multipart/form-data;')) return null;
  const host = headers.get('x-forwarded-host') ?? headers.get('host');
  if (new URL(origin).host !== host) return null;
  const result = new Headers(headers);
  result.set('origin', origin);
  return result;
}
