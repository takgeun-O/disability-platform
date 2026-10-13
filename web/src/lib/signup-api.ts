// 브라우저에 공개해도 되는 API 주소만 NEXT_PUBLIC 환경변수로 읽습니다.
// 실제 요청은 Client Component의 이벤트 핸들러에서 시작합니다.
export type TermsCode = 'SERVICE_TERMS' | 'PRIVACY_COLLECTION_USE';
export type SignupAgreement = { termsCode: TermsCode; version: string; agreed: boolean };
export type SignupRequest = {
  email: string;
  password: string;
  passwordConfirm: string;
  nickname: string;
  agreements: SignupAgreement[];
};
export type SignupResponse = { status: 'PENDING' };
export type ApiFieldError = { field: string; message: string };
type ApiErrorResponse = { code: string; message: string; fieldErrors: ApiFieldError[] };

export class SignupApiError extends Error {
  status: number;
  code: string;
  fieldErrors: ApiFieldError[];

  constructor(status: number, code: string, message: string, fieldErrors: ApiFieldError[] = []) {
    super(message);
    this.name = 'SignupApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

function apiUrl(path: string): string {
  const base = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (!base) {
    throw new SignupApiError(0, 'API_NOT_CONFIGURED', '가입 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.');
  }
  return base.replace(/\/$/, '') + path;
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isApiError(value: unknown): value is ApiErrorResponse {
  return isObject(value) && typeof value.code === 'string' && typeof value.message === 'string'
    && Array.isArray(value.fieldErrors) && value.fieldErrors.every((error: unknown) =>
      isObject(error) && typeof error.field === 'string' && typeof error.message === 'string');
}

async function readJson(response: Response): Promise<unknown> {
  // 프록시·서버 장애 시 HTML이나 빈 응답이 와도 폼이 깨지지 않도록 처리합니다.
  try {
    return await response.json();
  } catch {
    return null;
  }
}

function responseError(response: Response, body: unknown): SignupApiError {
  if (response.status >= 500) {
    return new SignupApiError(response.status, 'SERVER_ERROR',
      '서버 오류로 가입 결과를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.');
  }
  if (isApiError(body)) {
    return new SignupApiError(response.status, body.code, body.message, body.fieldErrors);
  }
  return new SignupApiError(response.status, 'UNEXPECTED_RESPONSE',
    '가입 결과를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.');
}

// 가입·이메일 인증의 기존 문자열 인터페이스는 유지한다.
export async function getCsrfToken(): Promise<string> {
  return (await getCsrfContext()).token;
}

export type CsrfContext = { headerName: 'X-CSRF-TOKEN'; token: string };

// 인증 기능들이 공유하는 세션 CSRF 조회. 호출자가 기능에 맞는 오류 문구로 변환한다.
export async function getCsrfContext(): Promise<CsrfContext> {
  const url = apiUrl('/api/v1/auth/csrf');
  let response: Response;
  try {
    response = await fetch(url, {
      method: 'GET',
      redirect: 'error',
      referrerPolicy: 'no-referrer',
      credentials: 'include', // 서버의 세션 쿠키를 받고, 다음 요청에도 같은 쿠키를 보냅니다.
      cache: 'no-store',
      headers: { Accept: 'application/json' },
      signal: AbortSignal.timeout(15_000),
    });
  } catch {
    throw new SignupApiError(0, 'NETWORK_ERROR',
      '가입 요청을 준비하지 못했습니다. 서버 연결을 확인한 뒤 다시 시도해 주세요.');
  }
  const body = await readJson(response);
  if (!response.ok) throw responseError(response, body);
  if (response.status !== 200
      || !/^application\/json(?:;|$)/i.test(response.headers.get('content-type') ?? '')
      || !isObject(body) || body.headerName !== 'X-CSRF-TOKEN'
      || typeof body.token !== 'string' || !body.token) {
    throw new SignupApiError(response.status, 'UNEXPECTED_RESPONSE',
      '보안 확인 정보를 받지 못했습니다. 잠시 후 다시 시도해 주세요.');
  }
  return { headerName: body.headerName, token: body.token };
}

export async function signup(request: SignupRequest, csrfToken: string): Promise<SignupResponse> {
  const url = apiUrl('/api/v1/auth/signup');
  let response: Response;
  try {
    response = await fetch(url, {
      method: 'POST',
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': csrfToken,
      },
      body: JSON.stringify(request),
      signal: AbortSignal.timeout(15_000),
    });
  } catch {
    // 요청이 서버에 도착했을 수도 있으므로 POST를 자동으로 재전송하지 않습니다.
    throw new SignupApiError(0, 'NETWORK_ERROR',
      '서버와 통신하지 못해 가입 결과를 확인할 수 없습니다. 연결을 확인한 뒤 다시 시도해 주세요.');
  }
  const body = await readJson(response);
  // fetch는 400/409/500에서도 resolve되므로 HTTP 상태를 직접 확인해야 합니다.
  if (!response.ok) throw responseError(response, body);
  if (response.status !== 201 || !isObject(body) || body.status !== 'PENDING') {
    throw new SignupApiError(response.status, 'UNEXPECTED_RESPONSE',
      '가입 결과를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.');
  }
  return { status: 'PENDING' };
}
