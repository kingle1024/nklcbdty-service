package com.nklcbdty.api.board.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/** 게시글 목록 페이지 응답 */
@Builder
@Getter
public class BoardPageResponse {

    private List<BoardPostSummaryDto> rows;
    private long totalElements;
    private int totalPages;
    private int pageNumber;
    private int pageSize;
}
