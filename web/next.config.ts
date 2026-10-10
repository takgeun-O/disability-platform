import type { NextConfig } from 'next';
const config: NextConfig = {
  poweredByHeader: false,
  // 인증 링크의 쿼리 토큰을 Next 개발 서버의 요청 로그에 남기지 않는다.
  logging: { incomingRequests: { ignore: [/^\/register\/verify(?:[/?]|$)/] } },
};
export default config;
