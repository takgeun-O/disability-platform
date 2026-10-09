import type { Metadata } from 'next';
import { Suspense } from 'react';

import SignupVerify from '@/features/SignupVerify';

export const metadata: Metadata = {
    // 페이지 주소에는 이메일 인증 토큰이 포함된다.
    // 이 설정은 브라우저가 이 페이지에서 보내는 요청에 이전 페이지 주소인 Referer 정보를
    // 싣지 않도록 하기 위한 설정. (토큰이 포함된 URL이 참조 주소로 전달되는 것을 막는 목적)
  referrer: 'no-referrer',
};

function VerificationFallback() {
  return (
    <main>
      <div
        style={{
          maxWidth: 500,
          margin: '0 auto',
          padding: '60px 24px 80px',
        }}
      >
        <p role="status" aria-live="polite">
          인증 정보를 확인하고 있습니다.
        </p>
      </div>
    </main>
  );
}

export default function Page() {
  return (
    // URL의 쿼리를 읽는 화면이 준비되는 동안 fallback에 지정한 안내를 표시하도록 구성
    // 개발 서버에서는 문제가 없어보여도 프로덕션 빌드에서 오류가 날 수 있어서 함께 적용한다.
    <Suspense fallback={<VerificationFallback />}>
      <SignupVerify />
    </Suspense>
  );
}