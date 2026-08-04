package com.nklcbdty.api.board.dto;

import java.time.LocalDateTime;

import com.nklcbdty.api.board.vo.BoardComment;

/**
 * 댓글 응답. authorId 는 내보내지 않고 "내 댓글인지"(mine) 만 알려준다.
 */
public record BoardCommentDto(
    Long id,
    String content,
    String authorName,
    LocalDateTime insertDts,
    boolean mine
) {
    public static BoardCommentDto of(BoardComment comment, String viewerId) {
        return new BoardCommentDto(
            comment.getId(),
            comment.getContent(),
            comment.getAuthorName(),
            comment.getInsertDts(),
            viewerId != null && viewerId.equals(comment.getAuthorId())
        );
    }
}
