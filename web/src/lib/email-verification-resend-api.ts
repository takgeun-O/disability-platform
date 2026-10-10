export type EmailVerificationResendResponse = { status: 'ACCEPTED' };

export class EmailVerificationResendApiError extends Error {
  constructor(public status: number, public code: string, message: string) {
    super(message);
    this.name = 'EmailVerificationResendApiError';
  }
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

export async function resendEmailVerification(
  request: { email: string }, csrfToken: string,
): Promise<EmailVerificationResendResponse> {
  const base = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (!base) {
    throw new EmailVerificationResendApiError(0, 'API_NOT_CONFIGURED',
      '인증 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.');
  }

  let response: Response;
  try {
    response = await fetch(base.replace(/\/$/, '') + '/api/v1/auth/email/resend', {
      method: 'POST',
      redirect: 'error', // 307/308을 따라 POST를 다시 보내지 않는다.
      credentials: 'include',
      cache: 'no-store',
      referrerPolicy: 'no-referrer',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': csrfToken,
      },
      body: JSON.stringify(request),
      signal: AbortSignal.timeout(15_000),
    });
  } catch {
    // 서버에서는 처리됐을 수 있다. POST를 자동 재시도하지 않는다.
    throw new EmailVerificationResendApiError(0, 'NETWORK_ERROR',
      '서버와 통신하지 못해 처리 결과를 확인할 수 없습니다. 요청이 이미 처리되었을 수 있으니 메일함을 확인한 뒤 다시 시도해 주세요.');
  }

  let body: unknown = null;
  if (/^application\/json(?:;|$)/i.test(response.headers.get('content-type') ?? '')) {
    try { body = await response.json(); } catch { /* HTML·깨진 JSON·빈 응답은 접수로 인정하지 않는다. */ }
  }
  if (response.status >= 500) {
    throw new EmailVerificationResendApiError(response.status, 'SERVER_ERROR',
      '서버 오류로 처리 결과를 확인하지 못했습니다. 메일함을 확인하고 잠시 후 다시 시도해 주세요.');
  }
  const code = isObject(body) && typeof body.code === 'string' ? body.code : '';
  if (response.status === 403 && code === 'CSRF_TOKEN_INVALID') {
    throw new EmailVerificationResendApiError(403, code,
      '보안 확인 정보가 유효하지 않습니다. 재전송 버튼을 다시 눌러 주세요.');
  }
  if (response.status === 400 && ['VALIDATION_FAILED', 'INVALID_REQUEST_BODY'].includes(code)) {
    throw new EmailVerificationResendApiError(400, code,
      '이메일 입력값을 확인한 뒤 다시 요청해 주세요.');
  }
  if (response.status !== 202 || !isObject(body) || body.status !== 'ACCEPTED') {
    throw new EmailVerificationResendApiError(response.status, 'UNEXPECTED_RESPONSE',
      '서버 응답을 확인하지 못해 접수 여부를 알 수 없습니다. 메일함을 확인한 뒤 다시 시도해 주세요.');
  }
  // 서버가 돌려준 임의의 message나 필드 오류를 화면에 그대로 전달하지 않는다.
  return { status: 'ACCEPTED' };
}
