import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './helpers/load-typescript.mjs';
const { createAuthSession } = await loadTypeScript('../src/lib/auth-session.ts', import.meta.url);
const { AuthApiError } = await loadTypeScript('../src/lib/auth-api.ts', import.meta.url);
const member = { memberId: 1, email: 'member@example.test', nickname: '회원', role: 'USER' };
const credentials = { email: member.email, password: 'test-only-value' };
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; };
const flush = async () => { for (let i = 0; i < 10; i++) await Promise.resolve(); };
const uncertain = kind => new AuthApiError(kind, 0, 'NETWORK_ERROR', 'result unknown', {}, true);
function setup(overrides = {}) {
  const calls = [];
  const client = {
    getCurrentMember: async () => { calls.push('me'); return null; },
    getAuthCsrf: async () => { calls.push('csrf'); return { headerName: 'X-CSRF-TOKEN', token: 'test-only' }; },
    login: async () => { calls.push('login'); return member; },
    logout: async () => { calls.push('logout'); },
    ...overrides,
  };
  const session = createAuthSession(client); session.start();
  return { session, client, calls };
}
test('초기 확인은 checking이며 정상 비로그인·조회 실패·회원 복원을 구분', async () => {
  for (const expected of ['anonymous', 'authenticated', 'error']) {
    const { session } = setup({ getCurrentMember: async () => {
      if (expected === 'error') throw new Error('offline'); return expected === 'authenticated' ? member : null;
    } });
    assert.equal(session.getSnapshot().status, 'checking');
    await flush(); assert.equal(session.getSnapshot().status, expected); session.stop();
  }
});
test('로그인 결과와 입력 초기화는 새 CSRF 조회 완료보다 먼저 반영', async () => {
  const { session, client, calls } = setup(); await flush();
  const renewed = deferred(); let csrfCount = 0; let cleared = false;
  client.getAuthCsrf = async () => { calls.push('csrf'); if (++csrfCount === 2) return renewed.promise; return { headerName: 'X-CSRF-TOKEN', token: 'old-test' }; };
  const login = session.signIn(credentials, () => { cleared = true; }); await flush();
  assert.equal(cleared, true); assert.equal(session.getSnapshot().status, 'authenticated');
  assert.equal(session.getSnapshot().pending, 'login');
  renewed.resolve({ headerName: 'X-CSRF-TOKEN', token: 'new-test' });
  assert.deepEqual(await login, { kind: 'authenticated', csrfReady: true, recovered: false });
  assert.deepEqual(calls, ['me', 'csrf', 'login', 'csrf']);
  assert.equal(session.getSnapshot().pending, null); session.stop();
});
test('로그인 성공 뒤 CSRF 실패는 성공 회원을 지우거나 POST를 반복하지 않음', async () => {
  const { session, client, calls } = setup(); await flush(); let count = 0;
  client.getAuthCsrf = async () => { if (++count === 2) throw new Error('offline'); return {}; };
  const result = await session.signIn(credentials, () => {});
  assert.equal(result.kind, 'authenticated'); assert.equal(result.csrfReady, false);
  assert.deepEqual(session.getSnapshot().member, member); assert.equal(calls.filter(call => call === 'login').length, 1); session.stop();
});
for (const outcome of ['anonymous', 'other-member', 'failure']) {
  test(`늦은 초기 /me ${outcome}가 새 로그인 결과를 덮어쓰지 않음`, async () => {
    const old = deferred(); const { session } = setup({ getCurrentMember: () => old.promise });
    await session.signIn(credentials, () => {});
    if (outcome === 'failure') old.reject(new Error('offline'));
    else old.resolve(outcome === 'anonymous' ? null : { ...member, memberId: 2 });
    await flush(); assert.deepEqual(session.getSnapshot().member, member); session.stop();
  });
}
test('늦은 이전 회원 조회가 로그아웃 결과를 복원하지 않음', async () => {
  const old = deferred(); const { session } = setup({ getCurrentMember: () => old.promise });
  await session.signIn(credentials, () => {});
  assert.equal((await session.signOut()).confirmedBy, 'logout');
  old.resolve(member); await flush();
  assert.equal(session.getSnapshot().status, 'anonymous'); assert.equal(session.getSnapshot().member, null); session.stop();
});
test('여러 상태 재조회 중 가장 최근 응답만 사용', async () => {
  const { session, client } = setup(); await flush(); const old = deferred();
  client.getCurrentMember = () => old.promise; const first = session.refresh();
  client.getCurrentMember = async () => member; await session.refresh();
  old.resolve(null); await first; assert.equal(session.getSnapshot().status, 'authenticated'); session.stop();
});
test('POST 결과 불확실 시 /me로 확인한 로그인은 recovered로 구분', async () => {
  const { session, client } = setup(); await flush(); let posts = 0;
  client.login = async () => { posts++; throw uncertain('login'); };
  client.getCurrentMember = async () => member;
  const result = await session.signIn(credentials, () => {});
  assert.equal(result.recovered, true); assert.equal(session.getSnapshot().status, 'authenticated'); assert.equal(posts, 1); session.stop();
});
test('POST와 /me 모두 연결 실패면 비로그인 확정 대신 error', async () => {
  const { session, client } = setup(); await flush();
  client.login = async () => { throw uncertain('login'); };
  client.getCurrentMember = async () => { throw new Error('offline'); };
  await assert.rejects(session.signIn(credentials, () => {}));
  assert.equal(session.getSnapshot().status, 'error'); session.stop();
});
test('명확한 자격 증명 실패는 회원 조회나 POST 자동 반복 없이 사용자에게 전달', async () => {
  const { session, client, calls } = setup(); await flush();
  client.login = async () => { calls.push('login'); throw new AuthApiError('login', 401, 'LOGIN_FAILED', 'check credentials'); };
  await assert.rejects(session.signIn(credentials, () => {}), error => error.code === 'LOGIN_FAILED');
  assert.deepEqual(calls, ['me', 'csrf', 'login']); assert.equal(session.getSnapshot().status, 'anonymous'); session.stop();
});
for (const action of ['login', 'logout']) {
  test(`${action} CSRF 오류는 GET 갱신만 하고 수동 제출을 기다림`, async () => {
    const { session, client, calls } = setup({ getCurrentMember: async () => member }); await flush();
    client[action] = async () => { calls.push(action); throw new AuthApiError(action, 403, 'CSRF_TOKEN_INVALID', 'csrf'); };
    await assert.rejects(action === 'login' ? session.signIn(credentials, () => {}) : session.signOut());
    assert.deepEqual(calls, ['csrf', action, 'csrf']); assert.equal(session.getSnapshot().status, 'authenticated'); session.stop();
  });
}
test('로그아웃 204 뒤 회원 제거, 다음 로그인은 다시 CSRF를 조회', async () => {
  const { session, calls } = setup({ getCurrentMember: async () => member }); await flush();
  assert.deepEqual(await session.signOut(), { kind: 'anonymous', confirmedBy: 'logout' });
  assert.equal(session.getSnapshot().member, null); assert.deepEqual(calls, ['csrf', 'logout']);
  await session.signIn(credentials, () => {});
  assert.deepEqual(calls, ['csrf', 'logout', 'csrf', 'login', 'csrf']); session.stop();
});
for (const state of ['anonymous', 'authenticated', 'error']) {
  test(`로그아웃 응답 불확실 후 /me ${state}`, async () => {
    const { session, client } = setup({ getCurrentMember: async () => member }); await flush(); let posts = 0;
    client.logout = async () => { posts++; throw uncertain('logout'); };
    client.getCurrentMember = async () => { if (state === 'error') throw new Error('offline'); return state === 'authenticated' ? member : null; };
    if (state === 'anonymous') assert.equal((await session.signOut()).confirmedBy, 'me');
    else await assert.rejects(session.signOut());
    assert.equal(session.getSnapshot().status, state); assert.equal(posts, 1); session.stop();
  });
}
test('CSRF 준비 실패 시 logout POST를 보내거나 기존 회원을 지우지 않음', async () => {
  const { session, client, calls } = setup({ getCurrentMember: async () => member }); await flush();
  client.getAuthCsrf = async () => { throw new Error('offline'); };
  await assert.rejects(session.signOut()); assert.equal(session.getSnapshot().status, 'authenticated'); assert.equal(calls.length, 0); session.stop();
});
test('동시에 들어온 로그인·로그아웃 및 조회가 진행 중인 인증 변경을 추월하지 않음', async () => {
  const blocked = deferred(); const { session, client, calls } = setup(); await flush();
  client.login = async () => { calls.push('login'); return blocked.promise; };
  const first = session.signIn(credentials, () => {}); await flush();
  assert.equal((await session.signIn(credentials, () => {})).kind, 'ignored');
  assert.equal((await session.signOut()).kind, 'ignored'); await session.refresh();
  assert.deepEqual(calls, ['me', 'csrf', 'login']); blocked.resolve(member); await first; session.stop();
});
test('최초 조회 중 CSRF 준비 실패도 checking 상태에 멈추지 않음', async () => {
  const initial = deferred(); const { session, client } = setup({ getCurrentMember: () => initial.promise });
  client.getAuthCsrf = async () => { throw new Error('offline'); };
  client.getCurrentMember = async () => null;
  await assert.rejects(session.signIn(credentials, () => {}));
  assert.equal(session.getSnapshot().status, 'anonymous'); initial.resolve(member); await flush();
  assert.equal(session.getSnapshot().status, 'anonymous'); session.stop();
});
test('Provider 해제 후 오래된 응답과 후속 CSRF 조회는 상태를 변경하지 않음', async () => {
  const blocked = deferred(); const { session, client, calls } = setup(); await flush();
  client.login = () => blocked.promise; const result = session.signIn(credentials, () => assert.fail('unmounted callback')); await flush();
  session.stop(); blocked.resolve(member); assert.equal((await result).kind, 'ignored');
  assert.deepEqual(calls, ['me', 'csrf']);
  session.start(); await flush(); assert.equal(session.getSnapshot().status, 'anonymous'); session.stop();
});

