import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './helpers/load-typescript.mjs';
const form = await loadTypeScript('../src/lib/password-reset.ts', import.meta.url);
const api = await loadTypeScript('../src/lib/password-reset-api.ts', import.meta.url);
const signup = await loadTypeScript('../src/lib/signup-api.ts', import.meta.url);
const nav = await loadTypeScript('../src/lib/navigation.ts', import.meta.url);
const token = 'A'.repeat(43), csrf = { headerName: 'X-CSRF-TOKEN', token: 'unit-test-csrf' };
const values = { token, newPassword: ' Test1234! ', newPasswordConfirm: ' Test1234! ' };
const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
const base = 'https://api.example.test';

test('token: strict shape and duplicate rejection; reset URL cannot become login return', () => {
  assert.equal(form.resetToken(token), token);
  for (const v of [undefined, '', [token], [token, token], token + ' ', token + '\n', 'a'.repeat(42), '!'.repeat(43)]) assert.equal(form.resetToken(v), null);
  for (const v of ['/reset-password?token=test', '/%72eset-password?token=test', '/reset-password/complete']) assert.equal(nav.safeReturnPath(v), '/');
});
test('Java-compatible length, ASCII letter/number, raw space and UTF-8 byte policy', () => {
  assert.deepEqual(form.validateResetPassword(values), {});
  for (const password of ['', '       ', 'Abc1234', 'abcdefgh', '12345678', '가'.repeat(23) + 'Ab12']) {
    assert(form.validateResetPassword({ ...values, newPassword: password }).newPassword);
  }
  const boundary = '가'.repeat(22) + 'Ab1234';
  assert.equal(new TextEncoder().encode(boundary).length, 72);
  assert.deepEqual(form.validateResetPassword({ token, newPassword: boundary, newPasswordConfirm: boundary }), {});
  assert(form.validateResetPassword({ ...values, newPasswordConfirm: values.newPassword.trim() }).newPasswordConfirm);
  assert(form.validateResetPassword({ ...values, newPasswordConfirm: '' }).newPasswordConfirm);
});
test('email validation uses existing rules', () => {
  for (const v of ['', '   ', 'not-mail', 'a'.repeat(255) + '@example.test']) assert(form.validateResetEmail(v).email);
  assert.deepEqual(form.validateResetEmail(' Member@Example.test '), {});
});
test('CSRF shared transport then POST: cookie/cache/referrer, exact fields and 204 without JSON', async () => {
  const calls = [];
  const fetcher = async (url, options) => { calls.push({ url, options }); if (url.endsWith('/csrf')) return json(csrf);
    return { status: 204, json() { assert.fail('204 must not read body'); } }; };
  const context = await signup.getCsrfContext(base, fetcher);
  await api.completePasswordReset(values, context, { base, fetcher });
  assert.equal(calls.length, 2);
  assert.equal(calls[1].url, base + '/api/v1/auth/password-reset/complete');
  assert.deepEqual(JSON.parse(calls[1].options.body), values);
  assert.equal(calls[1].options.headers[csrf.headerName], csrf.token);
  for (const { options } of calls) {
    assert.equal(options.credentials, 'include'); assert.equal(options.cache, 'no-store');
    assert.equal(options.redirect, 'error'); assert.equal(options.referrerPolicy, 'no-referrer');
  }
});
test('202 common message, malformed success rejected, one POST only', async () => {
  let calls = 0;
  await api.requestPasswordReset('unknown@example.test', csrf, { base, fetcher: async () => { calls++; return json({ message: form.RESET_ACCEPTED }, 202); } });
  assert.equal(calls, 1);
  await assert.rejects(api.requestPasswordReset('unknown@example.test', csrf, { base, fetcher: async () => json({ message: 'account exists' }, 202) }), e => e.code === 'UNEXPECTED_RESPONSE');
});
for (const [status, code, field] of [[400,'PASSWORD_RESET_PASSWORD_INVALID','newPassword'],[400,'PASSWORD_RESET_PASSWORD_UNCHANGED','newPassword'],[400,'VALIDATION_FAILED','newPasswordConfirm']]) {
  test(`field error ${code}: one field, no common duplicate and no raw server message`, async () => {
    await assert.rejects(api.completePasswordReset(values, csrf, { base, fetcher: async () => json({ code, message: 'PRIVATE', fieldErrors: [{ field, message: 'PRIVATE' }, { field, message: 'PRIVATE' }] }, status) }), e => {
      assert.equal(Object.keys(e.state.fields).length, 1); assert(e.state.fields[field]); assert.equal(e.state.message, undefined); assert(!JSON.stringify(e.state).includes('PRIVATE')); return true;
    });
  });
}
for (const [status, code] of [[429,'PASSWORD_RESET_RATE_LIMITED'],[403,'CSRF_TOKEN_INVALID'],[400,'INVALID_REQUEST_BODY'],[503,'SERVER_ERROR']]) {
  test(`general ${code} remains retryable without auto replay`, async () => {
    let calls = 0;
    await assert.rejects(api.requestPasswordReset('review@example.test', csrf, { base, fetcher: async () => { calls++; return json({ code, message: 'PRIVATE', fieldErrors: [] }, status); } }), e => {
      assert.equal(e.state.status, 'error'); assert.deepEqual(e.state.fields, {}); assert(e.state.message); assert(!e.state.message.includes('PRIVATE')); return true;
    }); assert.equal(calls, 1);
  });
}
test('unusable token is link error; network error is not token invalid and never replayed', async () => {
  await assert.rejects(api.completePasswordReset(values, csrf, { base, fetcher: async () => json({ code: 'PASSWORD_RESET_TOKEN_INVALID' }, 400) }), e => e.state.status === 'invalid-link');
  let calls = 0;
  await assert.rejects(api.completePasswordReset(values, csrf, { base, fetcher: async () => { calls++; throw Error('PRIVATE'); } }), e => e.code === 'NETWORK_ERROR' && e.state.status === 'error' && !e.message.includes('PRIVATE'));
  assert.equal(calls, 1);
});

