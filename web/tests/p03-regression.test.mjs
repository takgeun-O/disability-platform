import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';
import path from 'node:path';
const root = path.resolve(import.meta.dirname, '..');
import ts from 'typescript';
const text = fs.readFileSync(root + '/src/features/HearingAidGuide.tsx', 'utf8');
const source = ts.createSourceFile('HearingAidGuide.tsx', text, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
import test from 'node:test';
const names = ['RESULT_INFO','BASE','getCurrentStep','getLastAnsweredQ','computeResult','getResultInfo','computeFacts','buildCopyText','makeStep','stepInstitution','stepDetails','computeJourney','computeTopBarSteps','getAnswerJourneyStep'];
const declarations = source.statements.filter(n => names.includes(n.name?.text) || ts.isVariableStatement(n) && n.declarationList.declarations.some(d=>names.includes(d.name.text)));
assert.equal(declarations.length,names.length);
const js = ts.transpileModule(declarations.map(n=>n.getText(source)).join('\n')+'\n;({'+names.join(',')+'});', {compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText;
const api = vm.runInNewContext(js,{});
test('P03 existing 24 result combinations and copy parity', () => {
let scenarios = 0;
const histories = ['first','previous','unknown'];
const applications = {approved:'R18',waiting:'R19','not-applied':'R20',unknown:'R21'};
assert.equal(api.getCurrentStep('registered','medical-aid','yes',null,null),'Q4');
assert.equal(api.computeResult('registered','medical-aid','yes',null,'approved'),null);
assert.equal(api.getCurrentStep('registered','medical-aid','yes','unknown',null),'Q5');
assert.equal(api.computeResult('registered','medical-aid','yes','unknown',null),null);
for (const history of histories) for(const [application,expected] of Object.entries(applications)) {
 const args=['registered','medical-aid','yes',history,application];
 assert.equal(api.getCurrentStep(...args),'done');
 assert.equal(api.computeResult(...args),expected);
 assert.equal(api.getLastAnsweredQ(...args),'Q5');
 assert.equal(api.getAnswerJourneyStep(...args),2);
 const info=api.getResultInfo(expected,'medical-aid',history,application);
 assert.equal(info.title,api.RESULT_INFO[expected].title);
 assert.equal(info.institution,api.RESULT_INFO[expected].institution);
 assert.equal(info.nextAction,api.RESULT_INFO[expected].nextAction);
 assert.equal(info.hasHealthDetail,false);
 assert.equal(info.questions.length,3);
 const facts=api.computeFacts(...args);
 assert.equal(facts.length,5);
 assert.equal(facts[3].status,history==='first'?'done':history==='previous'?'check-needed':'unknown');
 const copy=api.buildCopyText(info,facts);
 for(const fact of facts) assert.ok(copy.includes(`${fact.label}: ${fact.value}`));
 assert.ok(copy.includes(info.contactQuestion));
 for(const question of info.questions) assert.ok(copy.includes(question.text));
 assert.ok(!/[.?][가-힣]/u.test(copy),'sentence separator');
 if(history==='unknown') {
  assert.ok(copy.includes('기억나지 않아요'));
  assert.ok(!copy.includes('교체할 제품'));
  assert.ok(!copy.includes('지금 쓰는 보청기'));
  if(application!=='approved') assert.ok(copy.includes('기록이 있다면'));
 } else if(history==='previous') assert.ok(copy.includes('받은 적이 있어요'));
 else assert.ok(copy.includes('처음 신청해요'));
 if(application==='approved') assert.ok(!info.nextAction.includes('다시 신청'));
 scenarios++;
}
for(const [insurance,codes] of [['health',['R08','R09','R10']],['unknown',['R13','R14','R15']]]) {
 histories.forEach((history,index)=>{
  const args=['registered',insurance,'yes',history,null];
  assert.equal(api.getCurrentStep(...args),'done');
  assert.equal(api.computeResult(...args),codes[index]);
  assert.equal(api.getLastAnsweredQ(...args),'Q4');
  assert.equal(api.getResultInfo(codes[index],insurance,history,null),api.RESULT_INFO[codes[index]]);
  assert.equal(api.computeFacts(...args).length,4);
  scenarios++;
 });
}
for(const [insurance,codes] of [['health',['R06','R07']],['unknown',['R11','R12']],['medical-aid',['R16','R17']]]) {
 ['no','unknown'].forEach((prescription,index)=>{
  const args=['registered',insurance,prescription,null,null];
  assert.equal(api.getCurrentStep(...args),'done');
  assert.equal(api.computeResult(...args),codes[index]);
  assert.equal(api.computeFacts(...args).length,3);
  scenarios++;
 });
}
console.log(`PASS: ${scenarios} result scenarios; Q4/Q5 guards; medical-aid history/application independence; copy text parity; unknown handling; existing early results.`);

});

test('Registration early results and unanswered guards', () => {
  for (const [answer, result] of Object.entries({planning:'R01',assessment:'R02',waiting:'R03',unknown:'R04'})) {
    assert.equal(api.computeResult(answer,null,null,null,null),result);
    assert.equal(api.getCurrentStep(answer,null,null,null,null),'done');
  }
  assert.equal(api.getCurrentStep(null,null,null,null,null),'Q1');
  assert.equal(api.getCurrentStep('registered',null,null,null,null),'Q2');
  assert.equal(api.getCurrentStep('registered','unknown',null,null,null),'Q3');
});
test('Unknown insurance never exposes health amount details; no ownership assumption', () => {
  for (const code of ['R11','R12','R13','R14','R15']) {
    const info = api.RESULT_INFO[code];
    assert.equal(info.hasHealthDetail,false);
    assert.equal(info.questions.length,3);
    const copy = api.buildCopyText(info,[]);
    assert.ok(!/[.?][가-힣]/u.test(copy));
  }
  for (const code of ['R09','R10']) {
    const copy=api.buildCopyText(api.RESULT_INFO[code],[]);
    assert.ok(!copy.includes('지금 쓰는 보청기'));
    if(code==='R09') assert.ok(copy.includes('사용 중인 보청기가 있다면'));
    else {assert.ok(copy.includes('기록이 있다면')); assert.ok(copy.includes('이전 지원 여부를 확인 중이에요'));}
  }
});
