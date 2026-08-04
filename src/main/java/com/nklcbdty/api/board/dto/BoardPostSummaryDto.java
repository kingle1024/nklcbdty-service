package com.nklcbdty.api.board.dto;

import java.time.LocalDateTime;

import com.nklcbdty.api.board.vo.BoardPost;

/** 목록용. 본문은 담지 않는다. */
public record BoardPostSummaryDto(
    Long id,
    String title,
    String authorName,
    int viewCount,
    int commentCount,
    LocalDateTime insertDts
) {
    public static BoardPostSummaryDto of(BoardPost post) {
        return new BoardPostSummaryDto(
            post.getId(),
            post.getTitle(),
            post.getAuthorName(),
            post.getViewCount(),
            post.getCommentCount(),
            post.getInsertDts()
        );
    }
}
