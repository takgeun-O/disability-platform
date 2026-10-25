"use client"
import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import Link from '@/components/AppLink'
import PasswordChangeDialog from '@/components/member/PasswordChangeDialog'
import WithdrawalDialog from '@/components/member/WithdrawalDialog'
import MyActivity from '@/components/member/MyActivity'
import SuccessToast from '@/components/member/SuccessToast'
import { usePostVersion, getPostVersion } from '@/lib/community-mutations'
import OverlayDialog from '@/components/OverlayDialog'
import { useAuth } from '@/components/AuthProvider'
import { useNavigationRequest } from '@/components/NavigationGuard'
import { FieldError } from '@/components/auth/AuthFields'
import { FeedAvatar } from '@/components/community/FeedNavigation'
import { safeReturnPath } from '@/lib/navigation'
import { getProfile, getActivitySummary, getMemberCsrf, updateNickname, nicknameError, normalizeNickname, joinedMonth, MemberApiError, type Profile, type ActivitySummary } from '@/lib/member-api'
const loginHref = `/login?from=${encodeURIComponent(safeReturnPath('/mypage'))}`
const message = (error: unknown) => error instanceof Error ? error.message : '정보를 확인하지 못했습니다. 다시 시도해 주세요.'
type Resource<T> = { data?: T; error?: string }
function LoginRequired() { return <div className="mypage-message"><h2>로그인이 필요해요</h2><p>로그인하고 내 프로필과 활동을 확인해 보세요.</p><Link className="iyum-button iyum-button-primary" href={loginHref}>로그인하기</Link></div> }
export default function MyPage() {
  const auth = useAuth()
  const [passwordNotice, setPasswordNotice] = useState('')
  return <main id="main-content" className="mypage"><header className="mypage-heading"><h1>마이페이지</h1><p>내가 나눈 이야기와 계정 정보를 한곳에서 확인해요.</p></header>
    {passwordNotice && <p role="status" className="mypage-notice has-notice">{passwordNotice}</p>}
    {auth.status === 'checking' || auth.pending ? <p role="status">회원 상태를 확인하고 있습니다.</p>
      : auth.status === 'error' ? <div className="mypage-message" role="alert"><p>로그인 상태를 확인하지 못했어요.</p><button className="iyum-button iyum-button-outline" onClick={() => void auth.refresh()}>로그인 상태 다시 확인</button></div>
      : auth.status !== 'authenticated' || !auth.member ? <LoginRequired /> : <MemberPage key={auth.member.memberId} memberId={auth.member.memberId} onPasswordNotice={setPasswordNotice} />}
  </main>
}
function MemberPage({ memberId, onPasswordNotice }: { memberId: number; onPasswordNotice: (message: string) => void }) {
  const { captureMemberRequest, memberRequests } = useAuth(), scope = useNavigationRequest()
  const [profile, setProfile] = useState<Resource<Profile>>({}), [summary, setSummary] = useState<Resource<ActivitySummary>>({})
  const [profileAttempt, setProfileAttempt] = useState(0), [summaryAttempt, setSummaryAttempt] = useState(0)
  const [changingPassword, setChangingPassword] = useState(false)
  const [withdrawing, setWithdrawing] = useState(false)
  const [editing, setEditing] = useState(false)
  const [toast, setToast] = useState<{ id: number; message: string; returnFocus: string } | null>(null)
  const toastId = useRef(0)
  const dismissToast = useCallback(() => setToast(null), [])
  function showSuccess(message: string, returnFocus: string) { setToast({ id: ++toastId.current, message, returnFocus }) }
  const editButton = useRef<HTMLButtonElement>(null)
  const activityVersion = usePostVersion("list")
  // Keep the screen mounted during credential replacement, but never reuse an old response.
  useEffect(() => {
    if (memberRequests.paused) return
    const session = captureMemberRequest(memberId), controller = new AbortController()
    const valid = () => !controller.signal.aborted && scope.isActive() && session.isCurrent()
    getProfile(memberId, controller.signal).then(data => { if (valid()) { setProfile({ data }); session.updateNickname(data.nickname) } })
      .catch(error => { if (valid()) { if (error instanceof MemberApiError && error.status === 401) session.expire(); else setProfile({ error: message(error) }) } })
    return () => controller.abort()
  }, [memberId, profileAttempt, scope, captureMemberRequest, memberRequests])
  useEffect(() => {
    if (memberRequests.paused) return
    const session = captureMemberRequest(memberId), controller = new AbortController()
    const requestedVersion = getPostVersion("list")
    const valid = () => !controller.signal.aborted && scope.isActive() && session.isCurrent()
    getActivitySummary(controller.signal).then(data => { if (valid() && requestedVersion === getPostVersion("list")) setSummary({ data }) })
      .catch(error => { if (valid()) { if (error instanceof MemberApiError && error.status === 401) session.expire(); else setSummary({ error: message(error) }) } })
    return () => controller.abort()
  }, [memberId, summaryAttempt, scope, captureMemberRequest, memberRequests, activityVersion])
  function closeEditor() { setEditing(false); editButton.current?.focus({ preventScroll: true }) }
  return <>
    <section className="mypage-profile" aria-label="프로필">
      {!profile.data ? profile.error ? <div role="alert"><p>{profile.error}</p><button className="iyum-button iyum-button-outline" onClick={() => { setProfile({}); setProfileAttempt(n => n+1) }}>회원 정보 다시 시도</button></div> : <p role="status">회원 정보를 불러오고 있습니다.</p> : <>
        <div className="mypage-identity"><FeedAvatar /><div className="mypage-name"><h2>{profile.data.nickname}</h2><p><time dateTime={profile.data.createdAt}>{joinedMonth(profile.data.createdAt)}</time> 가입</p></div><button ref={editButton} className="iyum-button iyum-button-outline" onClick={() => { dismissToast(); setEditing(true) }} aria-expanded={editing} aria-controls="nickname-editor">프로필 수정</button></div>
        {editing && <NicknameEditor key={profile.data.nickname} profile={profile.data} onCancel={closeEditor} onSaved={data => { setProfile({ data }); closeEditor(); showSuccess('닉네임을 변경했습니다.', '.mypage-identity > button') }} />}
      </>}
      <div className="mypage-counts" aria-label="활동 요약">
        {summary.data ? <><div><span>내 게시글</span><strong>{summary.data.postCount.toLocaleString('ko-KR')}</strong></div><div><span>내 댓글</span><strong>{summary.data.commentCount.toLocaleString('ko-KR')}</strong></div></> : summary.error ? <div className="mypage-summary-error" role="alert"><p>{summary.error}</p><button className="iyum-button iyum-button-outline" onClick={() => { setSummary({}); setSummaryAttempt(n => n+1) }}>활동 개수 다시 시도</button></div> : <p role="status">활동 개수를 불러오고 있습니다.</p>}
      </div>
    </section>
    {toast && <SuccessToast key={toast.id} message={toast.message} returnFocus={toast.returnFocus} onDismiss={dismissToast} />}
    <MyActivity memberId={memberId} summary={summary.data} layoutReady={!!(profile.data || profile.error) && !!(summary.data || summary.error)} />
    <section className="mypage-account" aria-labelledby="account-title">
      <h2 id="account-title">계정 관리</h2>
      <div className="mypage-account-card">
        <dl><dt>이메일</dt><dd>{profile.data?.email ?? (profile.error?'회원 정보를 확인해 주세요.':'불러오는 중…')}</dd><dd className="mypage-readonly">읽기 전용</dd></dl>
        <button id="password-change-entry" type="button" className="password-change-entry" aria-label="비밀번호 변경" onClick={() => { onPasswordNotice(''); dismissToast(); setChangingPassword(true) }}>
          <span>비밀번호</span><span className="mypage-account-action">변경<svg aria-hidden="true" focusable="false" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round"><path d="m9 5 7 7-7 7"/></svg></span>
        </button>
      </div>
      <button id="withdrawal-entry" type="button" className="withdrawal-entry" onClick={() => { dismissToast(); setWithdrawing(true) }}><span>회원 탈퇴</span><svg aria-hidden="true" focusable="false" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round"><path d="m9 5 7 7-7 7"/></svg></button>
    </section>
    {changingPassword && <PasswordChangeDialog memberId={memberId} onClose={completed => { setChangingPassword(false); if (completed && scope.isActive()) showSuccess('비밀번호가 변경되었습니다.', '#password-change-entry') }} onNotice={onPasswordNotice} />}
    {withdrawing && <WithdrawalDialog memberId={memberId} onClose={() => setWithdrawing(false)} />}
  </>
}
function NicknameEditor({ profile, onCancel, onSaved }: { profile: Profile; onCancel: () => void; onSaved: (p: Profile) => void }) {
  const auth = useAuth(), scope = useNavigationRequest(), input = useRef<HTMLInputElement>(null)
  const [value, setValue] = useState(profile.nickname), [field, setField] = useState(''), [error, setError] = useState(''), [saving, setSaving] = useState(false)
  const busy = useRef(false)
  useEffect(() => { if (field && !saving) input.current?.focus({ preventScroll: true }) }, [field, saving])
  async function submit(event: FormEvent) {
    event.preventDefault(); if (busy.current) return
    const invalid = nicknameError(value);setField(invalid);setError('')
    if (invalid) { input.current?.focus();return }
    if (normalizeNickname(value) === profile.nickname) return
    const session=auth.captureMemberRequest(profile.memberId);if(!session.isCurrent())return
    busy.current=true;setSaving(true);const navigationCurrent=scope.begin()
    const valid=()=>navigationCurrent()&&session.isCurrent()
    try {
      const csrf=await getMemberCsrf();if(!valid())return
      const data=await updateNickname(profile.memberId,value,csrf);if(!valid())return
      if(session.updateNickname(data.nickname)) onSaved(data)
    } catch(e) {
      if(!valid())return
      if(e instanceof MemberApiError && e.status===401) { session.expire();return }
      if(e instanceof MemberApiError && e.field) { setField(e.field);input.current?.focus() } else setError(message(e))
    } finally { busy.current=false;if(navigationCurrent())setSaving(false) }
  }
  function close() { if (!busy.current) onCancel() }
  return <OverlayDialog titleId="nickname-title" className="mypage-edit-dialog" initialFocus="#my-nickname" returnFocus=".mypage-identity > button" onClose={close}>
    <div className="mypage-edit-heading"><h2 id="nickname-title">닉네임 수정</h2><button type="button" className="iyum-overlay-close" aria-label="닉네임 수정 닫기" aria-disabled={saving} onClick={close}>×</button></div>
    <form id="nickname-editor" className="mypage-editor" onSubmit={submit} noValidate aria-busy={saving}><label className="auth-label" htmlFor="my-nickname">닉네임</label><p id="nickname-help">한글·영문·숫자·밑줄 2~20자. 닉네임만 변경할 수 있어요.</p><input ref={input} id="my-nickname" name="nickname" className="auth-input" value={value} disabled={saving} autoComplete="nickname" aria-invalid={!!field} aria-describedby={`nickname-help${field?' nickname-error':''}`} onChange={e=>{setValue(e.target.value);setField('');setError('')}} /><FieldError id="nickname-error" msg={field} />{error&&<p role="alert" className="auth-field-error">{error}</p>}<div className="mypage-editor-actions"><button className="iyum-button iyum-button-outline" type="button" disabled={saving} onClick={close}>취소</button><button className="iyum-button iyum-button-primary" disabled={saving||normalizeNickname(value)===profile.nickname}>{saving?'저장 중…':'저장'}</button></div>{saving&&<p role="status">닉네임을 저장하고 있습니다.</p>}</form></OverlayDialog>
}
