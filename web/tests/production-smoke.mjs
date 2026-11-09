import assert from 'node:assert/strict';
const base = process.env.BASE_URL ?? 'http://127.0.0.1:8443';
const checks = [
 ['/', '지금 나에게 필요한'], ['/community', '서로의 질문과 경험을 나눠요.'],
 ['/community/posts?topic='+encodeURIComponent('보청기'), '보청기'],
 ['/community/posts/create?type='+encodeURIComponent('질문·답변'), '게시글 작성'],
 ['/search?q='+encodeURIComponent('보청기'), '보청기'], ['/login?from=%2Fcommunity', '로그인'],
 ['/register', '동의'], ['/register/info', '닉네임'], ['/register/verify', '인증 정보를 확인하고 있습니다.'], ['/register/resend', '인증 메일 재전송'], ['/welfare','보청기'],
 ['/forgot-password','비밀번호를 잊으셨나요?'], ['/reset-password','새 재설정 메일 요청'], ['/hospitals','준비 중입니다.'], ['/devices','준비 중입니다.'],
 ['/validation/hearing-aid-health-insurance', '공식 출처'],
];
for(const [path, expected] of checks) {
 const response=await fetch(base+path);
 assert.equal(response.status,200,path);
 const html=await response.text();
 const content=html.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi,'').replace(/<[^>]*>/g,' ').replace(/\s+/g,' ');
 assert.ok(content.includes(expected),`${path} must include public body in initial HTML`);
 if(path.includes('validation')) {
   for(const text of ['청각장애 등록','최대 99만 9천 원','양쪽','구입 후 1개월','국민건강보험공단']) assert.ok(content.includes(text),text);
 }
 console.log('PASS',path);
}
assert.equal((await fetch(base+'/nonexistent-migration-test-route')).status,404);
assert.ok((await (await fetch(base+'/robots.txt')).text()).includes('Disallow: /'));
console.log('PASS unknown route 404; prototype robots policy retained');

// 공개 API와 서버 HTML을 비교한다. 로딩 문구나 RSC/script 안의 데이터만으로 통과하지 않는다.
const api = process.env.API_BASE_URL ?? 'http://127.0.0.1:8082';
const plain = html => html.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi,'').replace(/<[^>]*>/g,' ')
  .replace(/&amp;/g,'&').replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&quot;/g,'"').replace(/&#x27;|&#39;/g,"'").replace(/\s+/g,' ');
const response = await fetch(api+'/api/v1/community/posts?page=0&size=20',{credentials:'omit'});
assert.equal(response.status,200,'public Spring list must be reachable');
const listing = await response.json();
assert(listing.items.length>0,'SSR content verification needs an existing post; no data created by this test');
const initialList=plain(await (await fetch(base+'/community/posts')).text());
for(const item of listing.items) { assert(initialList.includes(item.title));assert(initialList.includes(item.author.nickname)); }
const post=await (await fetch(api+'/api/v1/community/posts/'+listing.items[0].id,{credentials:'omit'})).json();
const detailResponse=await fetch(base+'/community/posts/'+post.id);assert.equal(detailResponse.status,200);
const detailHtml=await detailResponse.text(),detailBody=plain(detailHtml);
for(const text of [post.title,post.author.nickname,post.content.replace(/\s+/g,' ')])assert(detailBody.includes(text));
assert(detailHtml.includes('property="og:title"'));assert(detailHtml.includes('name="description"'));assert(detailHtml.includes('noindex'));
for(const agent of ['Mozilla/5.0','Twitterbot'])assert.equal((await fetch(base+'/community/posts/999999999',{headers:{'User-Agent':agent}})).status,404);
console.log('PASS Spring list/detail content in initial HTML; metadata; missing post HTTP 404 (browser/bot)');
