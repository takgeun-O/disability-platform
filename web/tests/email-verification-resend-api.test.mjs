import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import ts from 'typescript';

async function load(path) {
  const source = fs.readFileSync(new URL(path, import.meta.url), 'utf8');
  const { outputText } = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
}
const api = await load('../src/lib/email-verification-resend-api.ts');
const signup = await load('../src/lib/signup-api.ts');
const form = await load('../src/lib/signup-form.ts');
const base = 'http://127.0.0.1:8082';
process.env.NEXT_PUBLIC_API_BASE_URL = base;
const request = { email: 'member@example.com' };
const json = (body, status = 202) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

test('CSRF 조회 후 재전송 POST가 같은 세션 쿠키와 정확한 계약을 사용한다', async t => {
  const calls = [];
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push({ url, options });
    return calls.length === 1 ? json({ headerName: 'X-CSRF-TOKEN', token: 'csrf-test' }, 200) : json({ status: 'ACCEPTED' });
  });
  const csrf = await signup.getCsrfToken();
  assert.deepEqual(await api.resendEmailVerification(request, csrf), { status: 'ACCEPTED' });
  assert.equal(calls.length, 2);
  assert.equal(calls[0].url, base + '/api/v1/auth/csrf');
  assert.equal(calls[0].options.method, 'GET');
  assert.equal(calls[1].url, base + '/api/v1/auth/email/resend');
  assert.equal(calls[1].options.method, 'POST');
  assert.equal(calls[1].options.redirect, 'error');
  for (const { options } of calls) {
    assert.equal(options.credentials, 'include');
    assert.equal(options.cache, 'no-store');
    assert.ok(options.signal instanceof AbortSignal);
  }
  assert.deepEqual(calls[1].options.headers, { Accept: 'application/json', 'Content-Type': 'application/json', 'X-CSRF-TOKEN': 'csrf-test' });
  assert.equal(calls[1].options.referrerPolicy, 'no-referrer');
  assert.deepEqual(JSON.parse(calls[1].options.body), request);
});

for (const [status, body, type] of [
  [200, '{"status":"ACCEPTED"}'], [201, '{"status":"ACCEPTED"}'], [202, '{"status":"PENDING"}'],
  [202, '{}'], [202, 'null'], [202, '[{"status":"ACCEPTED"}]'], [202, '{broken'],
  [202, '<html>private detail</html>', 'text/html'], [202, '{"status":"ACCEPTED"}', 'text/html'],
  [202, ''], [204, null], [302, '{"status":"ACCEPTED"}'],
]) {
  test(`정확한 JSON 202/ACCEPTED 외 응답 거절 (${status}, ${body}, ${type})`, async t => {
    const mock = t.mock.method(globalThis, 'fetch', async () => new Response(body, { status, headers: { 'Content-Type': type ?? 'application/json' } }));
    await assert.rejects(api.resendEmailVerification(request, 'csrf'), error => {
      assert.equal(error.code, 'UNEXPECTED_RESPONSE');
      assert.doesNotMatch(error.message, /private detail|<html>/);
      return true;
    });
    assert.equal(mock.mock.callCount(), 1);
  });
}
for (const [status, code, expected] of [
  [400, 'VALIDATION_FAILED', 'VALIDATION_FAILED'], [400, 'INVALID_REQUEST_BODY', 'INVALID_REQUEST_BODY'],
  [403, 'CSRF_TOKEN_INVALID', 'CSRF_TOKEN_INVALID'], [503, 'INTERNAL_SERVER_ERROR', 'SERVER_ERROR'],
  [429, 'PRIVATE_ACCOUNT_REASON', 'UNEXPECTED_RESPONSE'],
]) {
  test(`${code}: 서버 본문을 노출하지 않고 오류를 구분하며 자동 재시도하지 않는다`, async t => {
    const mock = t.mock.method(globalThis, 'fetch', async () => json({ code, message: 'private detail', fieldErrors: [{ field: 'email', message: 'secret detail' }] }, status));
    await assert.rejects(api.resendEmailVerification(request, 'csrf'), error => {
      assert.equal(error.code, expected);
      assert.doesNotMatch(error.message, /private detail|secret detail/);
      return true;
    });
    assert.equal(mock.mock.callCount(), 1);
  });
}

test('POST 네트워크 실패는 결과 불확실성을 안내하고 자동 재시도하지 않는다', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async () => { throw new TypeError('offline'); });
  await assert.rejects(api.resendEmailVerification(request, 'csrf'), error => {
    assert.equal(error.code, 'NETWORK_ERROR');
    assert.match(error.message, /이미 처리되었을 수/);
    return true;
  });
  assert.equal(mock.mock.callCount(), 1);
});
test('API 주소 누락 시 POST를 보내지 않는다', async t => {
  delete process.env.NEXT_PUBLIC_API_BASE_URL;
  const mock = t.mock.method(globalThis, 'fetch', async () => { throw new Error('must not call'); });
  try {
    await assert.rejects(api.resendEmailVerification(request, 'csrf'), error => error.code === 'API_NOT_CONFIGURED');
    assert.equal(mock.mock.callCount(), 0);
  } finally { process.env.NEXT_PUBLIC_API_BASE_URL = base; }
});
test('API 주소 끝 슬래시를 제거한다', async t => {
  process.env.NEXT_PUBLIC_API_BASE_URL = base + '/';
  t.mock.method(globalThis, 'fetch', async url => {
    assert.equal(url, base + '/api/v1/auth/email/resend');
    return json({ status: 'ACCEPTED' });
  });
  try { await api.resendEmailVerification(request, 'csrf'); } finally { process.env.NEXT_PUBLIC_API_BASE_URL = base; }
});
test('재전송과 가입의 공유 이메일 검증', () => {
  for (const value of ['', '   ', 'wrong', 'a b@example.com', 'a'.repeat(250) + '@example.com']) assert.ok(form.validateEmail(value));
  assert.equal(form.validateEmail(' User@Example.com '), undefined);
});
