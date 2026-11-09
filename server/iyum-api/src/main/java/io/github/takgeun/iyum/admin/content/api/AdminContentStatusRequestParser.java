package io.github.takgeun.iyum.admin.content.api;

import io.github.takgeun.iyum.admin.content.application.AdminCommentStatus;
import io.github.takgeun.iyum.admin.content.application.AdminContentReadException;
import io.github.takgeun.iyum.admin.content.application.AdminPostStatus;
import jakarta.annotation.Nullable;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 관리자 콘텐츠 조회의 상태 파라미터를 해석한다.
 *
 * status: 기존 단일 상태 파라미터
 * statuses: 새 다중 상태 파라미터
 *
 * HTTP 요청의 표현 방식은 처리하고,
 * 상태 집합의 정규화는 기존 enum의 정책을 사용한다.
 */
final class AdminContentStatusRequestParser {

    public AdminContentStatusRequestParser() {
    }

    static Set<AdminPostStatus> postStatuses(
            // MultivalueMap을 사용하면 파라미터의 생략 여부, 빈 값, 반복 전달을 구분할 수 있음.
            MultiValueMap<String, String> parameters
    ) {
        requireExclusiveParameters(parameters);

        if(parameters.containsKey("statuses")) {
            return AdminPostStatus.normalizeStatuses(
                    readMultiple(
                            parameters.get("statuses"),
                            AdminPostStatus.class
                    )
            );
        }

        return AdminPostStatus.fromSingleStatus(
                readSingle(
                        parameters.get("status"),
                        AdminPostStatus.class
                )
        );
    }

    static Set<AdminCommentStatus> commentStatuses(
            MultiValueMap<String, String> parameters
    ) {
        requireExclusiveParameters(parameters);

        if (parameters.containsKey("statuses")) {
            return AdminCommentStatus.normalizeStatuses(
                    readMultiple(
                            parameters.get("statuses"),
                            AdminCommentStatus.class
                    )
            );
        }

        return AdminCommentStatus.fromSingleStatus(
                readSingle(
                        parameters.get("status"),
                        AdminCommentStatus.class
                )
        );
    }

    private static void requireExclusiveParameters(
            MultiValueMap<String, String> parameters
    ) {
        // 값이 비어 있거나 ALL이어도 두 이름이 함께 있으면 거부한다.
        if(parameters.containsKey("status")
        && parameters.containsKey("statuses")) {
            throw invalidParameter();
        }
    }

    @Nullable
    private static <E extends Enum<E>> E readSingle(
            @Nullable List<String> values,
            Class<E> enumType
    ) {
        // 기존 단일 파라미터를 생략한 경우.
        if (values == null) {
            return null;
        }

        // status는 단일 선택이므로 반복 전달을 허용하지 않는다.
        if (values.size() != 1 || values.getFirst() == null) {
            throw invalidParameter();
        }

        // 기존 enum 변환 방식처럼 앞뒤 공백을 제거한다.
        String value = values.getFirst().trim();

        // 기존 status= 요청의 전체 조회 동작을 유지한다.
        if (value.isEmpty()) {
            return null;
        }

        return parseEnum(value, enumType);
    }

    private static <E extends Enum<E>> List<E> readMultiple(
            @Nullable List<String> values,
            Class<E> enumType
    ) {
        if(values == null || values.isEmpty()) {
            throw invalidParameter();
        }

        List<E> result = new ArrayList<>();

        for (String value : values) {
            // 다중 파라미터는 값을 보정하지 않고 정확한 enum 코드만 받는다.
            // 빈 문자열, 공백, 쉼표로 연결된 값은 parseEnum에서 거부된다.
            result.add(parseEnum(value, enumType));
        }

        return result;
    }

    private static <E extends Enum<E>> E parseEnum(
            @Nullable String value,
            Class<E> enumType
    ) {
        if(value == null) {
            throw invalidParameter();
        }

        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException exception) {
            throw invalidParameter();
        }
    }

    private static AdminContentReadException invalidParameter() {
        return new AdminContentReadException(
                AdminContentReadException.Code.INVALID_REQUEST_PARAMETER
        );
    }
}
