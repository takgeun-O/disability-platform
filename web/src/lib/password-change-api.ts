import type { CsrfContext } from './signup-api';
import { PASSWORD_CHANGE_FIELDS, retryDeadline, type PasswordChangeValues, type PasswordChangeFields } from './password-change';

export const PASSWORD_CHANGE_UNCERTAIN = '변경 결과를 확인하지 못했습니다. 이미 변경되었을 수 있습니다. 새 비밀번호로 로그인되는지 확인하거나 비밀번호 재설정을 이용해 주세요.';
export class PasswordChangeError extends Error {
  constructor(public status: number, public code: string, message: string,
    public fields: PasswordChangeFields = {}, public retryAt: number | null = null) { super(message); }
}
const object = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v);
const fieldMessages = {
  currentPassword: '현재 비밀번호를 입력하고 UTF-8 기준 72바이트 이하인지 확인해 주세요.',
  newPassword: '새 비밀번호는 8자 이상, 영문·숫자 포함, UTF-8 기준 72바이트 이하여야 합니다.',
  newPasswordConfirm: '새 비밀번호를 한 번 더 입력하고 일치하는지 확인해 주세요.',
};
function failure(status: number, body: unknown, retryAfter: string | null) {
  const code = object(body) && typeof body.code === 'string' ? body.code : 'UNEXPECTED_RESPONSE';
  if (status === 401 && code === 'AUTHENTICATION_REQUIRED') return new PasswordChangeError(status, code, '로그인 시간이 만료되었습니다. 다시 로그인해 주세요.');
  if (status === 403) return new PasswordChangeError(status, code, code === 'CSRF_TOKEN_INVALID'
    ? '보안 확인 정보가 유효하지 않습니다. 다시 제출하면 새 보안 정보로 요청합니다.' : '현재 계정으로 비밀번호를 변경할 수 없습니다. 계정 상태를 확인해 주세요.');
  if (status === 429 && code === 'PASSWORD_CHANGE_RATE_LIMITED') return new PasswordChangeError(status, code,
    '비밀번호 변경 시도가 많습니다. 잠시 후 다시 시도해 주세요.', {}, retryDeadline(retryAfter));
  if (status === 400) {
    const fields: PasswordChangeFields = {};
    if (code === 'VALIDATION_FAILED' && object(body) && Array.isArray(body.fieldErrors)) {
      for (const item of body.fieldErrors) if (object(item)) {
        const field = PASSWORD_CHANGE_FIELDS.find(key => key === item.field);
        if (field) fields[field] = fieldMessages[field];
      }
    }
    if (code === 'PASSWORD_CHANGE_INVALID_CURRENT_PASSWORD') fields.currentPassword = fieldMessages.currentPassword;
    if (code === 'PASSWORD_CHANGE_INVALID_NEW_PASSWORD') fields.newPassword = fieldMessages.newPassword;
    if (code === 'PASSWORD_CHANGE_CURRENT_PASSWORD_MISMATCH') fields.currentPassword = '현재 비밀번호가 일치하지 않습니다.';
    if (code === 'PASSWORD_CHANGE_PASSWORD_UNCHANGED') fields.newPassword = '현재 비밀번호와 다른 비밀번호를 입력해 주세요.';
    if (Object.keys(fields).length) return new PasswordChangeError(status, code, '', fields);
    if (code === 'VALIDATION_FAILED' || code === 'INVALID_REQUEST_BODY') return new PasswordChangeError(status, code, '입력값을 확인해 주세요.');
  }
  return new PasswordChangeError(status, 'UNEXPECTED_RESPONSE', PASSWORD_CHANGE_UNCERTAIN);
}
export async function changePassword(values: PasswordChangeValues, csrf: CsrfContext): Promise<{ reloginRequired: boolean }> {
  const base = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (!base) throw new PasswordChangeError(0, 'API_NOT_CONFIGURED', '회원 서비스에 연결할 수 없어 변경 요청을 보내지 않았습니다.');
  let response: Response;
  try {
    response = await fetch(base.replace(/\/$/, '') + '/api/v1/members/me/password', {
      method: 'POST', credentials: 'include', cache: 'no-store', redirect: 'error', referrerPolicy: 'no-referrer',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
      body: JSON.stringify({ currentPassword: values.currentPassword, newPassword: values.newPassword, newPasswordConfirm: values.newPasswordConfirm }),
      signal: AbortSignal.timeout(15_000),
    });
  } catch { throw new PasswordChangeError(0, 'NETWORK_ERROR', PASSWORD_CHANGE_UNCERTAIN); }
  let body: unknown = null;
  if (/^application\/json(?:;|$)/i.test(response.headers.get('content-type') ?? '')) { try { body = await response.json(); } catch {} }
  if (response.status === 200 && object(body) && typeof body.reloginRequired === 'boolean') return { reloginRequired: body.reloginRequired };
  throw failure(response.status, body, response.headers.get('retry-after'));
}
