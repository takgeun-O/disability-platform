import { type OperationLog, LOG_ACTIONS } from "@/lib/admin-operation-logs"
import { MODERATION_REASONS } from "@/lib/moderation-reasons"
import { writtenTime } from "@/lib/written-time"
import AdminPostPreview from "./AdminPostPreview"
export default function AdminLogPreview({log,returnHref}:{log:OperationLog;returnHref:string}) {
  return <>
    <dl className="admin-log-fields">
      <div><dt>기록 ID</dt><dd>#{log.logId}</dd></div>
      <div><dt>처리 시각 · 한국 시간</dt><dd><time dateTime={log.createdAt}>{writtenTime(log.createdAt).exact.replace(" (한국 시간)", "")}</time></dd></div>
      <div><dt>처리 관리자</dt><dd>회원 #{log.actorMemberId}</dd></div>
      <div><dt>대상</dt><dd>{log.targetType==="POST"?"게시글":"댓글"} #{log.targetId}</dd></div>
      <div><dt>조치</dt><dd><span className={`admin-badge ${log.action==="HIDE"?"is-hidden":"is-visible"}`}>{LOG_ACTIONS[log.action]}</span></dd></div>
      <div><dt>사유 유형</dt><dd>{MODERATION_REASONS[log.reasonType]}</dd></div>
      <div><dt>처리 사유</dt><dd className="admin-reason-text" tabIndex={0}>{log.reason}</dd></div>
      {log.reasonDetail!==null&&log.reasonDetail!==log.reason&&<div><dt>상세 사유</dt><dd className="admin-reason-text" tabIndex={0}>{log.reasonDetail}</dd></div>}
    </dl>
    {log.targetType==="POST"?<AdminPostPreview key={log.logId} postId={log.targetId} returnHref={returnHref} readOnly/>:<p className="admin-preview-help">댓글 원문 연결은 현재 제공하지 않습니다.</p>}
  </>
}
