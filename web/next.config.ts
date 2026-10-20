import type { NextConfig } from 'next';
const config: NextConfig = {
  poweredByHeader: false,
  // Preserve RSC headers so proxy can distinguish Flight transport from document 404.
  skipProxyUrlNormalize: true,
  // 상세 metadata에서 notFound 판정 전 헤더가 전송되지 않도록 한다. 검색 차단은 그대로 유지한다.
  htmlLimitedBots: /.*/,
  // 인증 링크의 쿼리 토큰을 Next 개발 서버의 요청 로그에 남기지 않는다.
  logging: { incomingRequests: { ignore: [/^\/register\/verify(?:[/?]|$)/] } },
};
export default config;
