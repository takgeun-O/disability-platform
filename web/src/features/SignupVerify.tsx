import Link from '@/components/AppLink';

export default function SignupVerify() {
  return (
    <main>
      <div style={{ maxWidth: 500, margin: '0 auto', padding: '60px 24px 80px' }}>
        <p
          style={{
            fontSize: 11,
            color: '#908D88',
            fontFamily: "'DM Mono', monospace",
            letterSpacing: '0.04em',
            marginBottom: 8,
          }}
        >
          SCR-AUTH-004 · 임시 프로토타입 목적지
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
          이메일 인증
        </h1>
        <p style={{ fontSize: 13, color: '#908D88', lineHeight: 1.7, marginBottom: 32 }}>
          SCR-AUTH-003 회원정보 입력에서 정상적으로 이동했습니다.
          <br />
          이메일 인증 화면(SCR-AUTH-004)은 이후 별도로 구현됩니다.
        </p>
        <Link
          href="/register/info"
          style={{
            fontSize: 13,
            color: '#4A4845',
            textDecoration: 'underline',
            textDecorationStyle: 'dotted',
          }}
        >
          ← SCR-AUTH-003 회원정보 입력으로 돌아가기
        </Link>
      </div>
    </main>
  )
}
