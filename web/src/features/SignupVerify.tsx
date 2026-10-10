'use client';   // 이 파일을 클라이언트 컴포넌트로 사용하는 경계 선언

import { useSearchParams } from 'next/navigation';

import Link from '@/components/AppLink';
import { readEmailVerificationLink } from '@/lib/email-verification-link';

import { useSignupFlow } from './SignupFlow';
import EmailVerificationAction from './EmailVerificationAction';


const messageStyle = {
    fontSize: 13,
    color: '#908D88',
    lineHeight: 1.7,
    marginBottom: 32,
};

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
        <main>
            <div
                style={{
                    maxWidth: 500,
                    margin: '0 auto',
                    padding: '60px 24px 80px',
                }}
            >
                <p
                    style={{
                        fontSize: 11,
                        color: '#908D88',
                        fontFamily: "'DM Mono', monospace",
                        letterSpacing: '0.04em',
                        marginBottom: 8,
                    }}
                >
                    회원가입 3/3
                </p>

                <h1
                    style={{
                        fontSize: 22,
                        fontWeight: 700,
                        color: '#1A1918',
                        letterSpacing: '-0.01em',
                        marginBottom: 16,
                    }}
                >
                    {title}
                </h1>

                {link.kind === 'ready' && (
                    <section aria-label="이메일 인증 안내">
                        <p style={messageStyle}>
                            이메일 인증을 완료하면 계정이 활성화됩니다.
                            <br />
                            인증을 완료한 후 직접 로그인해 주세요.
                        </p>

                        <EmailVerificationAction
                            key={link.token}    // 자식 컴포넌트에 인증 토큰 전달
                            token={link.token}  // 토큰이 달라지면 해당 컴포넌트의 상태를 새로 시작
                        />
                    </section>
                )}

                {/* link.kind === 'invalid'가 참이면 오른쪽의 <p>를 화면에 표시한다. */}
                {link.kind === 'invalid' && (
                    <p role="alert" style={messageStyle}>
                        인증 링크의 형식이 올바르지 않습니다.
                        <br />
                        메일에 포함된 링크 전체를 다시 열어 주세요.
                    </p>
                )}

                {isPending && (
                    <div role="status" aria-live="polite">
                        <p
                            style={{
                                fontSize: 14,
                                fontWeight: 600,
                                color: '#4A4845',
                                marginBottom: 12,
                            }}
                        >
                            이메일 인증 대기 · PENDING
                        </p>

                        <p style={messageStyle}>
                            가입한 이메일의 메일함에서 인증 메일을 확인해 주세요.
                            <br />
                            메일의 링크에서 인증을 완료한 후 직접 로그인할 수 있습니다.
                        </p>
                    </div>
                )}

                {link.kind === 'missing' && !isPending && (
                    <p style={messageStyle}>
                        메일에 포함된 이메일 인증 링크로 접속해 주세요.
                        <br />
                        이미 가입을 요청했다면 이 안내만으로 가입 실패를
                        의미하지는 않습니다.
                    </p>
                )}

                {link.kind !== 'ready' && (
                    <p style={{ marginBottom: 24 }}>
                        <Link href="/register/resend" style={{ fontSize: 14, color: '#4A4845', textDecoration: 'underline' }}>
                            인증 메일 다시 요청하기
                        </Link>
                    </p>
                )}

                <Link
                    href="/"
                    style={{
                        fontSize: 13,
                        color: '#4A4845',
                        textDecoration: 'underline',
                        textDecorationStyle: 'dotted',
                    }}
                >
                    홈으로 돌아가기
                </Link>
            </div>
        </main>
    );
}