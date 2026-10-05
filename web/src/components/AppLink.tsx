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
    props.onNavigate?.(event);
    if (!guard.isDirty()) return;
    event.preventDefault();
    guard.request(() => {
      const href = typeof props.href === 'string' ? props.href : String(props.href.pathname ?? '/');
      if (props.replace) router.replace(href, {scroll: props.scroll});
      else router.push(href, {scroll: props.scroll});
    });
  }} />;
}
