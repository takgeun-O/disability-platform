'use client';
import NextLink from 'next/link';
import { useRouter } from 'next/navigation';
import type { ComponentProps } from 'react';
import { useNavigationGuard } from './NavigationGuard';

// Real Next links retain prefetching, modified clicks and browser history.
export default function AppLink(props: ComponentProps<typeof NextLink>) {
  const guard = useNavigationGuard();
  const router = useRouter();
  return <NextLink {...props} onNavigate={(event) => {
    let prevented = false;
    props.onNavigate?.({ ...event, preventDefault: () => { prevented = true; event.preventDefault(); } });
    if (prevented) return;
    // 확인창 없는 링크도 Next가 실제 전환을 완료하기 전에 이탈 의도를 알린다.
    // onNavigate는 새 탭/수정 키 클릭에는 호출되지 않는다.
    if (!guard.isDirty()) { guard.notifyNavigation(); return; }
    event.preventDefault();
    guard.request(() => {
      const href = typeof props.href === 'string' ? props.href : String(props.href.pathname ?? '/');
      if (props.replace) router.replace(href, {scroll: props.scroll});
      else router.push(href, {scroll: props.scroll});
    });
  }} />;
}
