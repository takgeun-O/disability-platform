package io.github.takgeun.iyum.community.post.api.dto;

import io.github.takgeun.iyum.community.post.application.PostSummaryResult;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

// 서비스에서 받은 게시글 목록과 페이지 정보를 프론트엔드에 보낼 응답으로 변환하는 DTO
@Schema(description = "게시글 목록과 페이지 정보")
public record PostListResponse(
        List<PostSummaryResponse> items,    // 현재 페이지의 게시글 목록
        int page,   // 현재 페이지 번호
        int size,   // 요청한 페이지 크기
        long totalElements, // 조회 조건에 맞는 전체 게시글 수
        int totalPages, // 전체 페이지 수
        boolean hasNext // 다음 페이지가 있는지 여부
) {

    public static PostListResponse from(
            // Page는 목록과 페이징 정보를 함께 담는 객체
            Page<PostSummaryResult> result
    ) {
        List<PostSummaryResponse> items = result
                .getContent()   // 현재 페이지의 List<PostSummaryResult> 추출
                .stream()
                .map(PostSummaryResponse::from)
                .toList();

        /**
         * {
         *   "items": [
         *     {
         *       "postId": 53,
         *       "title": "보청기 질문입니다"
         *     }
         *   ],
         *   "page": 0,
         *   "size": 20,
         *   "totalElements": 53,
         *   "totalPages": 3,
         *   "hasNext": true
         * }
         */
        return new PostListResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }
}
