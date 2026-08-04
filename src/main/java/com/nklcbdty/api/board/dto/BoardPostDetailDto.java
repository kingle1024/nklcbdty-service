package com.nklcbdty.api.board.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.nklcbdty.api.board.vo.BoardPost;

/**
 * 상세 응답. mine 이 true 면 프론트에서 수정/삭제 버튼을 보여준다.
 * (서버도 수정/삭제 시 작성자를 다시 확인한다 — 이 값은 화면 표시용)
 */
public record BoardPostDetailDto(
    Long id,
    String title,
    String content,
    String authorName,
    int viewCount,
    int commentCount,
    LocalDateTime insertDts,
    LocalDateTime updateDts,
    boolean mine,
    List<BoardCommentDto> comments
) {
    public static BoardPostDetailDto of(BoardPost post, List<BoardCommentDto> comments, String viewerId) {
        return new BoardPostDetailDto(
            post.getId(),
            post.getTitle(),
            post.getContent(),
            post.getAuthorName(),
            post.getViewCount(),
            post.getCommentCount(),
            post.getInsertDts(),
            post.getUpdateDts(),
            viewerId != null && viewerId.equals(post.getAuthorId()),
            comments
        );
    }
}
