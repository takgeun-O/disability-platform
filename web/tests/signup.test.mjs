import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import ts from 'typescript';

// 기존 node:test + TypeScript 도구를 재사용합니다. 브라우저나 별도 테스트 프레임워크가 필요 없습니다.
async function loadTypeScript(path) {
  const source = fs.readFileSync(new URL(path, import.meta.url), 'utf8');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
  });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
}
const api = await loadTypeScript('../src/lib/signup-api.ts');
const form = await loadTypeScript('../src/lib/signup-form.ts');
const fields = { email: ' Member@Example.COM ', password: ' Example123! ', passwordConfirm: ' Example123! ', nickname: ' FirstUser ' };
const agreements = form.createAgreements(true, true);
const request = form.buildSignupRequest(fields, agreements);
const base = 'http://127.0.0.1:8082';
process.env.NEXT_PUBLIC_API_BASE_URL = base;
const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

test('request includes the actual agreement choices and unchanged password values', () => {
  const selected = form.createAgreements(true, false);
  const payload = form.buildSignupRequest(fields, selected);
  assert.deepEqual(Object.keys(payload).sort(), ['agreements', 'email', 'nickname', 'password', 'passwordConfirm']);
  assert.equal(payload.password, fields.password);
  assert.equal(payload.passwordConfirm, fields.passwordConfirm);
  assert.deepEqual(payload.agreements, [
    { termsCode: 'SERVICE_TERMS', version: 'dev-v1', agreed: true },
    { termsCode: 'PRIVACY_COLLECTION_USE', version: 'dev-v1', agreed: false },
  ]);
});

test('basic validation accepts valid input and keeps whitespace in passwords', () => {
  assert.deepEqual(form.validateSignupForm(fields, agreements), {});
  const errors = form.validateSignupForm({ ...fields, passwordConfirm: fields.password.trim() }, agreements);
  assert.equal(errors.passwordConfirm, '비밀번호가 일치하지 않습니다.');
});

test('password byte limit differs from character count', () => {
  const allowed = 'Ab1' + '가'.repeat(23);
  const rejected = 'Ab1' + '가'.repeat(24);
  assert.deepEqual(form.validateSignupForm({ ...fields, password: allowed, passwordConfirm: allowed }, agreements), {});
  assert.match(form.validateSignupForm({ ...fields, password: rejected, passwordConfirm: rejected }, agreements).password, /72바이트/);
});

test('required agreements cannot be invented when the info route is opened directly', () => {
  for (const selected of [null, [], form.createAgreements(true, false)]) {
    assert.match(form.validateSignupForm(fields, selected).form, /약관/);
  }
});

test('field validation catches malformed email, weak password and reserved nickname', () => {
  const errors = form.validateSignupForm({ email: 'bad', password: 'abcdefgh', passwordConfirm: '', nickname: 'ADMIN' }, agreements);
  assert.deepEqual(Object.keys(errors), ['email', 'password', 'passwordConfirm', 'nickname']);
});

test('server field errors and agreement/unknown field errors all remain visible', () => {
  assert.deepEqual(form.mapSignupApiError({ code: 'VALIDATION_FAILED', message: '입력값 확인', fieldErrors: [
    { field: 'passwordConfirm', message: '불일치' },
    { field: 'agreements[0].version', message: '버전 확인' },
    { field: 'unknownField', message: '다른 입력 확인' },
  ] }), { passwordConfirm: '불일치', form: '버전 확인 다른 입력 확인' });
  assert.deepEqual(form.mapSignupApiError({ code: 'TERMS_VERSION_MISMATCH', message: '약관 확인', fieldErrors: [] }), { form: '약관 확인' });
});

for (const [code, field] of [['EMAIL_ALREADY_EXISTS', 'email'], ['NICKNAME_ALREADY_EXISTS', 'nickname']]) {
  test(`conflict ${code} maps to ${field}`, () => {
    assert.deepEqual(form.mapSignupApiError({ code, message: '중복', fieldErrors: [] }), { [field]: '중복' });
  });
}

test('CSRF GET and signup POST include credentials, token header and exact JSON body', async (t) => {
  const calls = [];
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push({ url, options });
    return calls.length === 1 ? json({ headerName: 'X-CSRF-TOKEN', token: 'test-token' }) : json({ status: 'PENDING' }, 201);
  });
  const token = await api.getCsrfToken();
  assert.deepEqual(await api.signup(request, token), { status: 'PENDING' });
  assert.equal(calls[0].url, base + '/api/v1/auth/csrf');
  assert.equal(calls[0].options.method, 'GET');
  assert.equal(calls[0].options.cache, 'no-store');
  assert.equal(calls[0].options.credentials, 'include');
  assert.equal(calls[1].url, base + '/api/v1/auth/signup');
  assert.equal(calls[1].options.credentials, 'include');
  assert.equal(calls[1].options.headers['Content-Type'], 'application/json');
  assert.equal(calls[1].options.headers['X-CSRF-TOKEN'], token);
  assert.deepEqual(JSON.parse(calls[1].options.body), request);
});

for (const [status, code] of [[400, 'VALIDATION_FAILED'], [409, 'EMAIL_ALREADY_EXISTS'], [403, 'CSRF_TOKEN_INVALID']]) {
  test(`HTTP ${status} becomes a typed error without retrying POST`, async (t) => {
    const fetchMock = t.mock.method(globalThis, 'fetch', async () => json({ code, message: '요청 확인', fieldErrors: [] }, status));
    await assert.rejects(api.signup(request, 'token'), error => error instanceof api.SignupApiError && error.status === status && error.code === code);
    assert.equal(fetchMock.mock.callCount(), 1);
  });
}

for (const [status, body] of [[400, '<html>Error</html>'], [500, 'SQL internal details'], [201, '{broken'], [200, '{"status":"PENDING"}'], [201, '{"status":"ACTIVE"}']]) {
  test(`unexpected response ${status} / ${body.slice(0, 15)} produces a safe failure`, async (t) => {
    t.mock.method(globalThis, 'fetch', async () => new Response(body, { status }));
    await assert.rejects(api.signup(request, 'token'), error => {
      assert.ok(error instanceof api.SignupApiError);
      assert.doesNotMatch(error.message, /<html>|SQL|internal details/);
      return true;
    });
  });
}

test('network failure does not automatically repeat an uncertain signup', async (t) => {
  const fetchMock = t.mock.method(globalThis, 'fetch', async () => { throw new TypeError('Failed to fetch'); });
  await assert.rejects(api.signup(request, 'token'), error => error.code === 'NETWORK_ERROR' && /확인할 수 없습니다/.test(error.message));
  assert.equal(fetchMock.mock.callCount(), 1);
});

test('malformed CSRF response cannot become a signup token', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => json({ token: 'token', headerName: 'Unexpected' }));
  await assert.rejects(api.getCsrfToken(), error => error.code === 'UNEXPECTED_RESPONSE');
});

test('absent API configuration fails before any request', async (t) => {
  delete process.env.NEXT_PUBLIC_API_BASE_URL;
  const fetchMock = t.mock.method(globalThis, 'fetch', async () => { throw new Error('must not fetch'); });
  try {
    await assert.rejects(api.getCsrfToken(), error => error.code === 'API_NOT_CONFIGURED');
    assert.equal(fetchMock.mock.callCount(), 0);
  } finally {
    process.env.NEXT_PUBLIC_API_BASE_URL = base;
  }
});
