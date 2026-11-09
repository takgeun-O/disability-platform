# 댓글 본문 수정 시각 응답

## 변경 목적

댓글 숨김·복원 등의 운영 조치로 updatedAt이 변경되어도
사용자 화면에서 본문이 수정된 것으로 표시하지 않도록
본문 수정 시각을 contentUpdatedAt으로 분리한다.

## 시각 필드

| 필드 | 의미 |
|---|---|
| createdAt | 댓글 작성 시각 |
| updatedAt | 댓글 엔티티의 마지막 변경 시각 |
| contentUpdatedAt | 마지막 실제 본문 변경 시각. 별도 기록이 없으면 null |

contentUpdatedAt은 ISO 8601 시각 문자열 또는 null로 반환한다.

## 기록 정책

- 새 댓글의 contentUpdatedAt은 null이다.
- 실제 본문이 변경되면 해당 시각을 기록한다.
- 동일한 본문으로 수정 요청하면 기존 값을 유지한다.
- 검증에 실패하면 본문과 기존 본문 수정 시각을 유지한다.
- 숨김·복원·삭제는 본문 수정 시각을 변경하지 않는다.
- 기존 데이터는 updatedAt으로 역산하거나 일괄 채우지 않는다.
- 기존 데이터의 null은 과거에 본문 수정이 전혀 없었다는 뜻이 아니다.

## 조회 응답 정책

### 공개 댓글 조회

조회 가능한 댓글의 contentUpdatedAt을 반환한다.
등록순·최신순·인기순 모두 동일한 필드를 제공한다.

### 마이페이지 내 댓글 조회

- VISIBLE: 저장된 contentUpdatedAt을 반환한다.
- HIDDEN 및 PARENT_HIDDEN: contentUpdatedAt을 null로 반환한다.
- 응답에서 값을 가리더라도 DB에 저장된 시각은 유지한다.

### 관리자 댓글 조회

정상·숨김·삭제 상태와 관계없이 저장된 contentUpdatedAt을 반환한다.
별도로 기록된 값이 없으면 null을 반환한다.

## 프론트 연동 예정

- 댓글의 '수정됨' 표시는 contentUpdatedAt을 기준으로 판단한다.
- contentUpdatedAt이 null이면 '수정됨' 표시를 하지 않는다.
- updatedAt과 createdAt의 비교로 본문 수정 여부를 판단하지 않는다.
- contentUpdatedAt이 null일 때 updatedAt으로 대체하지 않는다.
- 작성 상대 시간은 기존처럼 createdAt을 사용한다.
- 관리자 화면에서는 엔티티 변경 시각과 본문 수정 시각을 구분한다.
- 이번 변경을 게시글의 수정 표시 정책에 일괄 적용하지 않는다.