test('explicit expiration emits one notice; initial anonymous and logout never impersonate expiry', async () => {
  const {session,client}=setup();await flush();assert.equal(session.getExpiryNotice(),null);
  client.getCurrentMember=async()=>member;await session.refresh();const old=session.captureMemberRequest(member.memberId);
  assert(old.expire());const notice=session.getExpiryNotice();assert(notice);assert.equal(old.expire(),false);assert.equal(session.getExpiryNotice(),notice);
  session.dismissExpiryNotice(notice.id+1);assert.equal(session.getExpiryNotice(),notice);session.dismissExpiryNotice(notice.id);assert.equal(session.getExpiryNotice(),null);
  await session.signIn(credentials,()=>{});await session.signOut();assert.equal(session.getExpiryNotice(),null);session.stop();
});
test('refresh expiry is explicit validated anonymous, not lookup failure or initial lookup',async()=>{
  const {session,client}=setup({getCurrentMember:async()=>member});await flush();client.getCurrentMember=async()=>{throw new Error('offline')};await session.refresh();assert.equal(session.getExpiryNotice(),null);
  client.getCurrentMember=async()=>member;await session.refresh();client.getCurrentMember=async()=>null;await session.refresh();assert(session.getExpiryNotice());session.stop();
});
test('withdrawal and successful password relogin do not announce timeout; actual 401 does',async()=>{
  for(const reason of ['withdrawal','credentials','expired']){
    const{session}=setup({getCurrentMember:async()=>member});await flush();
    if(reason==='withdrawal')session.expire(member.memberId,reason);else session.beginCredentialChange(member.memberId).expire(reason);
    assert.equal(!!session.getExpiryNotice(),reason==='expired');session.stop();
  }
});
test('stale session cannot emit expiration or dismiss newer notice',async()=>{
 const{session}=setup({getCurrentMember:async()=>member});await flush();const old=session.captureMemberRequest(member.memberId);await session.signIn(credentials,()=>{});assert.equal(old.expire(),false);assert.equal(session.getExpiryNotice(),null);session.captureMemberRequest(member.memberId).expire();const first=session.getExpiryNotice();await session.signIn(credentials,()=>{});assert.equal(session.getExpiryNotice(),null);session.expire(member.memberId);const next=session.getExpiryNotice();session.dismissExpiryNotice(first.id);assert.equal(session.getExpiryNotice(),next);session.stop();
});
