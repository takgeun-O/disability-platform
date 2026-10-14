'use client';   // 이 파일을 클라이언트 컴포넌트로 사용하는 경계 선언

import { useSearchParams } from 'next/navigation';

import Link from '@/components/AppLink';
import AuthFrame from '@/components/auth/AuthFrame';
import { readEmailVerificationLink } from '@/lib/email-verification-link';

import { useSignupFlow } from './SignupFlow';
import EmailVerificationAction from './EmailVerificationAction';


export default function SignupVerify() {
    // 현재 URL의 ? 뒤에 있는 쿼리 파라미터를 읽는 Next.js 기능
    // /register/verify?token=abc&from=email
    const searchParams = useSearchParams();

    // 객체의 status를 가져오되, 이 함수에서는 signupStatus 라는 이름으로 사용한다는 JavaScript 문법
    // 이 값은 가입 당시 받은 결과를 메모리에 보관한 값 (현재 DB의 회원 상태를 다시 조회한 값은 아님)
    const { status: signupStatus } = useSignupFlow();

    const link = readEmailVerificationLink(
        searchParams.getAll('token'),   // 예를 들어 ['abc'] 문자열을 readEmailVerificationLink 함수에 전달
    );

    // 토큰이 있을 때는 메일 링크 처리를 우선해라.
    // URL에 토큰이 없음 && 기존 가입 흐름에서 PENDING 응답을 받았음 -> 가입 직후 대기 안내 표시
    const isPending =
        link.kind === 'missing'
        && signupStatus === 'PENDING';

    let title = '이메일 인증 안내';

    if (link.kind === 'ready') {
        title = '이메일 인증';
    } else if (link.kind === 'invalid') {
        title = '인증 링크를 확인해 주세요';
    } else if (isPending) {
        title = '회원가입 요청이 완료되었습니다';
    }

    return (
        <AuthFrame title={title} step={3}>
            {link.kind === 'ready' && <section aria-label="이메일 인증 안내">
                <p className="auth-copy">이메일 인증을 완료하면 계정이 활성화됩니다.<br />인증이 완료되면 로그인 화면으로 이동합니다.</p>
                <EmailVerificationAction
                    key={link.token} // 토큰이 달라지면 자식 상태를 새로 시작한다.
                    token={link.token}
                />
            </section>}
            {link.kind === 'invalid' && <p role="alert" className="auth-feedback is-error">
                인증 링크의 형식이 올바르지 않습니다.<br />메일에 포함된 링크 전체를 다시 열어 주세요.
            </p>}
            {link.kind === 'invalid' && <p className="auth-copy">이미 인증을 완료했다면 로그인해 주세요. <Link href="/login" className="iyum-text-link">로그인 화면으로 이동</Link></p>}
            {isPending && <div role="status" aria-live="polite" className="auth-feedback">
                <p className="auth-feedback-title">이메일 인증 대기 · PENDING</p>
                <p>가입한 이메일의 메일함에서 인증 메일을 확인해 주세요.<br />메일의 링크에서 인증을 완료한 후 직접 로그인할 수 있습니다.</p>
            </div>}
            {link.kind === 'missing' && !isPending && <p className="auth-copy">
                메일에 포함된 이메일 인증 링크로 접속해 주세요.<br />이미 가입을 요청했다면 이 안내만으로 가입 실패를 의미하지는 않습니다.
            </p>}
            {link.kind !== 'ready' && <Link href="/register/resend" className="iyum-button iyum-button-outline auth-submit">인증 메일 다시 요청하기</Link>}
            <div className="auth-links"><Link href="/" className="iyum-text-link">홈으로 돌아가기</Link></div>
        </AuthFrame>
    );
}
