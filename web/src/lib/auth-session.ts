import * as api from './auth-api';
import type { LoginRequest, Member } from './auth-api';

type AuthApi = Pick<typeof api, 'getAuthCsrf' | 'login' | 'getCurrentMember' | 'logout'>;
export type AuthState = {
  status: 'checking' | 'authenticated' | 'anonymous' | 'error';
  member: Member | null;
  pending: 'login' | 'logout' | null;
};
const INITIAL: AuthState = { status: 'checking', member: null, pending: null };
export type LoginResult = { kind: 'authenticated'; csrfReady: boolean; recovered: boolean } | { kind: 'ignored' };
export type LogoutResult = { kind: 'anonymous'; confirmedBy: 'logout' | 'me' } | { kind: 'ignored' };

// Provider마다 한 개 생성한다. 모듈 전역 싱글턴이나 브라우저 저장소에 회원을 보관하지 않는다.
export function createAuthSession(client: AuthApi = api) {
  let state = INITIAL;
  let generation = 0;
  let active = false;
  const listeners = new Set<() => void>();
  const publish = (next: AuthState) => { state = next; listeners.forEach(listener => listener()); };
  const current = (ticket: number) => active && ticket === generation;
  const setMember = (member: Member | null) => publish({
    status: member ? 'authenticated' : 'anonymous', member, pending: state.pending,
  });
  // GET도 순서를 가진다. 이전 GET의 성공·401·실패 모두 새 인증 결과를 덮어쓰지 못한다.
  async function readCurrent(ticket: number): Promise<'known' | 'unknown' | 'stale'> {
    try {
      const member = await client.getCurrentMember();
      if (!current(ticket)) return 'stale';
      setMember(member);
      return 'known';
    } catch {
      if (!current(ticket)) return 'stale';
      publish({ status: 'error', member: null, pending: state.pending });
      return 'unknown';
    }
  }
  async function refresh() {
    if (!active || state.pending) return;
    const ticket = ++generation;
    publish({ status: 'checking', member: null, pending: null });
    await readCurrent(ticket);
  }
  async function renewCsrf(ticket: number): Promise<boolean> {
    try { await client.getAuthCsrf(); return current(ticket); }
    catch { return false; }
    // 토큰은 캐시하지 않는다. 다음 POST에서도 현재 세션의 토큰을 다시 조회한다.
  }
  async function signIn(values: LoginRequest, onAuthenticated: () => void): Promise<LoginResult> {
    if (!active || state.pending) return { kind: 'ignored' };
    const ticket = ++generation;
    publish({ ...state, pending: 'login' });
    try {
      const csrf = await client.getAuthCsrf();
      if (!current(ticket)) return { kind: 'ignored' };
      let member: Member;
      try {
        member = await client.login(values, csrf);
      } catch (error) {
        if (!current(ticket)) return { kind: 'ignored' };
        if (error instanceof api.AuthApiError && error.uncertain) {
          const result = await readCurrent(ticket);
          if (!current(ticket)) return { kind: 'ignored' };
          if (result === 'known' && state.member) {
            onAuthenticated();
            const csrfReady = await renewCsrf(ticket);
            return current(ticket) ? { kind: 'authenticated', csrfReady, recovered: true } : { kind: 'ignored' };
          }
        } else if (error instanceof api.AuthApiError && error.code === 'CSRF_TOKEN_INVALID') {
          await renewCsrf(ticket); // 갱신은 GET만. POST는 사용자가 직접 다시 제출한다.
        }
        throw error;
      }
      if (!current(ticket)) return { kind: 'ignored' };
      setMember(member);
      onAuthenticated(); // 새 CSRF 조회를 기다리기 전에 비밀번호 입력을 비운다.
      const csrfReady = await renewCsrf(ticket);
      if (!current(ticket)) return { kind: 'ignored' };
      return { kind: 'authenticated', csrfReady, recovered: false };
    } catch (error) {
      // 최초 /me를 무효화한 상태에서 POST 준비가 실패해도 checking에 멈추지 않는다.
      if (current(ticket) && state.status === 'checking') await readCurrent(ticket);
      if (!current(ticket)) return { kind: 'ignored' };
      throw error;
    } finally {
      if (current(ticket)) publish({ ...state, pending: null });
    }
  }
  async function signOut(): Promise<LogoutResult> {
    if (!active || state.pending) return { kind: 'ignored' };
    const ticket = ++generation;
    publish({ ...state, pending: 'logout' });
    try {
      const csrf = await client.getAuthCsrf();
      if (!current(ticket)) return { kind: 'ignored' };
      try {
        await client.logout(csrf);
      } catch (error) {
        if (!current(ticket)) return { kind: 'ignored' };
        if (error instanceof api.AuthApiError && error.uncertain) {
          const result = await readCurrent(ticket);
          if (!current(ticket)) return { kind: 'ignored' };
          if (result === 'known' && state.status === 'anonymous') {
            return { kind: 'anonymous', confirmedBy: 'me' };
          }
        } else if (error instanceof api.AuthApiError && error.code === 'CSRF_TOKEN_INVALID') {
          await renewCsrf(ticket);
        }
        throw error;
      }
      if (!current(ticket)) return { kind: 'ignored' };
      setMember(null);
      // 성공 후 별도 CSRF 조회 실패가 회원 상태를 복원하는 경로는 없다.
      return { kind: 'anonymous', confirmedBy: 'logout' };
    } finally {
      if (current(ticket)) publish({ ...state, pending: null });
    }
  }
  return {
    getSnapshot: () => state,
    getServerSnapshot: () => INITIAL,
    subscribe: (listener: () => void) => { listeners.add(listener); return () => { listeners.delete(listener); }; },
    start: () => { active = true; publish({ ...state, pending: null }); void refresh(); },
    stop: () => { active = false; generation++; },
    // A private API 401 expires only the still-current session, never a newer account.
    expire(memberId: number) {
      if (!active || state.pending || state.status !== 'authenticated' || state.member?.memberId !== memberId) return false;
      generation++;
      publish({ status: 'anonymous', member: null, pending: null });
      return true;
    },
    // Only a validated profile response for the still-current member may update the header.
    updateNickname(memberId: number, nickname: string) {
      if (!active || state.status !== 'authenticated' || state.pending || state.member?.memberId !== memberId) return false;
      publish({ ...state, member: { ...state.member, nickname } });
      return true;
    },
    refresh, signIn, signOut,
  };
}
