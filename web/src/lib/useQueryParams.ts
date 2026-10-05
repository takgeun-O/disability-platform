'use client';
import { useCallback } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';

export function useQueryParams() {
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const setParams = useCallback((values: Record<string, string>, options?: {replace?: boolean}) => {
    const query = new URLSearchParams(values).toString();
    const href = `${pathname}${query ? `?${query}` : ''}`;
    if (options?.replace) router.replace(href, {scroll: false});
    else router.push(href, {scroll: false});
  }, [pathname, router]);
  return [params, setParams] as const;
}
