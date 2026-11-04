package io.github.takgeun.iyum.admin.operation.application;

/**
 * 운영 기록 조회 시 사용하는 대상 유형.
 * 기존 운영 기록 테이블에 새로운 컬럼을 추가하는 것은 아니다.
 */
public enum AdminOperationLogTargetType {

    ALL,
    POST,
    COMMENT
}