const requestPolicy = await loadTypeScript('../src/lib/password-reset-request.ts', import.meta.url);
test('no-referrer native form compatibility is restricted to same-origin reset document POST', () => {
  const source = { origin: 'null', host: 'iyum.example', 'sec-fetch-site': 'same-origin', 'sec-fetch-mode': 'navigate', 'sec-fetch-dest': 'document', 'content-type': 'multipart/form-data; boundary=test' };
  const headers = new Headers(source);
  assert.equal(requestPolicy.resetFormOrigin('POST', '/reset-password', headers, 'https://iyum.example').get('origin'), 'https://iyum.example');
  assert.equal(headers.get('origin'), 'null'); // never mutate shared input
  for (const [key, value] of [['origin','https://evil.example'],['sec-fetch-site','cross-site'],['sec-fetch-site','same-site'],['sec-fetch-site',''],['sec-fetch-mode','cors'],['sec-fetch-dest','iframe'],['content-type','application/json'],['host','evil.example']]) {
    assert.equal(requestPolicy.resetFormOrigin('POST', '/reset-password', new Headers({...source,[key]:value}), 'https://iyum.example'), null);
  }
  assert.equal(requestPolicy.resetFormOrigin('GET','/reset-password',headers,'https://iyum.example'),null);
  assert.equal(requestPolicy.resetFormOrigin('POST','/login',headers,'https://iyum.example'),null);
});

const validation = await loadTypeScript('../src/lib/password-reset-validation.ts', import.meta.url);
test('preflight: 204 only, no credentials/CSRF, no cache/referrer, one encoded GET', async () => {
  const calls=[];
  assert.equal(await validation.validateResetLink(token,base,async(url,options)=>{ calls.push({url,options});return new Response(null,{status:204}); }),'valid');
  assert.equal(calls.length,1);
  assert.equal(new URL(calls[0].url).pathname,'/api/v1/auth/password-reset/validate');
  assert.equal(new URL(calls[0].url).searchParams.get('token'),token);
  const init=calls[0].options;
  assert.equal(init.method,'GET'); assert.equal(init.credentials,'omit'); assert.equal(init.cache,'no-store');
  assert.equal(init.referrerPolicy,'no-referrer');assert.equal(init.redirect,'error');assert.equal(init.body,undefined);
  assert.deepEqual(init.headers,{Accept:'application/json'});
});
test('preflight distinguishes exact invalid contract, rate limit, malformed and failed responses without replay',async()=>{
  for(const [status,body,want] of [
    [400,{code:'PASSWORD_RESET_TOKEN_INVALID',message:'private',fieldErrors:[]},'invalid'],
    [400,{code:'PASSWORD_RESET_TOKEN_INVALID'},'unavailable'],
    [400,{code:'OTHER',message:'private',fieldErrors:[]},'unavailable'],
    [200,{},'unavailable'],[202,{},'unavailable'],[401,{},'unavailable'],[503,{code:'PASSWORD_RESET_TOKEN_INVALID'},'unavailable'],[429,{},'limited']]){
    let calls=0;assert.equal(await validation.validateResetLink(token,base,async()=>{calls++;return json(body,status);}),want);assert.equal(calls,1);
  }
  assert.equal(await validation.validateResetLink(token,base,async()=>{throw Error('private token URL');}),'unavailable');
  assert.equal(await validation.validateResetLink('bad',base,async()=>assert.fail('local format must skip API')),'invalid');
  assert.equal(await validation.validateResetLink(token,undefined,async()=>assert.fail('no configured API')),'unavailable');
});
test('friendly password guidance retains UTF-8 byte limit and does not claim unknown server cause',async()=>{
  const at72='가'.repeat(22)+'Ab1234';
  assert.deepEqual(form.validateResetPassword({token,newPassword:at72,newPasswordConfirm:at72}),{});
  const at73=at72+' ';
  assert.equal(form.validateResetPassword({token,newPassword:at73,newPasswordConfirm:at73}).newPassword,'비밀번호가 너무 깁니다. 글자 수를 줄여 주세요.');
  assert.equal(form.PASSWORD_RULE,'8자 이상 · 영문과 숫자 포함');
  for(const p of ['', 'abc1', 'abcdefgh']) assert(!form.validateResetPassword({token,newPassword:p,newPasswordConfirm:p}).newPassword.includes('바이트'));
  await assert.rejects(api.completePasswordReset(values,csrf,{base,fetcher:async()=>json({code:'PASSWORD_RESET_PASSWORD_INVALID'},400)}),e=>!e.state.fields.newPassword.includes('깁니다')&&!e.state.fields.newPassword.includes('바이트'));
});
