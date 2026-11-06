"use client"
import { useCallback, useEffect, useLayoutEffect, useRef, useState, useSyncExternalStore, type FormEvent } from 'react'
import Link from '@/components/AppLink'
import PasswordChangeDialog from '@/components/member/PasswordChangeDialog'
import WithdrawalDialog from '@/components/member/WithdrawalDialog'
import CommunityDialog from '@/components/community/CommunityDialog'
import MyActivity from '@/components/member/MyActivity'
import SuccessToast from '@/components/member/SuccessToast'
import { usePostVersion, getPostVersion } from '@/lib/community-mutations'
import OverlayDialog from '@/components/OverlayDialog'
import { useAuth } from '@/components/AuthProvider'
import { useNavigationGuard, useUnsavedChanges, useNavigationRequest } from '@/components/NavigationGuard'
import { FieldError } from '@/components/auth/AuthFields'
import { FeedAvatar } from '@/components/community/FeedNavigation'
import { requestListScrollRestore } from '@/lib/community-posts'
import { safeReturnPath } from '@/lib/navigation'
import { useRouter } from 'next/navigation'
import CommentAuthDialog from '@/components/community/CommentAuthDialog'
import { InlineCommentAuthNotice } from '@/components/member/InlineCommentAuthNotice'
import { getProfile, getActivitySummary, getMemberCsrf, updateNickname, nicknameError, normalizeNickname, joinedMonth, MemberApiError, type Profile, type ActivitySummary } from '@/lib/member-api'
const message = (error: unknown) => error instanceof Error ? error.message : '정보를 확인하지 못했습니다. 다시 시도해 주세요.'
type Resource<T> = { data?: T; error?: string }
function LoginRequired({ settings }: { settings: boolean }) { const loginHref = `/login?from=${encodeURIComponent(safeReturnPath(settings ? '/mypage/settings' : '/mypage'))}`; return <div className="mypage-message mypage-login-card"><FeedAvatar /><h2>로그인이 필요해요</h2><p>로그인하고 내 프로필과 활동을 확인해 보세요.</p><Link className="iyum-button iyum-button-primary" href={loginHref}>로그인하기</Link><Link className="mypage-community-link" href="/community">커뮤니티 둘러보기</Link></div> }
function DraftProtection() {
  const { commentDrafts, commentCreated } = useAuth()
  const drafts = useSyncExternalStore(commentDrafts.subscribe, commentDrafts.snapshot, commentDrafts.snapshot)
  const blocker = useUnsavedChanges(Object.values(drafts).some(Boolean))
  const { subscribeNavigation } = useNavigationGuard()
  useEffect(() => subscribeNavigation(() => { commentDrafts.clear(); commentCreated.clear() }), [subscribeNavigation, commentDrafts, commentCreated])
  return blocker.state === 'blocked' ? <CommunityDialog titleId="inline-comment-leave-title" onCancel={blocker.reset}>
    <h2 id="inline-comment-leave-title">작성 중인 댓글을 두고 나갈까요?</h2>
    <p>화면의 입력은 보관되지 않습니다. 이미 전송한 요청은 서버에서 처리될 수 있습니다.</p>
    <div className="community-dialog-actions"><button data-initial-focus className="iyum-button iyum-button-outline" onClick={blocker.reset}>계속 작성</button><button className="iyum-button iyum-button-primary" onClick={blocker.proceed}>나가기</button></div>
  </CommunityDialog> : null
}
export default function MyPage({ settings = false }: { settings?: boolean }) {
  const auth = useAuth()
  const router = useRouter(), guard = useNavigationGuard(), scope = useNavigationRequest()
  const { subscribeNavigation } = guard
  const [commentExpired, setCommentExpired] = useState(false)
  const commentFocus = useRef(false)
  useLayoutEffect(() => {
    if (!commentExpired && commentFocus.current) {
      commentFocus.current = false
      if (scope.isActive()) document.getElementById('mypage-title')?.focus({preventScroll:true})
    }
  }, [commentExpired, scope])
  if (commentExpired && (auth.status === 'authenticated' || auth.pending === 'login')) setCommentExpired(false)
  useEffect(() => subscribeNavigation(() => setCommentExpired(false)), [subscribeNavigation])
  const closeCommentNotice = () => {
    setCommentExpired(false)
    commentFocus.current = true
  }
  const [passwordNotice, setPasswordNotice] = useState('')
  return <InlineCommentAuthNotice.Provider value={() => { if(scope.isActive()) setCommentExpired(true) }}><main id="main-content" className={`mypage${settings ? ' mypage-settings' : ''}`}><header className="mypage-heading">{settings && <Link className="mypage-back" href="/mypage" scroll={false} prefetch={false} onNavigate={() => requestListScrollRestore('/mypage')}>← 마이페이지로 돌아가기</Link>}<h1 id="mypage-title" tabIndex={-1}>{settings ? '프로필·계정 설정' : '마이페이지'}</h1><p>{settings ? '프로필과 계정 보안을 관리하세요.' : '내가 나눈 이야기와 활동을 한곳에서 확인해요.'}</p></header>
    {!settings && <DraftProtection />}
    {passwordNotice && <p role="status" className="mypage-notice has-notice">{passwordNotice}</p>}
    {auth.status === 'checking' || auth.pending ? <p role="status">회원 상태를 확인하고 있습니다.</p>
      : auth.status === 'error' ? <div className="mypage-message" role="alert"><p>로그인 상태를 확인하지 못했어요.</p><button className="iyum-button iyum-button-outline" onClick={() => void auth.refresh()}>로그인 상태 다시 확인</button></div>
      : auth.status !== 'authenticated' || !auth.member ? <LoginRequired settings={settings} /> : <MemberPage settings={settings} key={`${auth.member.memberId}:${settings}`} memberId={auth.member.memberId} onPasswordNotice={setPasswordNotice} />}
    {commentExpired && auth.status === 'anonymous' && !auth.pending && scope.isActive() && !guard.pending && <CommentAuthDialog
      idPrefix="inline-comment-expired" action="create" hasDraft={false} status={auth.status} pending={false} dismissLabel="닫기"
      onClose={closeCommentNotice} onRetry={() => void auth.refresh()}
      onLogin={() => { if(!scope.isActive() || auth.status!=='anonymous' || auth.pending)return; setCommentExpired(false); guard.request(() => router.push(`/login?from=${encodeURIComponent(safeReturnPath('/mypage'))}`)) }} />}
  </main></InlineCommentAuthNotice.Provider>
}
function MemberPage({ memberId, onPasswordNotice, settings }: { settings: boolean; memberId: number; onPasswordNotice: (message: string) => void }) {
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
  const activityLeaving = useRef<(() => void) | null>(null)
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
    if (settings) return
    const requestedVersion = getPostVersion("list")
    const valid = () => !controller.signal.aborted && scope.isActive() && session.isCurrent()
    getActivitySummary(controller.signal).then(data => { if (valid() && requestedVersion === getPostVersion("list")) setSummary({ data }) })
      .catch(error => { if (valid()) { if (error instanceof MemberApiError && error.status === 401) session.expire(); else setSummary({ error: message(error) }) } })
    return () => controller.abort()
  }, [memberId, summaryAttempt, scope, captureMemberRequest, memberRequests, activityVersion, settings])
  function closeEditor() { setEditing(false); editButton.current?.focus({ preventScroll: true }) }
  return <>
    <section className="mypage-profile" aria-label="프로필">
      {!profile.data ? profile.error ? <div role="alert"><p>{profile.error}</p><button className="iyum-button iyum-button-outline" onClick={() => { setProfile({}); setProfileAttempt(n => n+1) }}>회원 정보 다시 시도</button></div> : <p role="status">회원 정보를 불러오고 있습니다.</p> : <>
        <div className="mypage-identity"><FeedAvatar /><div className="mypage-name"><h2>{profile.data.nickname}</h2><p><time dateTime={profile.data.createdAt}>{joinedMonth(profile.data.createdAt)}</time> 가입</p></div>{settings ? <button ref={editButton} className="iyum-button iyum-button-outline" onClick={() => { dismissToast(); setEditing(true) }} aria-expanded={editing} aria-controls="nickname-editor">프로필 수정</button> : <Link className="iyum-button iyum-button-outline" href="/mypage/settings" prefetch={false} onNavigate={() => activityLeaving.current?.()}>프로필·계정 설정</Link>}</div>
        {editing && <NicknameEditor key={profile.data.nickname} profile={profile.data} onCancel={closeEditor} onSaved={data => { setProfile({ data }); closeEditor(); showSuccess('닉네임을 변경했습니다.', '.mypage-identity > button') }} />}
      </>}
      {!settings && <div className="mypage-counts" aria-label="활동 요약">
        {summary.data ? <><div><span>내 게시글</span><strong>{summary.data.postCount.toLocaleString('ko-KR')}</strong></div><div><span>내 댓글</span><strong>{summary.data.commentCount.toLocaleString('ko-KR')}</strong></div></> : summary.error ? <div className="mypage-summary-error" role="alert"><p>{summary.error}</p><button className="iyum-button iyum-button-outline" onClick={() => { setSummary({}); setSummaryAttempt(n => n+1) }}>활동 개수 다시 시도</button></div> : <p role="status">활동 개수를 불러오고 있습니다.</p>}
      </div>}
    </section>
    {toast && <SuccessToast key={toast.id} message={toast.message} returnFocus={toast.returnFocus} onDismiss={dismissToast} />}
    <div className="mypage-workspace">
    {settings ? <section className="mypage-account" aria-labelledby="account-title">
      <h2 id="account-title">계정·보안</h2>
      <div className="mypage-account-card">
        <dl><dt>이메일</dt><dd>{profile.data?.email ?? (profile.error?'회원 정보를 확인해 주세요.':'불러오는 중…')}</dd>{profile.data && <dd className="mypage-readonly mypage-email-verified">이메일 인증 완료</dd>}</dl>
        <button id="password-change-entry" type="button" className="password-change-entry" aria-label="비밀번호 변경" onClick={() => { onPasswordNotice(''); dismissToast(); setChangingPassword(true) }}>
          <span>비밀번호</span><span className="mypage-account-action">변경<svg aria-hidden="true" focusable="false" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round"><path d="m9 5 7 7-7 7"/></svg></span>
        </button>
      </div>
      <div className="mypage-withdrawal-section"><h3>회원 탈퇴</h3><p>삭제할 글과 댓글이 있다면 탈퇴 전에 정리해 주세요.</p>
      <button id="withdrawal-entry" type="button" className="withdrawal-entry" onClick={() => { dismissToast(); setWithdrawing(true) }}><span>회원 탈퇴</span><svg aria-hidden="true" focusable="false" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round"><path d="m9 5 7 7-7 7"/></svg></button>
      </div>
    </section> : <MyActivity leavingRef={activityLeaving} memberId={memberId} summary={summary.data} layoutReady={!!(profile.data || profile.error) && !!(summary.data || summary.error)} />}
    </div>
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
