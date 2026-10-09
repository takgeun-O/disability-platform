import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import ts from "typescript";

const source = fs.readFileSync(
  new URL("../src/lib/email-verification-link.ts", import.meta.url),
  "utf8",
);

const { outputText } = ts.transpileModule(source, {
  compilerOptions: {
    target: ts.ScriptTarget.ES2022,
    module: ts.ModuleKind.ESNext,
  },
});

const encoded = Buffer.from(outputText).toString("base64");

const { readEmailVerificationLink } = await import(
  `data:text/javascript;base64,${encoded}`
);

// 실제 발급 토큰이 아닌 형식 검사 전용 문자열입니다.
const exampleToken = "AbCd_-0123".repeat(4) + "XYZ";

function readLink(query) {
  const params = new URLSearchParams(query);

  return readEmailVerificationLink(params.getAll("token"));
}

test("token 파라미터가 없으면 missing을 반환한다", () => {
  assert.deepEqual(readLink(""), { kind: "missing" });

  assert.deepEqual(readLink("from=email"), { kind: "missing" });
});

test("형식이 맞으면 대소문자를 유지한 원본 토큰을 반환한다", () => {
  const query = new URLSearchParams({
    token: exampleToken,
  }).toString();

  assert.deepEqual(readLink(query), {
    kind: "ready",
    token: exampleToken,
  });
});

test("값 없이 token 이름만 있으면 invalid를 반환한다", () => {
  assert.deepEqual(readLink("token"), { kind: "invalid" });
});

for (const [description, token] of [
  ["빈 값", ""],
  ["42글자", exampleToken.slice(1)],
  ["44글자", exampleToken + "A"],
  ["앞뒤 공백", ` ${exampleToken} `],
  ["허용하지 않는 문자", "A".repeat(42) + "+"],
  ["끝의 줄바꿈", exampleToken + "\n"],
]) {
  test(`${description}은 invalid를 반환한다`, () => {
    const query = new URLSearchParams({
      token,
    }).toString();

    assert.deepEqual(readLink(query), { kind: "invalid" });
  });
}

test("서로 다른 token이 두 개면 invalid를 반환한다", () => {
  const params = new URLSearchParams();

  params.append("token", exampleToken);
  params.append("token", "B".repeat(43));

  assert.deepEqual(readLink(params.toString()), { kind: "invalid" });
});

test("같은 token이 두 번 전달되어도 invalid를 반환한다", () => {
  const params = new URLSearchParams();

  params.append("token", exampleToken);
  params.append("token", exampleToken);

  assert.deepEqual(readLink(params.toString()), { kind: "invalid" });
});
