import { getCsrfContext, SignupApiError, type CsrfContext } from './signup-api';

export type Member = { memberId: number; email: string; nickname: string; role: 'USER' | 'ADMIN' };
export type LoginRequest = { email: string; password: string };
export type LoginFieldErrors = Partial<Record<'email' | 'password', string>>;
type RequestKind = 'csrf' | 'login' | 'me' | 'logout';

export class AuthApiError extends Error {
  constructor(
    public request: RequestKind,
    public status: number,
    public code: string,
    message: string,
    public fieldErrors: LoginFieldErrors = {},
    public uncertain = false,
  ) {
    super(message);
    this.name = 'AuthApiError';
  }
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
function isMember(value: unknown): value is Member {
  return isObject(value) && Number.isSafeInteger(value.memberId) && Number(value.memberId) > 0
    && typeof value.email === 'string' && value.email.length <= 254 && /^[^\s@]+@[^\s@]+$/.test(value.email)
    && typeof value.nickname === 'string' && value.nickname.trim().length > 0
    && (value.role === 'USER' || value.role === 'ADMIN');
}
function memberFrom(body: Member): Member {
  // 예상하지 않은 응답 필드는 전역 회원 상태에 복사하지 않는다.
  return { memberId: body.memberId, email: body.email, nickname: body.nickname, role: body.role };
}
function errorBody(body: unknown): body is { code: string; message: string; fieldErrors: { field: string; message: string }[] } {
  return isObject(body) && typeof body.code === 'string' && typeof body.message === 'string'
    && Array.isArray(body.fieldErrors) && body.fieldErrors.every(field =>
      isObject(field) && typeof field.field === 'string' && typeof field.message === 'string');
}
function responseError(kind: RequestKind, status: number, body: unknown): AuthApiError {
  const code = errorBody(body) ? body.code : '';
  if (status >= 500) return new AuthApiError(kind, status, 'SERVER_ERROR',
    '서버 오류로 처리 결과를 확인하지 못했습니다. 잠시 후 다시 확인해 주세요.', {}, kind !== 'me');
  if (kind === 'login' && status === 401 && code === 'LOGIN_FAILED') {
    return new AuthApiError(kind, status, code, '이메일 또는 비밀번호를 확인해 주세요.');
  }
  if (kind === 'login' && status === 403 && code === 'EMAIL_VERIFICATION_REQUIRED') {
    return new AuthApiError(kind, status, code, '이메일 인증을 완료한 후 로그인해 주세요.');
  }
  if (kind === 'login' && status === 403 && code === 'ACCOUNT_UNAVAILABLE') {
    return new AuthApiError(kind, status, code, '현재 이 계정으로 로그인할 수 없습니다. 계정 상태를 확인하거나 문의해 주세요.');
  }
  if ((kind === 'login' || kind === 'logout') && status === 403 && code === 'CSRF_TOKEN_INVALID') {
    return new AuthApiError(kind, status, code,
      '보안 확인 정보가 유효하지 않습니다. 버튼을 다시 누르면 정보를 새로 받아 요청합니다.');
  }
  if (kind === 'login' && status === 400 && (code === 'VALIDATION_FAILED' || code === 'INVALID_REQUEST_BODY')) {
    const fields: LoginFieldErrors = {};
    if (errorBody(body) && code === 'VALIDATION_FAILED') {
      for (const field of body.fieldErrors) {
        if (field.field === 'email') fields.email = '이메일 형식과 길이(최대 254자)를 확인해 주세요.';
        if (field.field === 'password') fields.password = '비밀번호를 입력하고 UTF-8 기준 72바이트 이하인지 확인해 주세요.';
      }
    }
    return new AuthApiError(kind, status, code, '입력값을 확인해 주세요.', fields);
  }
  return new AuthApiError(kind, status, 'UNEXPECTED_RESPONSE',
    '서버 응답을 확인하지 못해 처리 결과를 알 수 없습니다. 현재 로그인 상태를 확인한 뒤 다시 시도해 주세요.',
    {}, kind === 'login' || kind === 'logout');
}
async function request(path: string, kind: RequestKind, options: RequestInit = {}): Promise<Response> {
  const base = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (!base) throw new AuthApiError(kind, 0, 'API_NOT_CONFIGURED', '인증 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.');
  try {
    return await fetch(base.replace(/\/$/, '') + path, {
      ...options, credentials: 'include', cache: 'no-store', redirect: 'error', referrerPolicy: 'no-referrer',
      headers: { Accept: 'application/json', ...options.headers }, signal: AbortSignal.timeout(15_000),
    });
  } catch {
    throw new AuthApiError(kind, 0, 'NETWORK_ERROR',
      kind === 'me' ? '서버 연결 문제로 로그인 상태를 확인하지 못했습니다.'
        : '서버와 통신하지 못해 처리 결과를 확인할 수 없습니다. 요청이 이미 처리되었을 수 있으니 현재 로그인 상태를 확인해 주세요.',
      {}, kind === 'login' || kind === 'logout');
  }
}
async function readJson(response: Response): Promise<unknown> {
  if (!/^application\/json(?:;|$)/i.test(response.headers.get('content-type') ?? '')) return null;
  try { return await response.json(); } catch { return null; }
}

export async function getAuthCsrf(): Promise<CsrfContext> {
  try { return await getCsrfContext(); }
  catch (error) {
    // 기존 가입 전용 오류 문구나 임의의 서버 message를 로그인 화면에 노출하지 않는다.
    throw new AuthApiError('csrf', error instanceof SignupApiError ? error.status : 0,
      error instanceof SignupApiError ? error.code : 'UNEXPECTED_RESPONSE',
      '보안 확인 정보를 가져오지 못해 요청을 보내지 않았습니다. 연결을 확인하고 다시 시도해 주세요.');
  }
}
export async function login(values: LoginRequest, csrf: CsrfContext): Promise<Member> {
  const response = await request('/api/v1/auth/login', 'login', {
    method: 'POST', headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
    // 비밀번호를 trim·변환·해시하지 않는다.
    body: JSON.stringify({ email: values.email, password: values.password }),
  });
  const body = await readJson(response);
  if (response.status !== 200 || !isMember(body)) throw responseError('login', response.status, body);
  return memberFrom(body);
}
export async function getCurrentMember(): Promise<Member | null> {
  const response = await request('/api/v1/auth/me', 'me', { method: 'GET' });
  const body = await readJson(response);
  if (response.status === 401 && errorBody(body) && body.code === 'AUTHENTICATION_REQUIRED') return null;
  if (response.status !== 200 || !isMember(body)) throw responseError('me', response.status, body);
  return memberFrom(body);
}
export async function logout(csrf: CsrfContext): Promise<void> {
  const response = await request('/api/v1/auth/logout', 'logout', {
    method: 'POST', headers: { [csrf.headerName]: csrf.token },
  });
  // 204는 빈 본문이다. 성공 시 JSON 파싱을 하지 않는다.
  if (response.status === 204) return;
  throw responseError('logout', response.status, await readJson(response));
}
