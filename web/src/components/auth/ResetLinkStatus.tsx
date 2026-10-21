import Link from '@/components/AppLink';
import AuthFrame from './AuthFrame';
import { RESET_LINK_ERROR } from '@/lib/password-reset';

export function ResetLinkInvalid() {
  return <AuthFrame title="사용할 수 없는 재설정 링크입니다" description={RESET_LINK_ERROR}>
    <Link className="iyum-button iyum-button-primary auth-submit" href="/forgot-password">새 재설정 메일 요청</Link>
    <nav className="auth-links" aria-label="계정 관련 이동"><Link href="/login" className="iyum-text-link">로그인 화면으로 돌아가기</Link></nav>
  </AuthFrame>;
}
