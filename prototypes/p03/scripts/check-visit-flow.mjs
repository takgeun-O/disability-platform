// Regression checks for early completion, personalized questions and uncertain coverage.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import Module, { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';

const root = fileURLToPath(new URL('../src/app/validation/hearing-aid-health-insurance', import.meta.url));
const cache = new Map();
function load(file) {
  if (cache.has(file)) return cache.get(file).exports;
  const mod = new Module(file);
  mod.filename = file;
  const nativeRequire = createRequire(file);
  mod.require = specifier => specifier.startsWith('.') && !path.extname(specifier)
    ? load(path.resolve(path.dirname(file), `${specifier}.ts`))
    : nativeRequire(specifier);
  cache.set(file, mod);
  mod._compile(ts.transpileModule(fs.readFileSync(file, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, esModuleInterop: true },
  }).outputText, file);
  return mod.exports;
}
const { getGuidance, getOverviewState, getJourneyStates, replaceAnswer, answersForPreviousQuestion, overviewStages, questions } = load(path.join(root, 'guided-content.ts'));
const { getQuestionPlan } = load(path.join(root, 'consultation-content.ts'));
const { makeQuestionNote } = load(path.join(root, 'question-note.ts'));
const { registryProducts } = load(path.join(root, 'registry-content.ts'));
const { benefitBasis, benefitExamples, initialBasis, productBasis, formatBenefitWon } = load(path.join(root, 'benefit-content.ts'));
const { caregiverQuestions, caregiverQuestionNote } = load(path.join(root, 'bilateral-content.ts'));

// Published assumptions, not personalized entitlement: keep both ears tied to
// the one-ear figures, including delayed management rather than a lump sum.
assert.equal(productBasis, 910_000);
assert.equal(initialBasis, 1_110_000);
assert.equal(benefitBasis.followupCount, 4);
assert.equal(initialBasis + benefitBasis.annualFollowup * benefitBasis.followupCount, 1_310_000);
assert.deepEqual(benefitExamples.map(e => e.one), [
  { initial: 999_000, annual: 45_000, followup: 180_000, total: 1_179_000 },
  { initial: 1_110_000, annual: 50_000, followup: 200_000, total: 1_310_000 },
]);
assert.deepEqual(benefitExamples.map(e => e.two), [
  { initial: 1_998_000, annual: 90_000, followup: 360_000, total: 2_358_000 },
  { initial: 2_220_000, annual: 100_000, followup: 400_000, total: 2_620_000 },
]);
for (const example of benefitExamples) {
  for (const key of ['initial', 'annual', 'followup', 'total']) assert.equal(example.two[key], example.one[key] * 2);
  assert.equal(example.two.total, example.two.initial + example.two.followup);
  assert.equal(example.two.followup, example.two.annual * benefitBasis.followupCount);
}
assert.equal(formatBenefitWon(999_000), '99만 9천 원');
assert.equal(formatBenefitWon(2_358_000), '235만 8천 원');
assert.equal(formatBenefitWon(2_620_000), '262만 원');
assert.equal(caregiverQuestions.length, 3);
for (const q of caregiverQuestions) assert.ok(caregiverQuestionNote.includes(`${q.institution}\n${q.question}`), 'caregiver copy retains the displayed institution and question');
assert.ok(caregiverQuestionNote.includes('확정한 결과가 아니에요'), 'common caregiver questions do not declare individual eligibility');
console.log('PASS: one/two-ear maximum examples, initial/followup totals and caregiver question copy');

