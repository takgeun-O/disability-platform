"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import Link from "@/components/AppLink";
import { getCsrfToken, SignupApiError } from "@/lib/signup-api";
import { validateEmail } from "@/lib/signup-form";
import {
  EmailVerificationResendApiError,
  resendEmailVerification,
} from "@/lib/email-verification-resend-api";

type ResendState =
  | { kind: "idle" | "submitting" | "accepted" }
  | { kind: "error"; message: string; emailInvalid: boolean };

const acceptedMessage =
  "입력한 이메일이 인증 대기 상태이고 재전송 조건을 충족하면 인증 메일이 발송됩니다. 메일함과 스팸함을 확인해 주세요.";

export default function EmailVerificationResend() {
  const [email, setEmail] = useState("");
  const [state, setState] = useState<ResendState>({ kind: "idle" });
  const inFlightRef = useRef(false);
  const mountedRef = useRef(false);
  const emailRef = useRef<HTMLInputElement>(null);
  const feedbackRef = useRef<HTMLParagraphElement>(null);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    if (state.kind === "error" && state.emailInvalid) emailRef.current?.focus();
    else if (state.kind === "error" || state.kind === "accepted")
      feedbackRef.current?.focus();
  }, [state]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!mountedRef.current || inFlightRef.current) return;
    const validationError = validateEmail(email);
    if (validationError) {
      setState({ kind: "error", message: validationError, emailInvalid: true });
      return;
    }
    // state가 다시 렌더링되기 전의 연속 제출도 즉시 차단한다.
    inFlightRef.current = true;
    setState({ kind: "submitting" });
    try {
      // 수동 재시도마다 동일 세션의 새 CSRF 값을 조회한다.
      const csrfToken = await getCsrfToken();
      if (!mountedRef.current) return;
      await resendEmailVerification({ email }, csrfToken);
      if (mountedRef.current) setState({ kind: "accepted" });
    } catch (error) {
      if (!mountedRef.current) return;
      if (error instanceof EmailVerificationResendApiError) {
        setState({
          kind: "error",
          message: error.message,
          emailInvalid: ["VALIDATION_FAILED", "INVALID_REQUEST_BODY"].includes(
            error.code,
          ),
        });
      } else if (error instanceof SignupApiError) {
        setState({
          kind: "error",
          emailInvalid: false,
          message:
            error.code === "NETWORK_ERROR"
              ? "서버 연결 문제로 보안 확인 정보를 가져오지 못했습니다. 재전송 요청은 보내지 않았습니다. 연결을 확인하고 다시 시도해 주세요."
              : "보안 확인 정보를 가져오지 못해 재전송 요청을 보내지 않았습니다. 잠시 후 다시 시도해 주세요.",
        });
      } else {
        setState({
          kind: "error",
          emailInvalid: false,
          message:
            "처리 결과를 확인하지 못했습니다. 메일함을 확인하고 잠시 후 다시 시도해 주세요.",
        });
      }
    } finally {
      inFlightRef.current = false;
    }
  }

  const isSubmitting = state.kind === "submitting";
  return (
    <main>
      <div
        style={{ maxWidth: 500, margin: "0 auto", padding: "60px 24px 80px" }}
      >
        <h1
          style={{
            fontSize: 22,
            fontWeight: 700,
            color: "#1A1918",
            marginBottom: 16,
          }}
        >
          인증 메일 재전송
        </h1>
        <p
          id="resend-help"
          style={{
            fontSize: 13,
            color: "#4A4845",
            lineHeight: 1.7,
            marginBottom: 24,
          }}
        >
          메일을 받지 못했거나 인증 링크를 사용할 수 없다면 가입한 이메일을
          입력해 주세요. 새 메일이 발급되면 이전 인증 링크는 사용할 수 없습니다.
        </p>
        <form onSubmit={handleSubmit} noValidate aria-busy={isSubmitting}>
          <label
            htmlFor="resend-email"
            style={{ display: "block", fontSize: 14, marginBottom: 8 }}
          >
            이메일
          </label>
          <input
            id="resend-email"
            ref={emailRef}
            type="email"
            name="email"
            autoComplete="email"
            autoCapitalize="none"
            spellCheck={false}
            required
            value={email}
            disabled={isSubmitting}
            onChange={(event) => {
              setEmail(event.target.value);
              setState({ kind: "idle" });
            }}
            aria-invalid={state.kind === "error" && state.emailInvalid}
            aria-describedby={
              state.kind === "error"
                ? "resend-help resend-feedback"
                : "resend-help"
            }
            style={{
              width: "100%",
              boxSizing: "border-box",
              padding: "12px 14px",
              fontSize: 14,
              border: "1px solid #C8C5C0",
              borderRadius: 2,
              marginBottom: 16,
              fontFamily: "inherit",
            }}
          />
          {state.kind === "error" && (
            <p
              id="resend-feedback"
              ref={feedbackRef}
              tabIndex={-1}
              role="alert"
              style={{
                color: "#B91C1C",
                fontSize: 13,
                lineHeight: 1.7,
                marginBottom: 16,
              }}
            >
              {state.message}
            </p>
          )}
          {state.kind === "accepted" && (
            <p
              id="resend-feedback"
              ref={feedbackRef}
              tabIndex={-1}
              role="status"
              aria-live="polite"
              style={{
                color: "#4A4845",
                fontSize: 14,
                lineHeight: 1.7,
                marginBottom: 16,
              }}
            >
              {acceptedMessage}
            </p>
          )}
          {isSubmitting && (
            <p
              role="status"
              aria-live="polite"
              style={{ fontSize: 13, marginBottom: 12 }}
            >
              재전송 요청을 처리하고 있습니다.
            </p>
          )}
          <button
            type="submit"
            disabled={isSubmitting}
            style={{
              width: "100%",
              padding: "12px 16px",
              border: "none",
              borderRadius: 2,
              backgroundColor: "#1A1918",
              color: "#FFFFFF",
              fontSize: 14,
              fontWeight: 600,
              fontFamily: "inherit",
              cursor: isSubmitting ? "wait" : "pointer",
              opacity: isSubmitting ? 0.6 : 1,
            }}
          >
            {isSubmitting ? "요청 중…" : "인증 메일 재전송 요청"}
          </button>
        </form>
        <div
          style={{
            display: "flex",
            gap: 24,
            marginTop: 24,
            fontSize: 13,
            textDecoration: "underline",
          }}
        >
          <Link href="/register/verify">인증 안내로 돌아가기</Link>
          <Link href="/login">로그인 화면으로 이동</Link>
        </div>
      </div>
    </main>
  );
}
