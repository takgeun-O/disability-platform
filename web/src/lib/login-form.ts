import { validateEmail } from './signup-form';
import type { LoginRequest, LoginFieldErrors } from './auth-api';

export function validateLoginForm(values: LoginRequest): LoginFieldErrors {
  const errors: LoginFieldErrors = {};
  const emailError = validateEmail(values.email);
  if (emailError) errors.email = emailError;
  // 공백 여부만 검사한다. 실제 전송값은 원문 그대로 유지한다.
  const passwordError = validateCurrentPassword(values.password);
  if (passwordError) errors.password = passwordError;
  // 로그인에는 가입용 최소 길이·문자 조합 규칙을 적용하지 않는다.
  return errors;
}

export function validateCurrentPassword(password: string): string {
  if (!password || !password.trim()) return '비밀번호를 입력해 주세요.';
  if (new TextEncoder().encode(password).length > 72) return '비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.';
  return '';
}
