import { validateEmail } from './signup-form';

export type ResetValues = { token: string; newPassword: string; newPasswordConfirm: string };
export type ResetFields = Partial<Record<'email' | 'newPassword' | 'newPasswordConfirm', string>>;
export type ResetState = { status: 'idle' | 'accepted' | 'error' | 'invalid-link'; fields: ResetFields; message?: string };
export const RESET_INITIAL: ResetState = { status: 'idle', fields: {} };
export const RESET_ACCEPTED = '입력하신 이메일이 재설정 가능한 계정이면 비밀번호 재설정 안내 메일을 보내드립니다.';
export const RESET_LINK_ERROR = '링크가 만료되었거나 이미 사용되었습니다. 비밀번호를 다시 변경하려면 새 재설정 메일을 요청해 주세요.';
export const PASSWORD_RULE = '8자 이상 · 영문과 숫자 포함';

export function resetToken(value: string | string[] | undefined): string | null {
  return typeof value === 'string' && value.length === 43 && /^[A-Za-z0-9_-]{43}$/.test(value) ? value : null;
}
export function validateResetEmail(email: string): ResetFields {
  const error = validateEmail(email);
  return error ? { email: error } : {};
}
export function validateResetPassword(values: ResetValues): ResetFields {
  const fields: ResetFields = {};
  // Java String.length와 같은 UTF-16 길이. 전송값은 trim/정규화하지 않는다.
  if (!values.newPassword) fields.newPassword = '새 비밀번호를 입력해 주세요.';
  else if (values.newPassword.length < 8) fields.newPassword = '비밀번호를 8자 이상 입력해 주세요.';
  else if (!/[A-Za-z]/.test(values.newPassword) || !/[0-9]/.test(values.newPassword)) fields.newPassword = '영문과 숫자를 모두 포함해 주세요.';
  else if (new TextEncoder().encode(values.newPassword).length > 72) fields.newPassword = '비밀번호가 너무 깁니다. 글자 수를 줄여 주세요.';
  if (!values.newPasswordConfirm) fields.newPasswordConfirm = '새 비밀번호를 한 번 더 입력해 주세요.';
  else if (values.newPassword !== values.newPasswordConfirm) fields.newPasswordConfirm = '비밀번호가 일치하지 않습니다.';
  return fields;
}
