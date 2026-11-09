export const MODERATION_REASONS = {
  SPAM_ADVERTISING: "광고·홍보·스팸", ABUSE_HATE: "욕설·비방·혐오",
  PERSONAL_INFORMATION: "개인정보 노출", DUPLICATE_FLOODING: "도배·중복 게시",
  OFF_TOPIC: "게시판 주제와 무관", MODERATION_ERROR: "운영 조치 오류",
  NO_VIOLATION: "재검토 결과 위반 없음", OTHER: "기타", LEGACY: "기존 자유입력 사유",
} as const
export type ModerationReasonType = keyof typeof MODERATION_REASONS
export function isModerationReasonType(value: unknown): value is ModerationReasonType {
  return typeof value === "string" && Object.prototype.hasOwnProperty.call(MODERATION_REASONS, value)
}
export const ACTION_REASONS: Record<"hide" | "restore", readonly ModerationReasonType[]> = {
  hide: ["SPAM_ADVERTISING", "ABUSE_HATE", "PERSONAL_INFORMATION", "DUPLICATE_FLOODING", "OFF_TOPIC", "OTHER"],
  restore: ["MODERATION_ERROR", "NO_VIOLATION", "OTHER"],
}
