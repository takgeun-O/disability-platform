import assert from 'node:assert/strict';
import { test } from 'node:test';
import { loadTypeScript } from './helpers/load-typescript.mjs';
const model = await loadTypeScript('../src/lib/community-posts.ts', import.meta.url);
const api = await loadTypeScript('../src/lib/community-api.ts', import.meta.url);
const values = { type: 'QUESTION', topic: 'HEARING_AID', title: '  질문  ', content: '  첫 줄\n둘째 줄  ' };
const item = { id: 7, type: 'QUESTION', topic: 'HEARING_AID', title: '질문', author: { memberId: 2, nickname: '검토회원' }, createdAt: '2026-10-14T00:00:00Z' };
const query = { type: 'QUESTION', topic: 'HEARING_AID', page: 0 };
const page = { items: [{ ...item, commentCount: 0 }], page: 0, size: 20, totalElements: 1, totalPages: 1, hasNext: false };
const csrf = { headerName: 'X-CSRF-TOKEN', token: 'test-only' };
function json(body, status = 200) { return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json;charset=UTF-8' } }); }
async function withFetch(handler, run) {
  const original = globalThis.fetch, previous = process.env.NEXT_PUBLIC_API_BASE_URL;
  process.env.NEXT_PUBLIC_API_BASE_URL = 'https://api.example.test'; globalThis.fetch = handler;
  try { await run(); } finally { globalThis.fetch = original; if (previous === undefined) delete process.env.NEXT_PUBLIC_API_BASE_URL; else process.env.NEXT_PUBLIC_API_BASE_URL = previous; }
}
test('기존 한글 링크와 enum 필터를 받아 안정적인 URL로 정규화', () => {
  assert.deepEqual(model.parsePostQuery(new URLSearchParams('type=질문·답변&topic=보청기&page=2')), { ...query, page: 2 });
  assert.equal(model.listHref(query), '/community/posts?type=QUESTION&topic=HEARING_AID');
  for (const page of ['-1', 'Infinity', 'abc', '99999999999']) assert.equal(model.parsePostQuery(new URLSearchParams({ page })).page, 0);
  assert.deepEqual(model.parsePostQuery(new URLSearchParams('type=__proto__&topic=unknown')), model.DEFAULT_QUERY);
});
test('상세/로그인 복귀 주소는 목록 경로만 허용하고 임의 주소·스크립트를 거절', () => {
  for (const path of ['https://evil.test/community', '//evil.test/community', '/login', 'javascript:alert(1)', '/community\\evil', '/community/posts/7']) assert.equal(model.safeListReturn(path), '/community/posts');
  assert.equal(model.safeListReturn('/community?type=일반&page=3&unsafe=1'), '/community?type=GENERAL');
});
test('Java strip/isBlank·유니코드 코드포인트 제한·본문 줄바꿈 유지', () => {
  assert.deepEqual(model.validatePostForm(values), {});
  assert.equal(model.normalizeTitle('\u3000제목\u001c'), '제목');
  assert.equal(model.normalizeTitle('\u00a0'), '\u00a0');
  assert.equal(model.normalizeTitle('\u2007'), '\u2007');
  assert.equal(model.normalizeTitle('\ufeff'), '\ufeff');
  assert.equal(model.characterCount('😀한글'), 3);
  assert.deepEqual(model.validatePostForm({ ...values, title: '😀'.repeat(120), content: '😀'.repeat(10000) }), {});
  assert.ok(model.validatePostForm({ ...values, title: '😀'.repeat(121) }).title);
  assert.ok(model.validatePostForm({ ...values, content: '😀'.repeat(10001) }).content);
  assert.deepEqual(Object.keys(model.validatePostForm({ type: '', topic: '', title: '\u3000', content: '\n \t' })), ['type', 'topic', 'title', 'content']);
});
test('목록 GET은 enum·0-based page·size를 전송, 쿠키 생략, CSRF 요청 없음', async () => {
  let count = 0;
  await withFetch(async (url, options) => { count++; const u = new URL(url); assert.equal(u.pathname, '/api/v1/community/posts');
    assert.deepEqual(Object.fromEntries(u.searchParams), { type: 'QUESTION', topic: 'HEARING_AID', page: '0', size: '20' });
    assert.equal(options.method, 'GET'); assert.equal(options.credentials, 'omit'); assert.equal(options.cache, 'no-store'); assert.equal(options.headers['X-CSRF-TOKEN'], undefined); return json(page);
  }, async () => assert.deepEqual(await api.getPosts(query), page));
  assert.equal(count, 1);
});
test('POST는 새 CSRF를 먼저 받고 네 필드만 전송, 본문 원문 보존', async () => {
  const calls = [];
  await withFetch(async (url, options) => { calls.push(url); if (url.endsWith('/csrf')) return json(csrf);
    assert.equal(options.headers['X-CSRF-TOKEN'], csrf.token); assert.equal(options.credentials, 'include'); assert.equal(options.redirect, 'error');
    assert.deepEqual(JSON.parse(options.body), { ...values, title: '질문' }); return json({ id: 8 }, 201);
  }, async () => { assert.deepEqual(await api.createPost({ ...values, authorId: 999 }), { id: 8 }); await api.createPost(values); });
  assert.equal(calls.filter(url => url.endsWith('/csrf')).length, 2);
});
for (const [name, make] of [
  ['HTML', () => new Response('<html>login</html>', { status: 200, headers: { 'Content-Type': 'text/html' } })],
  ['깨진 JSON', () => new Response('{', { status: 200, headers: { 'Content-Type': 'application/json' } })],
  ['누락 author', () => json({ ...page, items: [{ ...item, commentCount: 0, author: null }] })],
  ['다른 필터 항목', () => json({ ...page, items: [{ ...item, commentCount: 0, type: 'GENERAL' }] })],
  ['잘못된 페이징', () => json({ ...page, page: 1 })],
  ['잘못된 totalPages', () => json({ ...page, totalPages: 4 })],
  ['잘못된 날짜', () => json({ ...page, items: [{ ...item, commentCount: 0, createdAt: 'yesterday' }] })],
  ['unknown enum', () => json({ ...page, items: [{ ...item, commentCount: 0, topic: 'OTHER' }] })],
  ['서버 500', () => json({ code: 'SERVER_ERROR', message: 'private', fieldErrors: [] }, 500)],
]) test(`목록 응답 거절: ${name}`, async () => withFetch(async () => make(), async () => {
  await assert.rejects(api.getPosts(query), error => error instanceof api.CommunityApiError && !error.uncertain && !error.message.includes('private'));
}));
test('상세는 본문·updatedAt·id를 검증하고 XSS 문자열을 데이터로 반환', async () => {
  const detail = { ...item, content: '<script>bad()</script>\n본문', updatedAt: item.createdAt };
  await withFetch(async () => json(detail), async () => assert.deepEqual(await api.getPost('7'), detail));
  for (const bad of [{ ...detail, content: null }, { ...detail, updatedAt: null }, { ...detail, id: 8 }]) await withFetch(async () => json(bad), async () => assert.rejects(api.getPost('7')));
});
test('없는/잘못된 게시글 ID는 404 처리, 잘못된 ID는 요청하지 않음', async () => {
  await withFetch(async () => { throw Error('must not request'); }, async () => {
    for (const id of ['0', '-2', '1.5', 'abc', '9007199254740992']) await assert.rejects(api.getPost(id), error => error.status === 404);
  });
  await withFetch(async () => json({ code: 'POST_NOT_FOUND', message: '', fieldErrors: [] }, 404), async () => assert.rejects(api.getPost('7'), error => error.status === 404));
});
for (const [status, code] of [[401, 'AUTHENTICATION_REQUIRED'], [403, 'CSRF_TOKEN_INVALID'], [403, 'POST_WRITE_NOT_ALLOWED'], [400, 'VALIDATION_FAILED']]) {
  test(`POST ${status}/${code} 구분·자동 재전송 없음`, async () => {
    let posts = 0;
    await withFetch(async (url) => { if (url.endsWith('/csrf')) return json(csrf); posts++; return json({ code, message: 'private', fieldErrors: [{ field: 'title', message: '제목은 120자 이하여야 합니다.' }] }, status); },
      async () => assert.rejects(api.createPost(values), error => error.code === code && !error.uncertain && !error.message.includes('private')));
    assert.equal(posts, 1);
  });
}
for (const [name, response] of [
  ['안전하지 않은 ID', () => json({ id: 9007199254740992 }, 201)], ['누락 ID', () => json({}, 201)],
  ['잘못된 성공 상태', () => json({ id: 1 }, 200)], ['서버 오류', () => json({}, 500)],
  ['네트워크 단절', () => { throw new TypeError('failed'); }],
]) test(`POST 결과 불확실: ${name}, 중복 전송 금지`, async () => {
  let posts = 0;
  await withFetch(async url => { if (url.endsWith('/csrf')) return json(csrf); posts++; return response(); }, async () => assert.rejects(api.createPost(values), error => error.uncertain && /목록/.test(error.message)));
  assert.equal(posts, 1);
});
test('CSRF 준비 실패에는 POST를 보내지 않으며 저장 불확실로 오인하지 않음', async () => {
  let count = 0;
  await withFetch(async url => { count++; assert.ok(url.endsWith('/csrf')); return json({}, 500); }, async () => assert.rejects(api.createPost(values), error => error.code === 'CSRF_PREPARATION_FAILED' && !error.uncertain));
  assert.equal(count, 1);
});
test('취소된 GET은 모의 목록으로 대체하지 않음', async () => {
  const controller = new AbortController(); controller.abort();
  await withFetch(async (_url, options) => { assert.ok(options.signal.aborted); throw new DOMException('cancelled', 'AbortError'); }, async () => assert.rejects(api.getPosts(query, controller.signal), error => error.name === 'AbortError'));
});

const load = await loadTypeScript('../src/lib/community-load.ts', import.meta.url);
test('빈 첫 페이지와 범위 밖 페이지 구분 (마지막 페이지/전체 글 삭제 포함)', () => {
  for (const [page, totalPages, expected] of [[0,0,false],[0,4,false],[3,4,false],[4,4,true],[99,4,true],[1,0,true]])
    assert.equal(load.pageIsOutOfRange({page,totalPages}), expected);
});
test('손상된 404와 장애는 POST_NOT_FOUND로 오인하지 않음', async () => {
  for (const response of [new Response('<html>proxy 404</html>',{status:404}), json({},404),json({code:'SERVER_ERROR',message:'',fieldErrors:[]},500)]) {
    await withFetch(async()=>response,async()=>assert.rejects(api.getPost('7'),error=>!load.isMissingPost(error)));
  }
  assert.equal(load.isMissingPost({status:404,code:'POST_NOT_FOUND',message:''}),true);
});
test('서버용 공개 조회는 지정한 내부 base만 사용하고 자격증명/CSRF를 전달하지 않음', async () => {
  await withFetch(async(url,options)=>{
    assert.ok(url.startsWith('http://internal.example.test/api/v1/community/posts'));
    assert.equal(options.credentials,'omit'); assert.equal(options.cache,'no-store');
    assert.deepEqual(options.headers,{Accept:'application/json'}); return json(page);
  },async()=>assert.deepEqual(await api.getPosts(query,undefined,'http://internal.example.test'),page));
});

for (const count of [0, 3, Number.MAX_SAFE_INTEGER]) test(`목록 commentCount 허용: ${count}`, async () => {
  await withFetch(async () => json({...page, items:[{...item,commentCount:count}]}), async()=>assert.equal((await api.getPosts(query)).items[0].commentCount,count));
});
for (const count of [undefined, null, "3", -1, 1.5, Number.MAX_SAFE_INTEGER+1]) test(`목록 commentCount 거절: ${count}`, async () => {
  await withFetch(async () => json({...page, items:[{...item,commentCount:count}]}), async()=>assert.rejects(api.getPosts(query),e=>e.code === 'UNEXPECTED_RESPONSE'));
});