const endpoints = [];
const pending = [[]];
while (pending.length) {
  const answers = pending.pop();
  const guide = getGuidance(answers);
  if (guide) { endpoints.push(answers); continue; }
  assert.ok(answers.length < questions.length, 'all final answers produce a result');
  for (const choice of questions[answers.length].choices) pending.push(replaceAnswer(answers, answers.length, choice.value));
}
assert.equal(endpoints.length, 15, '4 early registration + 1 medical aid + 5 health + 5 unknown coverage');
for (const answers of endpoints) {
  const guide = getGuidance(answers);
  const plan = getQuestionPlan(answers);
  assert.equal(plan.primary.length, 3);
  assert.ok(plan.primary.every(q => q.target === 'center' && q.reason));
  assert.equal(new Set(plan.primary.map(q => q.id)).size, 3);
  assert.ok(guide.summary && guide.action.institution && guide.action.instruction && guide.action.ask && guide.center);
  assert.equal(guide.showHealthDetails, answers[1] === 'health' && answers[2] === 'yes');
  const states = getJourneyStates(answers);
  assert.equal(states.length, 5);
  assert.equal(states[0].kind === 'done', answers[0] === 'registered', 'only a supplied registration answer marks completion');
  assert.equal(states[1].kind === 'done', answers[2] === 'yes', 'unknown/unasked prescriptions cannot be complete');
  assert.ok(states.slice(2).every(s => s.kind !== 'done'), 'purchase and later steps were not asked');
  const note = makeQuestionNote(guide, plan.primary);
  assert.ok(note.includes(guide.summary));
  assert.ok(note.includes(guide.action.ask), 'copy uses the displayed inquiry, including any conditions');
  for (const fact of guide.facts) assert.ok(note.includes(fact.text), 'copy retains supplied and unconfirmed facts');
  for (const q of plan.primary) assert.ok(note.includes(q.question) && note.includes(q.reason));
  const overview = getOverviewState(answers.length - 1, guide);
  assert.equal(overview.phase, guide.phase);
  assert.equal(overview.text, guide.position);
}
assert.equal(overviewStages.length, 5);
assert.equal(getOverviewState(1, null).phase, null, 'insurance is a separate topic, not unfinished registration');
assert.equal(getOverviewState(3, null).phase, null, 'prior history is not purchase authorization');
assert.equal(getOverviewState(0, null).phase, 0);
assert.equal(getOverviewState(2, null).phase, 1);
for (const stage of ['planning', 'assessment', 'waiting', 'unknown']) {
  const answers = [stage];
  assert.equal(getGuidance(answers).phase, 0);
  assert.equal(getQuestionPlan(answers).primary[0].id, `VISIT-REG-${stage}`);
  assert.equal(getGuidance(answers).showHealthDetails, false);
}
assert.notEqual(getGuidance(['assessment']).summary, getGuidance(['waiting']).summary);
assert.equal(getJourneyStates(['unknown'])[0].kind, 'check');
for (const answers of [[], ['registered'], ['registered', 'health'], ['registered', 'unknown'], ['registered', 'health', 'yes'], ['registered', 'unknown', 'yes']]) {
  assert.equal(getGuidance(answers), null, `continue incomplete answers: ${answers}`);
}
const aid = getGuidance(['registered', 'medical-aid']);
assert.equal(aid.phase, null);
assert.equal(aid.showHealthDetails, false);
for (const answers of endpoints.filter(a => a[1] === 'unknown')) {
  const guide = getGuidance(answers);
  assert.equal(guide.phase, null);
  assert.ok(guide.pending.includes('보험 자격'));
  assert.equal(guide.showHealthDetails, false);
  assert.equal(getJourneyStates(answers)[2].kind, 'check');
  assert.equal(getQuestionPlan(answers).insuranceUnclear, true);
  if (answers[2] === 'unknown') assert.ok(guide.pending.includes('처방전 여부'));
  if (answers[3] && answers[3] !== 'first') assert.ok(guide.pending.includes('이전 지원 이력'));
  const question = getQuestionPlan(answers).primary[0].question;
  assert.doesNotMatch(question, /[.!?][가-힣]/u, 'composed sentences must have a separator');
  assert.doesNotMatch(question, / {2,}/u, 'optional context must not add extra spaces');
  const sentences = question.split('. ');
  assert.equal(sentences.length, 3, `coverage, prescription, and consultation remain separate: ${answers}`);
  assert.ok(makeQuestionNote(guide, getQuestionPlan(answers).primary).includes(sentences.join('. ')));
}
const medicalAidQuestion = getQuestionPlan(['registered', 'medical-aid']).primary[0].question;
assert.doesNotMatch(medicalAidQuestion, / {2,}|[.!?][가-힣]/u, 'missing prescription context does not affect separators');
for (const history of ['previous', 'unknown']) {
  const answers = ['registered', 'health', 'yes', history];
  assert.equal(getGuidance(answers).phase, null);
  assert.equal(getJourneyStates(answers)[2].kind, 'check');
}
const ready = ['registered', 'health', 'yes', 'first'];
assert.equal(getGuidance(ready).phase, 2);
assert.equal(getQuestionPlan(ready).primary[0].id, 'VISIT-MODEL');
const verified = { product: { status: 'verified', product: registryProducts[0] } };
assert.equal(getQuestionPlan(ready, verified).primary[0].id, 'VISIT-SELECTED-MODEL');
assert.ok(getQuestionPlan(ready, verified).primary[0].question.includes(registryProducts[0].model));
assert.equal(getQuestionPlan(ready, { product: { ...verified.product, status: 'stale' } }).primary[0].id, 'VISIT-MODEL');
assert.equal(getQuestionPlan(['waiting'], verified).primary[0].id, 'VISIT-REG-waiting');
assert.equal(getQuestionPlan(['registered', 'unknown', 'yes', 'first'], verified).primary[0].id, 'VISIT-COVERAGE-unknown');
// The same helpers are called by the UI when editing earlier answers.
assert.deepEqual(replaceAnswer(ready, 0, 'assessment'), ['assessment']);
assert.deepEqual(replaceAnswer(ready, 1, 'unknown'), ['registered', 'unknown']);
assert.equal(getGuidance(replaceAnswer(ready, 1, 'unknown')), null);
assert.deepEqual(replaceAnswer(ready, 2, 'unknown'), ['registered', 'health', 'unknown']);
assert.equal(getGuidance(replaceAnswer(ready, 2, 'unknown')).showHealthDetails, false);
assert.deepEqual(answersForPreviousQuestion(ready, 3), ['registered', 'health', 'yes']);
assert.deepEqual(answersForPreviousQuestion(ready, 1), ['registered']);
assert.deepEqual(ready, ['registered', 'health', 'yes', 'first'], 'editing is immutable');

