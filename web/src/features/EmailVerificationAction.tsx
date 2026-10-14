'use client';

import { useEffect, useRef, useState } from 'react';

import { useRouter } from 'next/navigation';
import { useNavigationGuard, useNavigationRequest } from '@/components/NavigationGuard';
import { useVerificationNotice } from '@/components/VerificationNotice';
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
  | { kind: 'redirecting' }
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

  const router = useRouter();
  const guard = useNavigationGuard();
  const notice = useVerificationNotice();
  const requestScope = useNavigationRequest();

  // 성공·오류 안내로 키보드 초점을 이동할 때 사용합니다.
  const feedbackRef = useRef<HTMLParagraphElement>(null);

  useEffect(() => {
    // 전환 진행 중에는 초점을 옮기지 않는다. 로그인 도착 후 그 화면의 안내가 한 번 받는다.
    if (requestScope.isActive() && state.kind === 'error') feedbackRef.current?.focus();
  }, [state.kind, requestScope]);

  async function handleVerify() {
    if (
      !requestScope.isActive()
      || inFlightRef.current
      || state.kind === 'redirecting'
      || (
        state.kind === 'error'
        && !state.canRetry
      )
    ) {
      return;
    }

    const isCurrent = requestScope.begin();
    inFlightRef.current = true;

    setState({
      kind: 'submitting',
    });

    try {
      // 버튼을 누를 때마다 현재 세션의 CSRF 토큰을 조회합니다.
      // 실제로 HTTP 요청이 시작되는 곳이다.
      // 1. getCsrfToken()의 GET 요청이 끝날 때까지 기다림
      // 2. 반환받은 CSRF 토큰으로 인증 POST 요청
      // 3. verifyEmail()이 200/ACTIVE를 확인하면 같은 탭의 로그인으로 이동
      const csrfToken = await getCsrfToken();

      // CSRF 조회 중 화면을 떠났다면 인증 POST를 시작하지 않습니다.
      if (!isCurrent()) {
        return;
      }

      await verifyEmail(
        { token },
        csrfToken,
      );

      if (!isCurrent()) {
        return;
      }

      // verifyEmail은 200 / ACTIVE를 확인한 경우에만 정상 반환합니다.
      setState({ kind: 'redirecting' });
      guard.request(() => {
        notice.publish();
        router.replace('/login');
      });
    } catch (error) {
      if (!isCurrent()) {
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
      if (isCurrent()) inFlightRef.current = false;
    }
  }

  const isSubmitting = state.kind === 'submitting';

  const showButton =
    state.kind !== 'redirecting'
    && (
      state.kind !== 'error'
      || state.canRetry
    );

  return (
    <div className="auth-verification">
      {isSubmitting && <p role="status" aria-live="polite" className="auth-status">이메일 인증을 처리하고 있습니다.</p>}
      {state.kind === 'error' && <p id="email-verification-feedback" ref={feedbackRef} tabIndex={-1} role="alert" className="auth-feedback is-error">{state.message}</p>}
      {state.kind === 'redirecting' && <p role="status" aria-live="polite" className="auth-feedback">
        이메일 인증을 확인했습니다. 로그인 화면으로 이동하고 있습니다.
      </p>}
      {state.kind === 'error' && state.canResend && <p className="auth-copy">
        <Link href="/register/resend" className="iyum-button iyum-button-outline auth-submit">인증 메일 다시 요청하기</Link>
        <span>이미 인증을 완료했다면 로그인해 주세요. </span><Link href="/login" className="iyum-text-link">로그인 화면으로 이동</Link>
      </p>}
      {showButton && <button type="button" onClick={handleVerify} disabled={isSubmitting}
        aria-describedby={state.kind === 'error' ? 'email-verification-feedback' : undefined}
        className="iyum-button iyum-button-primary auth-submit">
        {isSubmitting ? '인증 중…' : state.kind === 'error' ? '다시 인증하기' : '이메일 인증하기'}
      </button>}
    </div>
  );
}
