import type { CsrfContext } from './signup-api';
import { RESET_ACCEPTED, RESET_LINK_ERROR, type ResetFields, type ResetState, type ResetValues } from './password-reset';

export class PasswordResetError extends Error {
  constructor(public code: string, public state: ResetState) { super(state.message ?? '입력값을 확인해 주세요.'); }
}
const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
function failure(status: number, body: unknown, kind: 'request' | 'complete'): PasswordResetError {
  const code = object(body) && typeof body.code === 'string' ? body.code : 'UNEXPECTED_RESPONSE';
  if (status === 400 && code === 'PASSWORD_RESET_TOKEN_INVALID') return new PasswordResetError(code, { status: 'invalid-link', fields: {}, message: RESET_LINK_ERROR });
  const fields: ResetFields = {};
  if (status === 400 && kind === 'complete' && ['PASSWORD_RESET_PASSWORD_INVALID', 'PASSWORD_RESET_PASSWORD_UNCHANGED'].includes(code)) {
    fields.newPassword = code === 'PASSWORD_RESET_PASSWORD_UNCHANGED' ? '현재 비밀번호와 다른 비밀번호를 입력해 주세요.' : '비밀번호 조건을 확인하고 다시 입력해 주세요.';
  }
  if (status === 400 && code === 'VALIDATION_FAILED' && object(body) && Array.isArray(body.fieldErrors)) {
    for (const entry of body.fieldErrors) {
      if (!object(entry)) continue;
      if (entry.field === 'token' && kind === 'complete') return new PasswordResetError(code, { status: 'invalid-link', fields: {}, message: RESET_LINK_ERROR });
      if (entry.field === 'email' && kind === 'request') fields.email = '올바른 이메일 주소를 254자 이내로 입력해 주세요.';
      if (entry.field === 'newPassword' && kind === 'complete') fields.newPassword = '비밀번호 조건을 확인하고 다시 입력해 주세요.';
      if (entry.field === 'newPasswordConfirm' && kind === 'complete') fields.newPasswordConfirm = '새 비밀번호와 확인 값이 같은지 확인해 주세요.';
    }
  }
  if (Object.keys(fields).length) return new PasswordResetError(code, { status: 'error', fields });
  const message = status === 429 && code === 'PASSWORD_RESET_RATE_LIMITED'
    ? '비밀번호 재설정 요청이 많습니다. 잠시 후 다시 시도해 주세요.'
    : status === 403 && code === 'CSRF_TOKEN_INVALID'
      ? '보안 확인 정보가 유효하지 않습니다. 다시 제출하면 새 정보를 받아 요청합니다.'
      : status === 400 && code === 'INVALID_REQUEST_BODY'
        ? '요청 내용을 확인하지 못했습니다. 입력값을 확인하고 다시 시도해 주세요.'
        : '처리 결과를 확인하지 못했습니다. 이미 처리되었을 수 있으니 메일함 또는 새 비밀번호로 로그인을 확인한 뒤 다시 시도해 주세요.';
  return new PasswordResetError(code, { status: 'error', fields: {}, message });
}
export function resetErrorState(error: unknown): ResetState {
  return error instanceof PasswordResetError ? error.state : { status: 'error', fields: {},
    message: '요청을 준비하지 못했습니다. 연결을 확인하고 다시 시도해 주세요.' };
}
type Transport = { base?: string; fetcher?: typeof fetch };
async function send(kind: 'request' | 'complete', values: { email: string } | ResetValues, csrf: CsrfContext, transport: Transport = {}): Promise<void> {
  const base = transport.base ?? process.env.NEXT_PUBLIC_API_BASE_URL;
  if (!base) throw new PasswordResetError('API_NOT_CONFIGURED', { status: 'error', fields: {}, message: '인증 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.' });
  let response: Response;
  try {
    response = await (transport.fetcher ?? fetch)(base.replace(/\/$/, '') + '/api/v1/auth/password-reset/' + kind, {
      method: 'POST', credentials: 'include', cache: 'no-store', redirect: 'error', referrerPolicy: 'no-referrer',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
      body: JSON.stringify(values), signal: AbortSignal.timeout(15_000),
    });
  } catch { throw failure(0, { code: 'NETWORK_ERROR' }, kind); }
  // 204에는 본문을 읽지 않는다. 불확실한 POST는 자동 재전송하지 않는다.
  if (kind === 'complete' && response.status === 204) return;
  let body: unknown = null;
  try { if (/^application\/json(?:;|$)/i.test(response.headers.get('content-type') ?? '')) body = await response.json(); } catch { /* 안전한 공통 안내 */ }
  if (kind === 'request' && response.status === 202 && object(body) && body.message === RESET_ACCEPTED) return;
  throw failure(response.status, body, kind);
}
export const requestPasswordReset = (email: string, csrf: CsrfContext, transport?: Transport) => send('request', { email }, csrf, transport);
export const completePasswordReset = (values: ResetValues, csrf: CsrfContext, transport?: Transport) => send('complete', values, csrf, transport);