// History knowledge is not evidence of current device ownership. Unknown history
// must first establish whether a support record exists, on screen and in copies.
const priorAnswers = replaceAnswer(ready, 3, 'previous');
const unknownAnswers = replaceAnswer(priorAnswers, 3, 'unknown');
const prior = getGuidance(priorAnswers);
const unknown = getGuidance(unknownAnswers);
const priorPlan = getQuestionPlan(priorAnswers);
const unknownPlan = getQuestionPlan(unknownAnswers);
assert.notEqual(unknown.action.instruction, prior.action.instruction);
assert.notEqual(unknown.action.ask, prior.action.ask);
assert.notEqual(unknownPlan.primary[0].question, priorPlan.primary[0].question);
assert.match(unknown.action.ask, /기록이 있는지/u);
assert.match(unknown.action.ask, /기록이 있다면/u);
const unknownText = [unknown.center, unknown.action.instruction, makeQuestionNote(unknown, unknownPlan.primary)].join('\n');
assert.doesNotMatch(unknownText, /교체할 제품|지금 쓰는 보청기|다시 지원받으려면|이전에 지원받은 적이 있어요/u);
for (const text of [prior.center, priorPlan.primary[0].question, priorPlan.primary[0].reason]) {
  assert.match(text, /사용 중인 보청기가 있다면/u, 'even a known prior benefit does not imply device ownership');
}
for (const answers of [priorAnswers, unknownAnswers, replaceAnswer(unknownAnswers, 3, 'first')]) {
  const guide = getGuidance(answers);
  const plan = getQuestionPlan(answers);
  const note = makeQuestionNote(guide, plan.primary);
  assert.ok(note.includes(guide.action.ask) && note.includes(plan.primary[0].question));
  for (const other of [prior, unknown].filter(other => other.action.ask !== guide.action.ask)) {
    assert.ok(!note.includes(other.action.ask), 'editing replaces the previous branch inquiry in the copy');
  }
}
console.log(`PASS: ${endpoints.length} results, unknown coverage, conditional history/device wording, sentence separators, editing, center questions, product context and copied notes`);
