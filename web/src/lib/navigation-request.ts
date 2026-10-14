// UI 요청의 수명만 관리한다. 서버의 저장/트랜잭션을 취소하는 기능은 아니다.
export function createNavigationRequestScope(getNavigationVersion: () => number) {
  let active = false;
  let screenVersion = 0;
  let generation = 0;
  const isActive = () => active && screenVersion === getNavigationVersion();
  return {
    activate() { active = true; screenVersion = getNavigationVersion(); },
    deactivate() { active = false; generation += 1; },
    isActive,
    begin() {
      const request = ++generation;
      return () => isActive() && request === generation;
    },
  };
}
