package io.github.takgeun.iyum.admin.operation.application;

/**
 * 운영 기록의 처리 시각 정렬 기준.
 * 정렬 조건을 생략하면 LATEST를 적용한다.
 */
public enum AdminOperationLogSort {

    // 처리 시각 내림차순 -> 운영 기록 ID 내림차순
    LATEST,

    // 처리 시각 오름차순 -> 운영 기록 ID 오름차순
    OLDEST

    // 이렇게 ID까지 같은 방향으로 정렬한 이유는
    // 같은 처리 시각을 가진 기록도 순서가 명확해지고
    // 동일한 데이터상에서 두 정렬이 정확히 역순이 되기 때문에 의도한 것.
}
