'use client';
import { createContext, useCallback, useContext, useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react';

type Guard = {
  isDirty: () => boolean;
  getApprovalVersion: () => number;
  setDirty: (value: boolean) => void;
  request: (action: () => void) => void;
  pending: (() => void) | null;
  cancel: () => void;
  proceed: () => void;
};
const Context = createContext<Guard | null>(null);
const INDEX = '__iyumNavigationIndex';

export function NavigationGuardProvider({children}: {children: ReactNode}) {
  const dirty = useRef(false);
  // 경로 전환/언마운트보다 먼저 확정된 이탈을 비동기 요청에서도 식별한다.
  const approvalVersion = useRef(0);
  const getApprovalVersion = useCallback(() => approvalVersion.current, []);
  const [pending, setPending] = useState<(() => void) | null>(null);
  const request = useCallback((action: () => void) => {
    if (dirty.current) setPending(() => action);
    else action();
  }, []);
  const setDirty = useCallback((value: boolean) => {dirty.current = value;}, []);

  useEffect(() => {
    // Preserve Next's history fields. Index only our entries; never replace __NA/tree.
    const push = history.pushState.bind(history);
    const replace = history.replaceState.bind(history);
    let index = Number(history.state?.[INDEX] ?? 0);
    let restoring = false;
    let bypass = false;
    replace({...history.state, [INDEX]: index}, '');
    const indexedPush: History['pushState'] = (data, title, url) => {
      index += 1;
      push({...data, [INDEX]: index}, title, url);
    };
    const indexedReplace: History['replaceState'] = (data, title, url) => {
      replace({...data, [INDEX]: index}, title, url);
    };
    history.pushState = indexedPush;
    history.replaceState = indexedReplace;
    const onPop = (event: PopStateEvent) => {
      const target = event.state?.[INDEX];
      if (restoring) {restoring = false; event.stopImmediatePropagation(); return;}
      if (typeof target !== 'number') return;
      const delta = target - index;
      if (!dirty.current || bypass || delta === 0) {index = target; bypass = false; return;}
      event.stopImmediatePropagation();
      restoring = true;
      history.go(-delta);
      setPending(() => () => {bypass = true; history.go(delta);});
    };
    const onUnload = (event: BeforeUnloadEvent) => {
      if (dirty.current) {event.preventDefault(); event.returnValue = '';}
    };
    window.addEventListener('popstate', onPop, true);
    window.addEventListener('beforeunload', onUnload);
    return () => {
      window.removeEventListener('popstate', onPop, true);
      window.removeEventListener('beforeunload', onUnload);
      if (history.pushState === indexedPush) history.pushState = push;
      if (history.replaceState === indexedReplace) history.replaceState = replace;
    };
  }, []);

  return <Context.Provider value={{
    isDirty: () => dirty.current, getApprovalVersion, setDirty, request, pending,
    cancel: () => setPending(null),
    proceed: () => {
      if (!pending) return;
      approvalVersion.current += 1;
      dirty.current = false; setPending(null); pending();
    },
  }}>{children}</Context.Provider>;
}
export function useNavigationGuard() {
  const guard = useContext(Context);
  if (!guard) throw new Error('NavigationGuardProvider is required');
  return guard;
}
export function useUnsavedChanges(isDirty: boolean) {
  const guard = useNavigationGuard();
  const setDirty = guard.setDirty;
  useLayoutEffect(() => {setDirty(isDirty); return () => setDirty(false);}, [isDirty, setDirty]);
  // proceed가 dirty를 초기화한 뒤에도 같은 폼의 새 편집은 다시 보호해야 한다.
  // isDirty가 이미 true이면 위 effect가 재실행되지 않으므로 편집 이벤트에서도 알린다.
  return {state: guard.pending ? 'blocked' : 'unblocked', reset: guard.cancel, proceed: guard.proceed, request: guard.request,
    getApprovalVersion: guard.getApprovalVersion, markDirty: () => setDirty(true)};
}
