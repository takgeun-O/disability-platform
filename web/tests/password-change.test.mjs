import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './helpers/load-typescript.mjs';
const form = await loadTypeScript('../src/lib/password-change.ts', import.meta.url);
const api = await loadTypeScript('../src/lib/password-change-api.ts', import.meta.url);
const { createAuthSession } = await loadTypeScript('../src/lib/auth-session.ts', import.meta.url);
// Synthetic fixtures only. No real credentials in these tests or their reports.
const values = { currentPassword: ' old ', newPassword: ' New12345! ', newPasswordConfirm: ' New12345! ' };
const csrf = { headerName: 'X-TEST-CSRF', token: 'synthetic-csrf' };
const json = (body, status = 200, headers = {}) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } });
function env(t, value) { const old = process.env.NEXT_PUBLIC_API_BASE_URL; process.env.NEXT_PUBLIC_API_BASE_URL = value; t.after(() => { if (old === undefined) delete process.env.NEXT_PUBLIC_API_BASE_URL; else process.env.NEXT_PUBLIC_API_BASE_URL = old; }); }
const member = { memberId: 7, email: 'review@example.test', nickname: '검토회원', role: 'USER' };
const flush = async () => { for (let i = 0; i < 12; i++) await Promise.resolve(); };

test('Java isBlank, raw current value, UTF-16 length and UTF-8 boundary match backend DTO/service', () => {
  assert.deepEqual(form.validatePasswordChange(values), {});
  for (const currentPassword of ['', ' \t\n', '\u001c\u3000', '가'.repeat(25)]) assert(form.validatePasswordChange({ ...values, currentPassword }).currentPassword);
  for (const currentPassword of ['x', '1', '\u00a0', '\u202f', '\ufeff', '가'.repeat(24)]) assert.equal(form.validatePasswordChange({ ...values, currentPassword }).currentPassword, undefined);
  for (const newPassword of ['', '       ', 'abc1234', 'abcdefgh', '12345678', '가'.repeat(23) + 'Ab12']) assert(form.validatePasswordChange({ ...values, newPassword }).newPassword);
  for (const newPassword of ['가'.repeat(22) + 'Ab1234', '😀'.repeat(17) + 'Ab12', 'Ab12\n345']) assert.deepEqual(form.validatePasswordChange({ ...values, newPassword, newPasswordConfirm: newPassword }), {});
  for (const newPasswordConfirm of ['', ' \t\n', values.newPassword.trim()]) assert(form.validatePasswordChange({ ...values, newPasswordConfirm }).newPasswordConfirm);
});
test('Retry-After uses deadline, supports zero, rejects dates/malformed/overflow without guessed cooldown', () => {
  assert.equal(form.retryDeadline('7', 1000), 8000); assert.equal(form.remainingSeconds(8000, 1001), 7); assert.equal(form.remainingSeconds(8000, 8001), 0);
  assert.equal(form.retryDeadline('0', 1000), 1000);
  for (const v of [null, '', '-1', '1.5', 'Infinity', 'later', '999999999999999999999']) assert.equal(form.retryDeadline(v, 1000), null);
});
test('exact POST, raw values, cookie, no-store, dynamic CSRF; both strict boolean 200 results', async t => {
  t.mock.method(globalThis, 'fetch', async (url, init) => {
    assert.equal(url, 'https://api.example.test/api/v1/members/me/password'); assert.equal(init.method, 'POST');
    assert.deepEqual(JSON.parse(init.body), values); assert.equal(init.credentials, 'include'); assert.equal(init.cache, 'no-store');
    assert.equal(init.headers[csrf.headerName], csrf.token); assert.equal(init.redirect, 'error'); assert.equal(init.referrerPolicy, 'no-referrer');
    return json({ reloginRequired: flag });
  });
  env(t, 'https://api.example.test/');
  let flag = false; assert.deepEqual(await api.changePassword(values, csrf), { reloginRequired: false });
  flag = true; assert.deepEqual(await api.changePassword(values, csrf), { reloginRequired: true });
});
test('all error codes are safe and field-only errors are not duplicated; no POST replay', async t => {
  env(t, 'https://api.example.test'); let response, calls = 0;
  t.mock.method(globalThis, 'fetch', async () => { calls++; return response; });
  const cases = [
    [400, 'PASSWORD_CHANGE_INVALID_CURRENT_PASSWORD', 'currentPassword'], [400, 'PASSWORD_CHANGE_INVALID_NEW_PASSWORD', 'newPassword'],
    [400, 'PASSWORD_CHANGE_CURRENT_PASSWORD_MISMATCH', 'currentPassword'], [400, 'PASSWORD_CHANGE_PASSWORD_UNCHANGED', 'newPassword'],
    [400, 'VALIDATION_FAILED', 'newPasswordConfirm'], [401, 'AUTHENTICATION_REQUIRED'], [403, 'CSRF_TOKEN_INVALID'], [403, 'ACCESS_DENIED'], [429, 'PASSWORD_CHANGE_RATE_LIMITED'], [500, 'INTERNAL_SERVER_ERROR'],
  ];
  for (const [status, code, field] of cases) {
    response = json({ code, message: 'untrusted raw body', fieldErrors: [{ field: 'newPasswordConfirm', message: 'untrusted raw body' }, { field: 'memberId', message: 'untrusted' }] }, status, { 'Retry-After': '2' });
    const before = calls;
    await assert.rejects(api.changePassword(values, csrf), e => {
      assert(e instanceof api.PasswordChangeError); assert.equal(e.status, status); assert(!JSON.stringify(e.fields).includes('untrusted')); assert(!e.message.includes('untrusted'));
      if (field) { assert.equal(e.message, ''); assert.deepEqual(Object.keys(e.fields), [field]); }
      if (status === 429) assert(e.retryAt > Date.now()); if (status === 500) assert.equal(e.message, api.PASSWORD_CHANGE_UNCERTAIN);
      return true;
    }); assert.equal(calls, before + 1);
  }
});
test('malformed success, HTML, empty 204, network and timeout are uncertain and never retried', async t => {
  env(t, 'https://api.example.test'); let make, calls = 0;
  t.mock.method(globalThis, 'fetch', async () => { calls++; return make(); });
  for (make of [() => json({}), () => json({ reloginRequired: 'false' }), () => json({ reloginRequired: null }), () => new Response('<html>bad</html>'), () => new Response('{', { headers: { 'Content-Type': 'application/json' } }), () => new Response(null, { status: 204 }), () => { throw Error('offline'); }, () => { throw new DOMException('timeout', 'TimeoutError'); }]) {
    const before = calls;
    await assert.rejects(api.changePassword(values, csrf), e => e.message === api.PASSWORD_CHANGE_UNCERTAIN); assert.equal(calls, before + 1);
  }
});
for (const transition of ['same member login', 'other member login', 'logout', 'refresh', 'stop']) test(`captured auth generation cannot expire or update newer session after ${transition}`, async () => {
  let current = member;
  const session = createAuthSession({ getCurrentMember: async () => current, getAuthCsrf: async () => csrf, login: async () => current, logout: async () => {} });
  session.start(); await flush(); const captured = session.captureMemberRequest(member.memberId); assert(captured.isCurrent());
  if (transition.includes('login')) { if (transition.startsWith('other')) current = { ...member, memberId: 8 }; await session.signIn({ email: 'review@example.test', password: 'test-only' }, () => {}); }
  else if (transition === 'logout') await session.signOut(); else if (transition === 'refresh') await session.refresh(); else session.stop();
  assert.equal(captured.isCurrent(), false); assert.equal(captured.expire(), false);
  if (transition.includes('login')) assert.equal(session.getSnapshot().member.memberId, current.memberId);
  session.stop();
});
test('current generation expire clears private owner synchronously; nickname sync does not invalidate request', async () => {
  const session = createAuthSession({ getCurrentMember: async () => member }); session.start(); await flush();
  const captured = session.captureMemberRequest(7); session.updateNickname(7, '새닉네임'); assert(captured.isCurrent());
  let owner = 7; session.subscribe(() => { if (session.getSnapshot().status !== 'authenticated') owner = null; });
  assert(captured.expire()); assert.equal(owner, null); assert.equal(session.getSnapshot().status, 'anonymous'); assert.equal(captured.expire(), false); session.stop();
});
