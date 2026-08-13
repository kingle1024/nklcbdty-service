package com.nklcbdty.api.board.dto;

import java.time.LocalDateTime;

import com.nklcbdty.api.board.vo.BoardComment;

import lombok.Builder;
import lombok.Getter;

/** 댓글 응답 */
@Builder
@Getter
public class BoardCommentDto {

    private Long id;
    private Long postId;
    private String content;
    private String authorName;
    private boolean writtenByAdmin;
    /** 익명 댓글(비밀번호로 수정/삭제)인지 */
    private boolean passwordProtected;
    private LocalDateTime insertDts;
    private LocalDateTime updateDts;

    public static BoardCommentDto from(BoardComment comment) {
        return BoardCommentDto.builder()
            .id(comment.getId())
            .postId(comment.getPostId())
            .content(comment.getContent())
            .authorName(comment.getAuthorName())
            .writtenByAdmin(comment.getAdminAuthor() != null)
            .passwordProtected(comment.getPasswordHash() != null)
            .insertDts(comment.getInsertDts())
            .updateDts(comment.getUpdateDts())
            .build();
    }
}
