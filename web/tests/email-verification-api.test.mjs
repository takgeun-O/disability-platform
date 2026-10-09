import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import ts from "typescript";

async function loadTypeScript(path) {
  const source = fs.readFileSync(new URL(path, import.meta.url), "utf8");

  const { outputText } = ts.transpileModule(source, {
    compilerOptions: {
      target: ts.ScriptTarget.ES2022,
      module: ts.ModuleKind.ESNext,
    },
  });

  const encoded = Buffer.from(outputText).toString("base64");

  return import(`data:text/javascript;base64,${encoded}`);
}

const api = await loadTypeScript("../src/lib/email-verification-api.ts");

const signupApi = await loadTypeScript("../src/lib/signup-api.ts");

const baseUrl = "http://127.0.0.1:8082";

// 실제 발급 토큰이 아닌 테스트용 문자열입니다.
const emailToken = "AbCd_-0123".repeat(4) + "XYZ";
const request = { token: emailToken };

process.env.NEXT_PUBLIC_API_BASE_URL = baseUrl;

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
    },
  });
}

test("CSRF 조회 후 원본 이메일 토큰을 인증 API에 전달한다", async (t) => {
  const calls = [];

  t.mock.method(globalThis, "fetch", async (url, options) => {
    calls.push({ url, options });

    if (calls.length === 1) {
      return json({
        headerName: "X-CSRF-TOKEN",
        token: "test-csrf-token",
      });
    }

    return json({ status: "ACTIVE" });
  });

  const csrfToken = await signupApi.getCsrfToken();
  const result = await api.verifyEmail(request, csrfToken);

  assert.deepEqual(result, { status: "ACTIVE" });
  assert.equal(calls.length, 2);

  assert.equal(calls[0].url, baseUrl + "/api/v1/auth/csrf");
  assert.equal(calls[0].options.method, "GET");
  assert.equal(calls[0].options.credentials, "include");

  assert.equal(calls[1].url, baseUrl + "/api/v1/auth/email/verify");
  assert.equal(calls[1].options.method, "POST");
  assert.equal(calls[1].options.credentials, "include");

  assert.equal(calls[1].options.headers["Content-Type"], "application/json");
  assert.equal(calls[1].options.headers["X-CSRF-TOKEN"], "test-csrf-token");

  assert.deepEqual(JSON.parse(calls[1].options.body), { token: emailToken });
});

for (const [status, code] of [
  [400, "EMAIL_VERIFICATION_INVALID"],
  [400, "VALIDATION_FAILED"],
  [403, "CSRF_TOKEN_INVALID"],
]) {
  test(`${code}를 구분하고 POST를 자동 반복하지 않는다`, async (t) => {
    const fetchMock = t.mock.method(globalThis, "fetch", async () =>
      json(
        {
          code,
          message: "서버 오류 안내",
          fieldErrors: [],
        },
        status,
      ),
    );

    await assert.rejects(
      api.verifyEmail(request, "test-csrf-token"),
      (error) => {
        assert.ok(error instanceof api.EmailVerificationApiError);
        assert.equal(error.status, status);
        assert.equal(error.code, code);

        return true;
      },
    );

    assert.equal(fetchMock.mock.callCount(), 1);
  });
}

for (const [status, body, expectedCode] of [
  [500, "<html>internal details</html>", "SERVER_ERROR"],
  [200, "<html>unexpected page</html>", "UNEXPECTED_RESPONSE"],
  [200, '{"status":"PENDING"}', "UNEXPECTED_RESPONSE"],
  [201, '{"status":"ACTIVE"}', "UNEXPECTED_RESPONSE"],
  [200, "{broken", "UNEXPECTED_RESPONSE"],
]) {
  test(`비정상 응답 ${status}: ${body}를 성공으로 처리하지 않는다`, async (t) => {
    t.mock.method(
      globalThis,
      "fetch",
      async () => new Response(body, { status }),
    );

    await assert.rejects(
      api.verifyEmail(request, "test-csrf-token"),
      (error) => {
        assert.ok(error instanceof api.EmailVerificationApiError);
        assert.equal(error.code, expectedCode);

        assert.doesNotMatch(error.message, /<html>|internal details/);

        return true;
      },
    );
  });
}

test("네트워크 오류 시 결과 불확실성을 안내하고 재전송하지 않는다", async (t) => {
  const fetchMock = t.mock.method(globalThis, "fetch", async () => {
    throw new TypeError("Failed to fetch");
  });

  await assert.rejects(api.verifyEmail(request, "test-csrf-token"), (error) => {
    assert.equal(error.code, "NETWORK_ERROR");
    assert.match(error.message, /결과를 확인할 수 없습니다/);

    return true;
  });

  assert.equal(fetchMock.mock.callCount(), 1);
});

test("API 주소가 없으면 요청 전에 실패한다", async (t) => {
  const previousValue = process.env.NEXT_PUBLIC_API_BASE_URL;

  delete process.env.NEXT_PUBLIC_API_BASE_URL;

  const fetchMock = t.mock.method(globalThis, "fetch", async () => {
    throw new Error("요청하면 안 됩니다.");
  });

  try {
    await assert.rejects(
      api.verifyEmail(request, "test-csrf-token"),
      (error) => error.code === "API_NOT_CONFIGURED",
    );

    assert.equal(fetchMock.mock.callCount(), 0);
  } finally {
    if (previousValue === undefined) {
      delete process.env.NEXT_PUBLIC_API_BASE_URL;
    } else {
      process.env.NEXT_PUBLIC_API_BASE_URL = previousValue;
    }
  }
});
