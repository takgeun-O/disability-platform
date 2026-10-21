import { resetToken } from './password-reset';

export type ResetValidation = 'valid' | 'invalid' | 'unavailable' | 'limited';
// 호출자는 서버 설정 주소만 사용한다. 원본 토큰/URL/응답 본문을 오류나 로그로 전파하지 않는다.
export async function validateResetLink(token: string, base: string | undefined, fetcher: typeof fetch = fetch): Promise<ResetValidation> {
  if (!resetToken(token)) return 'invalid';
  if (!base || !/^https?:\/\//.test(base)) return 'unavailable';
  try {
    const query = new URLSearchParams({ token });
    const response = await fetcher(`${base.replace(/\/$/, '')}/api/v1/auth/password-reset/validate?${query}`, {
      method: 'GET', credentials: 'omit', cache: 'no-store', redirect: 'error', referrerPolicy: 'no-referrer',
      headers: { Accept: 'application/json' }, signal: AbortSignal.timeout(10_000),
    });
    if (response.status === 204) return 'valid';
    if (response.status === 429) return 'limited';
    if (response.status === 400 && /^application\/json(?:;|$)/i.test(response.headers.get('content-type') ?? '')) {
      const body: unknown = await response.json();
      if (body && typeof body === 'object' && 'code' in body && body.code === 'PASSWORD_RESET_TOKEN_INVALID'
          && 'message' in body && typeof body.message === 'string' && 'fieldErrors' in body
          && Array.isArray(body.fieldErrors) && body.fieldErrors.length === 0) return 'invalid';
    }
  } catch { /* 통신 오류를 만료로 단정하지 않고 민감정보가 없는 상태만 반환한다. */ }
  return 'unavailable';
}
