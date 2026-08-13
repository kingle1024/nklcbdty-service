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
    /** 지금 보고 있는 로그인 사용자가 쓴 댓글인지 — 비밀번호 없이 수정/삭제 버튼을 띄울지 판단용 */
    private boolean mine;
    private LocalDateTime insertDts;
    private LocalDateTime updateDts;

    /** viewerId 는 조회하는 로그인 사용자(비로그인이면 null). */
    public static BoardCommentDto from(BoardComment comment, String viewerId) {
        return BoardCommentDto.builder()
            .id(comment.getId())
            .postId(comment.getPostId())
            .content(comment.getContent())
            .authorName(comment.getAuthorName())
            .writtenByAdmin(comment.getAdminAuthor() != null)
            .passwordProtected(comment.getPasswordHash() != null)
            .mine(isMine(comment.getAuthorId(), viewerId))
            .insertDts(comment.getInsertDts())
            .updateDts(comment.getUpdateDts())
            .build();
    }

    /** 로그인 사용자가 쓴 글이고 그 사용자가 보고 있을 때만 true. 익명·관리자 글은 항상 false. */
    static boolean isMine(String authorId, String viewerId) {
        return authorId != null && viewerId != null && authorId.equals(viewerId);
    }
}
