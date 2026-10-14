import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';
const load = file => { const exports = {}; const code = ts.transpileModule(fs.readFileSync(new URL(file, import.meta.url), 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText; vm.runInNewContext(code, { exports }); return exports; };
const { HEARING_AID_ACTION_GUIDANCE: guidance } = load('../src/data/hearingAidActionGuidance.ts');
const { buildInquiryMemo } = load('../src/lib/hearing-aid-notes.ts');
test('문의 메모는 질문·준비 체크와 미확인 항목을 구분하며 자격 완료로 표현하지 않는다', () => {
  const info = { currentState: '검사 중', institution: '진료받는 병원', contactQuestion: '남은 검사와 자료 제출 시점은?', nextAction: '진단 자료 확인', secondary: '추가 확인 조건' };
  const checked = new Set(['ask-0', 'tasks-1']);
  const text = buildInquiryMemo(info, guidance.R02, checked);
  for (const content of ['01. 먼저 확인할 곳\n진료받는 병원', '02. 이곳에서 물어볼 질문\n남은 검사', '[x] 남은 검사와 진단 자료를 받는 날짜', '[ ] 주민센터에 제출할 시점', '[x] 등록 신청용 검사·진단 자료 받기', '추가 확인 조건', '04. 이번 단계 다음에는', '자격이나 절차 완료를 판정하지 않습니다.']) assert(text.includes(content), content);
  assert.equal(checked.size, 2);
  assert(buildInquiryMemo(info, guidance.R02, new Set()).includes('[ ] 남은 검사'));
});
test('도달 가능한 결과 모두 체크 안내를 갖고 반복 상세 경로를 포함하지 않는다', () => {
  const codes = Array.from({length:21}, (_,i) => 'R'+String(i+1).padStart(2,'0')).filter(code => code !== 'R05');
  assert.equal(Object.keys(guidance).length, codes.length);
  for (const code of codes) { assert(guidance[code].checklist.ask.length >= 2); assert(guidance[code].checklist.tasks.length >= 2); assert(!('detail' in guidance[code].checklist)); }
});
