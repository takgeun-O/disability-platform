"use server";

import { redirect } from 'next/navigation';
import { completePasswordReset, requestPasswordReset, resetErrorState } from '@/lib/password-reset-api';
import { resetToken, validateResetEmail, validateResetPassword, RESET_LINK_ERROR, type ResetState } from '@/lib/password-reset';
import { resetServerContext } from '@/lib/password-reset-server';

function single(data: FormData, key: string): string {
  const values = data.getAll(key);
  return values.length === 1 && typeof values[0] === 'string' ? values[0] : '';
}
// previousState는 신뢰하지 않고 무시하며 FormData를 검증한다. 반환값은 표시 상태뿐이며 입력값을 포함하지 않는다.
export async function requestResetAction(_previous: ResetState, data: FormData): Promise<ResetState> {
  const email = single(data, 'email'), fields = validateResetEmail(email);
  if (Object.keys(fields).length) return { status: 'error', fields };
  try {
    const { csrf, transport } = await resetServerContext();
    await requestPasswordReset(email, csrf, transport);
    return { status: 'accepted', fields: {} };
  } catch (error) { return resetErrorState(error); }
}
export async function completeResetAction(_previous: ResetState, data: FormData): Promise<ResetState> {
  const token = resetToken(single(data, 'token'));
  if (!token) return { status: 'invalid-link', fields: {}, message: RESET_LINK_ERROR };
  const values = { token, newPassword: single(data, 'newPassword'), newPasswordConfirm: single(data, 'newPasswordConfirm') };
  const fields = validateResetPassword(values);
  if (Object.keys(fields).length) return { status: 'error', fields };
  try {
    const { csrf, transport } = await resetServerContext();
    await completePasswordReset(values, csrf, transport);
  } catch (error) { return resetErrorState(error); }
  // 성공 문서에 원본 토큰·비밀번호를 다시 직렬화하지 않는다.
  redirect('/reset-password/complete');
}
