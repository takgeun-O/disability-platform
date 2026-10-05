import assert from 'node:assert/strict';
const base = process.env.BASE_URL ?? 'http://127.0.0.1:8443';
const checks = [
 ['/', '필요한 장애 관련 정보를 찾아보세요'], ['/community', '질문, 경험, 일상을'],
 ['/community/posts?topic='+encodeURIComponent('보청기'), '보청기'],
 ['/community/posts/8', '오늘 아이 첫 보청기'],
 ['/community/posts/create?type='+encodeURIComponent('질문·답변'), '게시글 작성'],
 ['/search?q='+encodeURIComponent('보청기'), '보청기'], ['/login?from=%2Fcommunity', '로그인'],
 ['/register', '동의'], ['/register/info', '닉네임'], ['/register/verify', '이메일'], ['/welfare','보청기'],
 ['/forgot-password','준비 중입니다.'], ['/hospitals','준비 중입니다.'], ['/devices','준비 중입니다.'],
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
