import type { PostTopic } from "./community-posts"

// URL과 서버 enum을 명시적으로 연결한다. 이름을 변환해서 enum을 추정하지 않는다.
export type CommunityTopic = {
  slug: string
  topic: PostTopic
  name: string
  description: string
  image: string
  guidance: string
}
export const COMMUNITY_TOPICS: readonly CommunityTopic[] = [
  {
    slug: "hearing-aid",
    topic: "HEARING_AID",
    name: "보청기",
    description:
      "첫 선택부터 착용과 관리까지, 보청기를 사용하는 일상의 질문과 경험을 나눠요.",
    image: "/images/community/hearing-aid.svg",
    guidance:
      "제품 추천은 사용 환경과 개인 경험을 함께 적어 주세요. 검사·진료와 지원 자격은 담당 기관에 확인해 주세요.",
  },
  {
    slug: "cochlear-implant",
    topic: "COCHLEAR_IMPLANT",
    name: "인공와우",
    description:
      "인공와우를 알아보는 과정부터 적응과 일상까지, 궁금한 점과 경험을 나눠요.",
    image: "/images/community/cochlear-implant.svg",
    guidance:
      "수술·재활 경험은 개인마다 다를 수 있어요. 개인의 경험과 의료진의 진료·상담을 구분해 주세요.",
  },
  {
    slug: "daily",
    topic: "DAILY",
    name: "자유·일상",
    description: "소소한 안부와 새로운 발견, 오늘의 이야기를 편하게 나눠요.",
    image: "/images/community/daily.svg",
    guidance:
      "서로의 생활 방식과 속도를 존중해 주세요. 본인과 다른 사람의 연락처 등 개인정보는 공개하지 말아 주세요.",
  },
  {
    slug: "treatment-rehabilitation",
    topic: "TREATMENT_REHABILITATION",
    name: "치료·재활",
    description:
      "치료와 재활을 준비하며 생긴 질문, 꾸준히 이어가는 과정의 경험을 나눠요.",
    image: "/images/community/treatment-rehabilitation.svg",
    guidance:
      "치료 효과를 모두에게 동일한 사실로 단정하지 말아 주세요. 개인에게 필요한 치료 계획은 전문가와 상담해 주세요.",
  },
  {
    slug: "communication",
    topic: "COMMUNICATION",
    name: "의사소통",
    description:
      "대화와 자막, 서로를 이해하는 방법까지. 나에게 도움이 된 소통 경험을 나눠요.",
    image: "/images/community/communication.svg",
    guidance:
      "서로 다른 소통 방식을 존중해 주세요. 대화 사례를 공유할 때는 상대방을 알아볼 수 있는 정보를 지워 주세요.",
  },
  {
    slug: "employment",
    topic: "EMPLOYMENT",
    name: "취업·직장",
    description:
      "취업 준비부터 직장생활까지, 함께 일하며 겪는 질문과 경험을 나눠요.",
    image: "/images/community/employment.svg",
    guidance:
      "회사·동료의 개인정보와 내부 자료는 보호해 주세요. 채용·제도 정보를 인용한다면 출처와 확인 시점을 함께 적어 주세요.",
  },
  {
    slug: "welfare-life",
    topic: "WELFARE_LIFE",
    name: "복지·생활",
    description:
      "복지 제도를 이해하는 과정과 생활 속 도움이 된 경험을 함께 나눠요.",
    image: "/images/community/welfare-life.svg",
    guidance:
      "지원 조건은 지역과 시점에 따라 달라질 수 있어요. 공식 출처와 개인 경험을 구분하고, 최종 조건은 운영기관에 확인해 주세요.",
  },
]
export const topicBySlug = (slug: string) =>
  COMMUNITY_TOPICS.find((space) => space.slug === slug)
export const topicByValue = (value: string) =>
  COMMUNITY_TOPICS.find((space) => space.topic === value)
export const topicPath = (space: CommunityTopic) =>
  `/community/topics/${space.slug}`
export function topicByPath(pathname: string) {
  const match = /^\/community\/topics\/([a-z-]+)$/.exec(pathname)
  return match ? topicBySlug(match[1]) : undefined
}
export const isPostListPath = (pathname: string) =>
  ["/community", "/community/posts"].includes(pathname) ||
  !!topicByPath(pathname)
