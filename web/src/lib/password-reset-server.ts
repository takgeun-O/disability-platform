import 'server-only';
import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';
import { getCsrfContext } from './signup-api';

// 무JS 폼만 사용하는 요청별 Spring 세션 전달. 인증을 별도로 생성하지 않는다.
// 현재 Spring 기본 쿠키 이름은 JSESSIONID. 다른 앱 쿠키/Authorization/전달 IP 헤더는 보내지 않는다.
export async function resetServerContext() {
  const base = process.env.IYUM_API_INTERNAL_URL ?? process.env.NEXT_PUBLIC_API_BASE_URL;
  if (!base || !/^https?:\/\//.test(base)) throw new Error('Reset API unavailable');
  const jar = await cookies();
  let session = jar.get('JSESSIONID')?.value;
  const fetcher: typeof fetch = async (input, init) => {
    const headers = new Headers(init?.headers);
    if (session) headers.set('Cookie', `JSESSIONID=${session}`);
    const response = await fetch(input, { ...init, headers });
    // Next의 쿠키 파서로 Set-Cookie 속성을 보존하며 허용한 세션 쿠키만 전달한다.
    const outgoing = new NextResponse(null, { headers: response.headers }).cookies.get('JSESSIONID');
    if (outgoing) { session = outgoing.value; jar.set(outgoing); }
    return response;
  };
  const csrf = await getCsrfContext(base, fetcher);
  return { csrf, transport: { base, fetcher } };
}
