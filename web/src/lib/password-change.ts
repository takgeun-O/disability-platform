import { normalizeTitle } from './community-posts';
import { validateResetPassword } from './password-reset';

export type PasswordChangeValues = { currentPassword: string; newPassword: string; newPasswordConfirm: string };
export type PasswordChangeField = keyof PasswordChangeValues;
export type PasswordChangeFields = Partial<Record<PasswordChangeField, string>>;
export const PASSWORD_CHANGE_FIELDS: PasswordChangeField[] = ['currentPassword', 'newPassword', 'newPasswordConfirm'];
export function validatePasswordChange(values: PasswordChangeValues): PasswordChangeFields {
  const fields: PasswordChangeFields = validateResetPassword({ ...values, token: '' });
  // Only inspect Java String.isBlank-compatible whitespace; never transform the payload.
  if (!normalizeTitle(values.currentPassword)) fields.currentPassword = '현재 비밀번호를 입력해 주세요.';
  else if (new TextEncoder().encode(values.currentPassword).length > 72) fields.currentPassword = '현재 비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.';
  if (!normalizeTitle(values.newPasswordConfirm)) fields.newPasswordConfirm = '새 비밀번호를 한 번 더 입력해 주세요.';
  return fields;
}
// Retry-After delta-seconds only. No guessed cooldown for absent/invalid headers.
export function retryDeadline(value: string | null, now = Date.now()): number | null {
  if (value === null || !/^\d+$/.test(value)) return null;
  const seconds = Number(value), deadline = now + seconds * 1000;
  return Number.isSafeInteger(seconds) && Number.isSafeInteger(deadline) ? deadline : null;
}
export const remainingSeconds = (deadline: number, now = Date.now()) => Math.max(0, Math.ceil((deadline - now) / 1000));
