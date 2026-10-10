import type { SignupAgreement, SignupApiError, SignupRequest } from './signup-api';

export type SignupFields = Pick<SignupRequest, 'email' | 'password' | 'passwordConfirm' | 'nickname'>;
export type SignupFormErrors = Partial<Record<keyof SignupFields | 'form', string>>;
export const DEVELOPMENT_TERMS_VERSION = 'dev-v1';

// 체크박스의 실제 선택값을 서버 약관 코드와 연결합니다.
export function createAgreements(terms: boolean, privacy: boolean): SignupAgreement[] {
  return [
    { termsCode: 'SERVICE_TERMS', version: DEVELOPMENT_TERMS_VERSION, agreed: terms },
    { termsCode: 'PRIVACY_COLLECTION_USE', version: DEVELOPMENT_TERMS_VERSION, agreed: privacy },
  ];
}

export function buildSignupRequest(fields: SignupFields, agreements: SignupAgreement[]): SignupRequest {
  // 특히 비밀번호 두 값은 trim 등으로 변형하지 않습니다. 서버가 이메일·닉네임을 정규화합니다.
  return { ...fields, agreements };
}

// 가입과 재전송 폼이 같은 입력 안내 정책을 사용한다. 최종 검증은 서버가 담당한다.
export function validateEmail(value: string): string | undefined {
  const email = value.trim();
  if (!email) return '이메일을 입력해 주세요.';
  if (email.length > 254 || !/^[^\s@]+@[^\s@]+$/.test(email)) {
    return '올바른 이메일 주소를 254자 이내로 입력해 주세요.';
  }
}

export function validateSignupForm(fields: SignupFields, agreements: SignupAgreement[] | null): SignupFormErrors {
  const errors: SignupFormErrors = {};
  const nickname = fields.nickname.trim();
  const emailError = validateEmail(fields.email);
  if (emailError) errors.email = emailError;
  if (!fields.password) errors.password = '비밀번호를 입력해 주세요.';
  else if (fields.password.length < 8 || !/[A-Za-z]/.test(fields.password) || !/[0-9]/.test(fields.password)) {
    errors.password = '비밀번호는 8자 이상이며 영문과 숫자를 포함해야 합니다.';
  } else if (new TextEncoder().encode(fields.password).length > 72) {
    errors.password = '비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.';
  }
  if (!fields.passwordConfirm) errors.passwordConfirm = '비밀번호를 한 번 더 입력해 주세요.';
  else if (fields.password !== fields.passwordConfirm) errors.passwordConfirm = '비밀번호가 일치하지 않습니다.';
  if (!nickname) errors.nickname = '닉네임을 입력해 주세요.';
  else if (!/^[가-힣A-Za-z0-9_]{2,20}$/.test(nickname)) {
    errors.nickname = '닉네임은 2~20자의 한글·영문·숫자·밑줄만 사용할 수 있습니다.';
  } else if (['관리자', '운영자', 'admin', 'administrator', 'moderator'].includes(nickname.toLowerCase())) {
    errors.nickname = '사용할 수 없는 닉네임입니다.';
  }
  if (!agreements || !['SERVICE_TERMS', 'PRIVACY_COLLECTION_USE'].every(code =>
    agreements.some(agreement => agreement.termsCode === code && agreement.agreed))) {
    errors.form = '필수 약관에 동의해야 가입할 수 있습니다. 약관 동의를 다시 확인해 주세요.';
  }
  return errors;
}

export function mapSignupApiError(error: Pick<SignupApiError, 'code' | 'message' | 'fieldErrors'>): SignupFormErrors {
  if (error.code === 'EMAIL_ALREADY_EXISTS') return { email: error.message };
  if (error.code === 'NICKNAME_ALREADY_EXISTS') return { nickname: error.message };
  const errors: SignupFormErrors = {};
  const commonMessages: string[] = [];
  for (const { field, message } of error.fieldErrors) {
    if (field === 'email' || field === 'password' || field === 'passwordConfirm' || field === 'nickname') {
      errors[field] = errors[field] ? `${errors[field]} ${message}` : message;
    } else {
      // agreements[0].version처럼 별도 입력란이 없는 오류도 빠뜨리지 않습니다.
      commonMessages.push(message);
    }
  }
  if (commonMessages.length > 0) errors.form = commonMessages.join(' ');
  if (Object.keys(errors).length === 0) errors.form = error.message || '가입 요청을 처리하지 못했습니다.';
  return errors;
}
