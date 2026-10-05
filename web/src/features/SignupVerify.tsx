"use client";

import Link from '@/components/AppLink';
import { useSignupFlow } from './SignupFlow';

export default function SignupVerify() {
  const { status } = useSignupFlow();
  const isPending = status === 'PENDING';
  return (
    <main>
      <div style={{ maxWidth: 500, margin: '0 auto', padding: '60px 24px 80px' }}>
        <p style={{ fontSize: 11, color: '#908D88', fontFamily: "'DM Mono', monospace", letterSpacing: '0.04em', marginBottom: 8 }}>
          회원가입 3/3
        </p>
        <h1 style={{ fontSize: 22, fontWeight: 700, color: '#1A1918', letterSpacing: '-0.01em', marginBottom: 16 }}>
          {isPending ? '회원가입 요청이 완료되었습니다' : '이메일 인증 대기 안내'}
        </h1>
        {isPending ? (
          <div role="status" aria-live="polite">
            <p style={{ fontSize: 14, fontWeight: 600, color: '#4A4845', marginBottom: 12 }}>이메일 인증 대기 · PENDING</p>
            <p style={{ fontSize: 13, color: '#908D88', lineHeight: 1.7, marginBottom: 32 }}>
              이메일 인증과 메일 발송 기능은 준비 중입니다. 현재 인증 메일은 발송되지 않습니다.
              <br />
              기능이 제공되면 이메일 인증을 마친 후 직접 로그인할 수 있습니다.
            </p>
          </div>
        ) : (
          <p style={{ fontSize: 13, color: '#908D88', lineHeight: 1.7, marginBottom: 32 }}>
            현재 이 화면에서 확인할 수 있는 가입 결과가 없습니다.
            <br />
            이미 가입을 요청했다면 이 안내만으로 가입 실패를 의미하지는 않습니다.
          </p>
        )}
        <Link href="/" style={{ fontSize: 13, color: '#4A4845', textDecoration: 'underline', textDecorationStyle: 'dotted' }}>
          홈으로 돌아가기
        </Link>
      </div>
    </main>
  );
}
