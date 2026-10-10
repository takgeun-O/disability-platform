'use client';

import { useEffect, useRef, useState } from 'react';

import Link from '@/components/AppLink';

import {
  EmailVerificationApiError,
  verifyEmail,
} from '@/lib/email-verification-api';

import {
  getCsrfToken,
  SignupApiError,
} from '@/lib/signup-api';

type EmailVerificationActionProps = {
  token: string;
};

type VerificationState =
  | { kind: 'idle' }
  | { kind: 'submitting' }
  | { kind: 'success' }
  | {
      kind: 'error';
      message: string;
      canRetry: boolean;
      canResend?: boolean;
    };

export default function EmailVerificationAction({
  token,    // token: 부모 화면에서 전달 받는 값 <EmailVerificationAction token={link.token} />
  // 부모가 읽은 link.token을 자식 컴포넌트의 token이라는 입력값으로 전달하는 것.
  // React에서는 이런 입력값을 props라고 부른다.
}: EmailVerificationActionProps) {
    // useState : 화면에 반영할 상태를 보관
    // state : 현재 인증 화면 상태
    // setState : 상태를 바꾸고 화면을 다시 그리도록 요청
  const [state, setState] = useState<VerificationState>({
    kind: 'idle',
  });

  // 화면이 다시 그려지기 전에도 중복 실행을 막는 용도입니다.
  // useRef : 화면 변경 없이 값을 기억. 화면에 표시할 값은 useState(), 중복 실행 방지처럼 내부에서 기억할 값은 useRef()로 관리
  const inFlightRef = useRef(false);

  // 요청을 기다리는 동안 화면을 떠났는지 확인합니다.
  const mountedRef = useRef(false);

  // 성공·오류 안내로 키보드 초점을 이동할 때 사용합니다.
  const feedbackRef = useRef<HTMLParagraphElement>(null);

  useEffect(() => {
    mountedRef.current = true;

    return () => {
      mountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    if (
      state.kind === 'success'
      || state.kind === 'error'
    ) {
      feedbackRef.current?.focus();
    }
  }, [state.kind]);

  async function handleVerify() {
    if (
      !mountedRef.current
      || inFlightRef.current
      || state.kind === 'success'
      || (
        state.kind === 'error'
        && !state.canRetry
      )
    ) {
      return;
    }

    inFlightRef.current = true;

    setState({
      kind: 'submitting',
    });

    try {
      // 버튼을 누를 때마다 현재 세션의 CSRF 토큰을 조회합니다.
      // 실제로 HTTP 요청이 시작되는 곳이다.
      // 1. getCsrfToken()의 GET 요청이 끝날 때까지 기다림
      // 2. 반환받은 CSRF 토큰으로 인증 POST 요청
      // 3. verifyEamil()이 성공적으로 반환되면 화면을 success로 변경
      const csrfToken = await getCsrfToken();

      // CSRF 조회 중 화면을 떠났다면 인증 POST를 시작하지 않습니다.
      if (!mountedRef.current) {
        return;
      }

      await verifyEmail(
        { token },
        csrfToken,
      );

      if (!mountedRef.current) {
        return;
      }

      // verifyEmail은 200 / ACTIVE를 확인한 경우에만 정상 반환합니다.
      setState({
        kind: 'success',
      });
    } catch (error) {
      if (!mountedRef.current) {
        return;
      }

      if (error instanceof EmailVerificationApiError) {
        const cannotRetry = [
          'EMAIL_VERIFICATION_INVALID',
          'VALIDATION_FAILED',
          'INVALID_REQUEST_BODY',
        ].includes(error.code);

        setState({
          kind: 'error',
          message: error.message,
          canRetry: !cannotRetry,
          canResend: error.code === 'EMAIL_VERIFICATION_INVALID',
        });
      } else if (error instanceof SignupApiError) {
        // 기존 getCsrfToken()에서 발생하는 오류입니다.
        // 이 경우에는 이메일 인증 POST를 아직 보내지 않았습니다.
        setState({
          kind: 'error',
          message:
            '보안 확인 정보를 가져오지 못해 인증 요청을 보내지 못했습니다. 잠시 후 다시 시도해 주세요.',
          canRetry: true,
        });
      } else {
        setState({
          kind: 'error',
          message:
            '인증 결과를 확인하지 못했습니다. 잠시 후 다시 확인해 주세요.',
          canRetry: true,
        });
      }
    } finally {
      inFlightRef.current = false;
    }
  }

  const isSubmitting = state.kind === 'submitting';

  const showButton =
    state.kind !== 'success'
    && (
      state.kind !== 'error'
      || state.canRetry
    );

  return (
    <div style={{ marginBottom: 32 }}>
      {isSubmitting && (
        <p
          role="status"
          aria-live="polite"
          style={{
            fontSize: 13,
            color: '#4A4845',
            marginBottom: 12,
          }}
        >
          이메일 인증을 처리하고 있습니다.
        </p>
      )}

      {state.kind === 'error' && (
        <p
          id="email-verification-feedback"
          ref={feedbackRef}
          tabIndex={-1}
          role="alert"
          style={{
            fontSize: 13,
            color: '#B91C1C',
            lineHeight: 1.7,
            marginBottom: 16,
          }}
        >
          {state.message}
        </p>
      )}

      {state.kind === 'success' && (
        <div>
          <p
            ref={feedbackRef}
            tabIndex={-1}
            role="status"
            aria-live="polite"
            style={{
              fontSize: 14,
              color: '#4A4845',
              lineHeight: 1.7,
              marginBottom: 16,
            }}
          >
            이메일 인증이 완료되었습니다.
            <br />
            계정이 활성화되었습니다. 직접 로그인해 주세요.
          </p>

          <Link
            href="/login"
            style={{
              fontSize: 14,
              color: '#1A1918',
              textDecoration: 'underline',
            }}
          >
            로그인 화면으로 이동
          </Link>
        </div>
      )}

      {state.kind === 'error' && state.canResend && (
        <p style={{ marginBottom: 16 }}>
          <Link href="/register/resend" style={{ fontSize: 14, color: '#4A4845', textDecoration: 'underline' }}>
            인증 메일 다시 요청하기
          </Link>
        </p>
      )}

      {showButton && (
        <button
          type="button"
          onClick={handleVerify}
          disabled={isSubmitting}
          aria-describedby={
            state.kind === 'error'
              ? 'email-verification-feedback'
              : undefined
          }
          style={{
            width: '100%',
            padding: '12px 16px',
            border: 'none',
            borderRadius: 2,
            backgroundColor: '#1A1918',
            color: '#FFFFFF',
            fontSize: 14,
            fontWeight: 600,
            fontFamily: 'inherit',
            cursor: isSubmitting ? 'wait' : 'pointer',
            opacity: isSubmitting ? 0.6 : 1,
          }}
        >
          {isSubmitting
            ? '인증 중…'
            : state.kind === 'error'
              ? '다시 인증하기'
              : '이메일 인증하기'}
        </button>
      )}
    </div>
  );
}