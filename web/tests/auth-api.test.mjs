import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './helpers/load-typescript.mjs';
const api = await loadTypeScript('../src/lib/auth-api.ts', import.meta.url);
const form = await loadTypeScript('../src/lib/login-form.ts', import.meta.url);
const base = 'http://127.0.0.1:8082';
process.env.NEXT_PUBLIC_API_BASE_URL = base;
const member = { memberId: 1, email: 'member@example.test', nickname: '테스트회원', role: 'USER' };
const csrf = { headerName: 'X-CSRF-TOKEN', token: 'test-only-csrf' };
const values = { email: ' Member@Example.test ', password: ' 원문Test1! ' };
const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
const errorBody = code => ({ code, message: 'private server detail', fieldErrors: [] });

test('CSRF→login→새 CSRF 순서와 본문·쿠키·캐시·헤더·리디렉션 설정', async t => {
  const calls = [];
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push({ url, options });
    return url.endsWith('/csrf') ? json(csrf) : json({ ...member, ignoredField: 'private' });
  });
  assert.deepEqual(await api.login(values, await api.getAuthCsrf()), member);
  await api.getAuthCsrf();
  assert.deepEqual(calls.map(call => call.url), [base + '/api/v1/auth/csrf', base + '/api/v1/auth/login', base + '/api/v1/auth/csrf']);
  for (const { options } of calls) {
    assert.equal(options.credentials, 'include'); assert.equal(options.cache, 'no-store');
    assert.equal(options.redirect, 'error'); assert.equal(options.referrerPolicy, 'no-referrer');
  }
  assert.equal(calls[1].options.method, 'POST');
  assert.equal(calls[1].options.headers[csrf.headerName], csrf.token);
  assert.equal(calls[1].options.headers['Content-Type'], 'application/json');
  assert.deepEqual(JSON.parse(calls[1].options.body), values);
});

for (const [status, code] of [[401, 'LOGIN_FAILED'], [403, 'EMAIL_VERIFICATION_REQUIRED'], [403, 'ACCOUNT_UNAVAILABLE'], [403, 'CSRF_TOKEN_INVALID']]) {
  test(`로그인 ${status}/${code} 구분, 서버 임의 메시지 비노출`, async t => {
    const fetch = t.mock.method(globalThis, 'fetch', async () => json(errorBody(code), status));
    await assert.rejects(api.login(values, csrf), error => {
      assert.equal(error.code, code); assert.equal(error.status, status); assert.equal(error.uncertain, false);
      assert.doesNotMatch(error.message, /private server detail|가입/); return true;
    });
    assert.equal(fetch.mock.callCount(), 1);
  });
}
test('VALIDATION_FAILED는 해당 입력란에 고정된 안전한 메시지를 연결', async t => {
  t.mock.method(globalThis, 'fetch', async () => json({ ...errorBody('VALIDATION_FAILED'), fieldErrors: [
    { field: 'email', message: 'private' }, { field: 'password', message: 'private' }, { field: 'unknown', message: 'private' },
  ] }, 400));
  await assert.rejects(api.login(values, csrf), error => {
    assert.equal(error.code, 'VALIDATION_FAILED'); assert.deepEqual(Object.keys(error.fieldErrors), ['email', 'password']);
    assert.doesNotMatch(JSON.stringify(error.fieldErrors), /private/); return true;
  });
});

