import { type OperationLog, LOG_ACTIONS } from "@/lib/admin-operation-logs"
import { MODERATION_REASONS } from "@/lib/moderation-reasons"
import { writtenTime } from "@/lib/written-time"
import AdminCommentPreview from "./AdminCommentPreview"
import AdminPostPreview from "./AdminPostPreview"
export default function AdminLogPreview({log,returnHref,onManage,onParent}:{log:OperationLog;returnHref:string;onManage:()=>void;onParent:(postId:number)=>void}) {
  return <>
    <section aria-label="당시 운영 조치"><h3>당시 운영 조치</h3>
    <dl className="admin-log-fields admin-log-summary-compact">
      <div><dt>대상</dt><dd>{log.targetType==="POST"?"게시글":"댓글"} #{log.targetId}</dd></div>
      <div><dt>조치</dt><dd><span className={`admin-badge ${log.action==="HIDE"?"is-hidden":"is-visible"}`}>{LOG_ACTIONS[log.action]}</span></dd></div>
      <div><dt>처리 시각 · 한국 시간</dt><dd><time dateTime={log.createdAt}>{writtenTime(log.createdAt).exact.replace(" (한국 시간)", "")}</time></dd></div>
      <div><dt>처리 관리자</dt><dd>회원 #{log.actorMemberId}</dd></div>
      {(log.reasonType==="LEGACY"||log.reason!==MODERATION_REASONS[log.reasonType])&&<div><dt>사유 유형</dt><dd>{MODERATION_REASONS[log.reasonType]}</dd></div>}
      <div><dt>처리 사유</dt><dd className="admin-reason-text" tabIndex={0}>{log.reason}</dd></div>
      {log.reasonDetail!==null&&log.reasonDetail!==log.reason&&<div><dt>상세 사유</dt><dd className="admin-reason-text" tabIndex={0}>{log.reasonDetail}</dd></div>}
    </dl></section>
    <section className="admin-log-current" aria-label="현재 콘텐츠">
      <h3>현재 {log.targetType==="POST"?"게시글":"댓글"}</h3>
      <p className="admin-preview-help">조회한 현재 내용이며 조치 당시와 다를 수 있습니다.</p>
      {log.targetType==="POST"?<AdminPostPreview key={log.logId} postId={log.targetId} returnHref={returnHref} readOnly showHeading={false} onManage={onManage}/>:<AdminCommentPreview key={log.logId} commentId={log.targetId} showHeading={false} onManage={onManage} onParent={onParent}/>}
    </section>
  </>
}
