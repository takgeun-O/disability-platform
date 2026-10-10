import type { Metadata } from 'next';
import { Suspense } from 'react';

import SignupVerify from '@/features/SignupVerify';

export const metadata: Metadata = {
    // 이 페이지에서 나가는 요청의 Referer 헤더에
    // 토큰이 포함된 현재 페이지 URL이 전달되지 않도록 한다.
  referrer: 'no-referrer',
};

function VerificationFallback() {
    // 준비 중에 보여줄 화면을 정의한다. (임시 안내 화면)
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
    // SignupVerify : 실제로 보여줄 인증 화면
    <Suspense fallback={<VerificationFallback />}>
      <SignupVerify />
    </Suspense>
  );
}