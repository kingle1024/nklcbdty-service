package com.nklcbdty.api.board.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import com.nklcbdty.api.board.vo.BoardPost;

/** 목록 + 페이징 정보. Page 를 그대로 내보내면 응답 구조가 스프링 구현에 종속되므로 필요한 값만 담는다. */
public record BoardPostPageDto(
    List<BoardPostSummaryDto> items,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public static BoardPostPageDto of(Page<BoardPost> page) {
        return new BoardPostPageDto(
            page.getContent().stream().map(BoardPostSummaryDto::of).toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}
