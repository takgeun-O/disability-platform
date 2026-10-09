export type EmailVerificationLink =
  | { kind: 'missing' }     // URL에 token이 없음
  | { kind: 'invalid' }     // 토큰이 여러 개이거나 형식이 잘못됨
  | { kind: 'ready'; token: string };   // 서버에 인증을 요청할 수 있는 형식 (인증 성공이나 유효한 토큰이란 의미는 아님)

export function readEmailVerificationLink(
    tokens: string[], // /register/verify?token=첫번째값&token=두번째값
): EmailVerificationLink {
    // token이라는 쿼리 파라미터 자체가 없는 경우
    if(tokens.length === 0) {
        return { kind: 'missing' };
    }

    // token이 여러 번 전달되면 어느 값을 사용할지 임의로 선택하지 않음
    if(tokens.length !== 1) {
        return { kind: 'invalid' };
    }

    // 위 조건을 통과하면 tokens는 정확히 하나만 전달이 된 것이 보장되므로 tokens[0]로 꺼내오는 것
    const token = tokens[0];

    // 원본 값을 변경하지 않고 길이와 허용 문자를 검사
    if (
        token.length !== 43
        || !/^[A-Za-z0-9_-]{43}$/.test(token)
    ) {
        return { kind: 'invalid' };
    }

    return {
        kind: 'ready',
        token,
    };
}