for (const [status, body, type] of [
  [201, member], [200, {}], [200, null], [200, [member]], [200, { ...member, memberId: '1' }],
  [200, { ...member, memberId: 0 }], [200, { ...member, memberId: Number.MAX_SAFE_INTEGER + 1 }],
  [200, { ...member, role: 'UNKNOWN' }], [200, { ...member, email: '' }], [200, { ...member, nickname: '' }],
  [200, '<html>private detail</html>', 'text/html'], [200, '{broken', 'application/json'],
  [200, JSON.stringify(member), 'text/html'],
]) {
  test(`잘못된 로그인 성공 계약 거절 ${status}/${JSON.stringify(body).slice(0, 50)}/${type}`, async t => {
    t.mock.method(globalThis, 'fetch', async () => type ? new Response(body, { status, headers: { 'Content-Type': type } }) : json(body, status));
    await assert.rejects(api.login(values, csrf), error => error.code === 'UNEXPECTED_RESPONSE' && error.uncertain);
  });
}
test('/me는 정확한 401/AUTHENTICATION_REQUIRED만 비로그인으로 처리', async t => {
  t.mock.method(globalThis, 'fetch', async (_url, options) => {
    assert.equal(options.method, 'GET'); assert.equal(options.credentials, 'include'); assert.equal(options.cache, 'no-store');
    return json(errorBody('AUTHENTICATION_REQUIRED'), 401);
  });
  assert.equal(await api.getCurrentMember(), null);
});
for (const [status, code] of [[401, 'LOGIN_FAILED'], [403, 'AUTHENTICATION_REQUIRED'], [401, 'OTHER']]) {
  test(`/me ${status}/${code}는 비로그인 확정이 아님`, async t => {
    t.mock.method(globalThis, 'fetch', async () => json(errorBody(code), status));
    await assert.rejects(api.getCurrentMember(), error => error.code === 'UNEXPECTED_RESPONSE');
  });
}
test('/me 회원 정보의 구조도 확인하고 추가 필드를 저장하지 않는다', async t => {
  const fetch = t.mock.method(globalThis, 'fetch', async () => json({ ...member, ignoredField: 'private' }));
  assert.deepEqual(await api.getCurrentMember(), member);
  fetch.mock.mockImplementation(async () => json({ ...member, role: null }));
  await assert.rejects(api.getCurrentMember(), error => error.code === 'UNEXPECTED_RESPONSE');
});
test('/me의 본문이 계약과 다르면 401이어도 비로그인으로 단정하지 않는다', async t => {
  t.mock.method(globalThis, 'fetch', async () => json({ code: 'AUTHENTICATION_REQUIRED' }, 401));
  await assert.rejects(api.getCurrentMember());
});
test('logout 204 성공은 JSON 파싱하지 않고 쿠키와 새 CSRF를 사용', async t => {
  const response = new Response(null, { status: 204 });
  const parse = t.mock.method(response, 'json', () => { throw new Error('204 must not be parsed'); });
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, base + '/api/v1/auth/logout'); assert.equal(options.method, 'POST');
    assert.equal(options.credentials, 'include'); assert.equal(options.cache, 'no-store');
    assert.equal(options.headers[csrf.headerName], csrf.token); assert.equal(options.redirect, 'error');
    return response;
  });
  assert.equal(await api.logout(csrf), undefined); assert.equal(parse.mock.callCount(), 0);
});
for (const [status, code, expected] of [[200, 'ACCEPTED', 'UNEXPECTED_RESPONSE'], [403, 'CSRF_TOKEN_INVALID', 'CSRF_TOKEN_INVALID'], [403, 'EMAIL_VERIFICATION_REQUIRED', 'UNEXPECTED_RESPONSE'], [401, 'AUTHENTICATION_REQUIRED', 'UNEXPECTED_RESPONSE']]) {
  test(`logout ${status}/${code}를 성공으로 처리하지 않음`, async t => {
    const fetch = t.mock.method(globalThis, 'fetch', async () => json(errorBody(code), status));
    await assert.rejects(api.logout(csrf), error => error.code === expected);
    assert.equal(fetch.mock.callCount(), 1);
  });
}
for (const name of ['login', 'logout', 'getCurrentMember']) {
  for (const failure of ['network', 'server']) {
    test(`${name} ${failure}: POST 반복 없이 안전한 오류`, async t => {
      const fetch = t.mock.method(globalThis, 'fetch', async () => {
        if (failure === 'network') throw new TypeError('offline');
        return new Response('<html>private detail</html>', { status: 500 });
      });
      const operation = name === 'login' ? api.login(values, csrf) : name === 'logout' ? api.logout(csrf) : api.getCurrentMember();
      await assert.rejects(operation, error => {
        assert.equal(error.code, failure === 'network' ? 'NETWORK_ERROR' : 'SERVER_ERROR');
        assert.equal(error.uncertain, name !== 'getCurrentMember'); assert.doesNotMatch(error.message, /private|<html>/); return true;
      });
      assert.equal(fetch.mock.callCount(), 1);
    });
  }
}
test('CSRF 오류에 가입 전용 문구가 없음', async t => {
  t.mock.method(globalThis, 'fetch', async () => json(errorBody('ANY_CODE'), 500));
  await assert.rejects(api.getAuthCsrf(), error => error.request === 'csrf' && !/가입|private/.test(error.message));
});
test('CSRF headerName·토큰·상태·JSON 형식 검사', async t => {
  const fetch = t.mock.method(globalThis, 'fetch', async () => json({ ...csrf, headerName: 'Authorization' }));
  for (const response of [json({ ...csrf, headerName: 'Authorization' }), json(csrf, 201), json({ ...csrf, token: '' }), new Response(JSON.stringify(csrf), { headers: { 'Content-Type': 'text/html' } })]) {
    fetch.mock.mockImplementation(async () => response);
    await assert.rejects(api.getAuthCsrf(), error => error.code === 'UNEXPECTED_RESPONSE');
  }
});
test('API 주소 누락은 전송 전에 실패하고 마지막 슬래시는 제거', async t => {
  const fetch = t.mock.method(globalThis, 'fetch', async url => { assert.equal(url, base + '/api/v1/auth/me'); return json(member); });
  delete process.env.NEXT_PUBLIC_API_BASE_URL;
  try {
    await assert.rejects(api.login(values, csrf), error => error.code === 'API_NOT_CONFIGURED');
    assert.equal(fetch.mock.callCount(), 0);
    process.env.NEXT_PUBLIC_API_BASE_URL = base + '/';
    assert.deepEqual(await api.getCurrentMember(), member);
  } finally { process.env.NEXT_PUBLIC_API_BASE_URL = base; }
});
test('로그인 검증은 UTF-8 72바이트 및 필수값만 적용하고 비밀번호 원문은 유지', () => {
  for (const password of ['a', '12', ' spaced ', '가'.repeat(24), 'a'.repeat(72)]) {
    assert.deepEqual(form.validateLoginForm({ ...values, password }), {});
  }
  for (const password of ['', '   ', '가'.repeat(25), 'a'.repeat(73)]) assert.ok(form.validateLoginForm({ ...values, password }).password);
  for (const email of ['', 'wrong', 'a'.repeat(250) + '@example.test']) assert.ok(form.validateLoginForm({ ...values, email }).email);
  assert.equal(values.password.startsWith(' '), true);
});
