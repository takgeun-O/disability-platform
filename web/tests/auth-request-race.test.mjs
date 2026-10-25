import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './helpers/load-typescript.mjs';
const { createAuthSession } = await loadTypeScript('../src/lib/auth-session.ts', import.meta.url);
const { createMemberActivityCache } = await loadTypeScript('../src/lib/member-activity.ts', import.meta.url);
const member = { memberId: 7, email: 'review@example.test', nickname: '회원', role: 'USER' };
const flush = async () => { for (let i = 0; i < 10; i++) await Promise.resolve(); };
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };
async function setup() {
  let meCalls = 0;
  const client = { getCurrentMember: async () => { meCalls++; return member; }, getAuthCsrf: async () => ({}), login: async () => member, logout: async () => {} };
  const session = createAuthSession(client), cache = createMemberActivityCache();
  session.subscribe(() => { const s = session.getSnapshot(); cache.setOwner(s.status === 'authenticated' && !s.pending ? s.member.memberId : null); });
  session.start(); await flush();
  return { session, client, cache, meCalls: () => meCalls };
}
for (const arrival of ['before-result', 'after-result']) test(`old private 401 and success are ignored ${arrival} without AbortController`, async () => {
  const { session, cache, meCalls } = await setup();
  const old = session.captureMemberRequest(7), lease = cache.lease(7), snapshot = { items: [], tab: 'comments', segments: 3 };
  lease.write('entry', snapshot);
  const operation = session.beginCredentialChange(7); assert(operation?.isCurrent());
  assert.equal(session.getMemberRequestSnapshot().paused, true);
  assert.equal(session.getSnapshot().status, 'authenticated');
  assert.equal(session.captureMemberRequest(7).isCurrent(), false);
  if (arrival === 'after-result') operation.finish();
  assert.equal(old.isCurrent(), false); assert.equal(old.expire(), false); assert.equal(old.updateNickname('이전응답'), false);
  assert.equal(session.getSnapshot().member.nickname, member.nickname);
  assert.equal(lease.valid(), true); assert.deepEqual(lease.read('entry'), snapshot);
  operation.finish(); assert.equal(session.getMemberRequestSnapshot().paused, false);
  const fresh = session.captureMemberRequest(7); assert(fresh.isCurrent()); assert(fresh.updateNickname('현재응답'));
  assert.equal(meCalls(), 1); assert(fresh.expire()); assert.equal(session.getSnapshot().status, 'anonymous'); assert.equal(lease.valid(), false);
  session.stop();
});
test('credential operation survives its own read revision and finish is idempotent', async () => {
  const { session } = await setup();
  const before = session.getMemberRequestSnapshot(), operation = session.beginCredentialChange(7);
  assert.equal(session.beginCredentialChange(7), null); assert.equal(session.beginCredentialChange(8), null);
  assert(operation.isCurrent()); operation.finish(); assert(operation.isCurrent());
  const after = session.getMemberRequestSnapshot(); assert(after.revision > before.revision); assert.equal(after.paused, false);
  operation.finish(); assert.strictEqual(session.getMemberRequestSnapshot(), after); session.stop();
});
for (const same of [true, false]) test(`old operation/read cannot mutate or finish ${same ? 'same-member' : 'other-member'} login`, async () => {
  const { session, client, cache } = await setup(); const oldRead = session.captureMemberRequest(7), oldLease = cache.lease(7);
  const operation = session.beginCredentialChange(7), response = deferred(); client.login = () => response.promise;
  const login = session.signIn({ email: member.email, password: 'synthetic' }, () => {}); await flush();
  assert.equal(oldLease.valid(), false); assert.equal(session.getMemberRequestSnapshot().paused, false);
  const next = { ...member, memberId: same ? 7 : 8, nickname: '새로그인' }; response.resolve(next); await login;
  const snapshot = session.getMemberRequestSnapshot();
  assert.equal(oldRead.expire(), false); assert.equal(oldRead.updateNickname('이전'), false); assert.equal(operation.expire(), false);
  operation.finish(); assert.strictEqual(session.getMemberRequestSnapshot(), snapshot); assert.deepEqual(session.getSnapshot().member, next);
  assert(session.captureMemberRequest(next.memberId).isCurrent()); session.stop();
});
test('a stopped provider cannot be changed by old operation completion', async () => {
  const { session } = await setup(); const old = session.beginCredentialChange(7); session.stop();
  assert.equal(old.isCurrent(), false); assert.equal(old.expire(), false); old.finish();
  session.start(); await flush(); assert(session.captureMemberRequest(7).isCurrent()); assert.equal(session.getMemberRequestSnapshot().paused, false); session.stop();
});
test('current credential 401/relogin expires and clears personal cache, old reads stay powerless', async () => {
  const { session, cache } = await setup(); const old = session.captureMemberRequest(7), lease = cache.lease(7);
  const operation = session.beginCredentialChange(7); assert(operation.expire()); operation.finish();
  assert.equal(session.getSnapshot().status, 'anonymous'); assert.equal(session.getMemberRequestSnapshot().paused, false); assert.equal(lease.valid(), false); assert.equal(old.expire(), false); session.stop();
});
