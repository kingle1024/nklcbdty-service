package com.nklcbdty.api.board.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.nklcbdty.api.board.vo.BoardPost;

import lombok.Builder;
import lombok.Getter;

/** 상세 화면용 게시글. 본문과 댓글 목록을 함께 담는다. */
@Builder
@Getter
public class BoardPostDetailDto {

    private Long id;
    private String boardType;
    private String title;
    private String content;
    private String authorName;
    private int viewCount;
    private boolean pinned;
    private boolean writtenByAdmin;
    /** 익명 글(비밀번호로 수정/삭제하는 글)인지 — 프론트에서 비밀번호 입력창 노출 판단용 */
    private boolean passwordProtected;
    private LocalDateTime insertDts;
    private LocalDateTime updateDts;
    private List<BoardCommentDto> comments;

    public static BoardPostDetailDto from(BoardPost post, int viewCount, List<BoardCommentDto> comments) {
        return BoardPostDetailDto.builder()
            .id(post.getId())
            .boardType(post.getBoardType().name())
            .title(post.getTitle())
            .content(post.getContent())
            .authorName(post.getAuthorName())
            .viewCount(viewCount)
            .pinned(post.isPinned())
            .writtenByAdmin(post.getAdminAuthor() != null)
            .passwordProtected(post.getPasswordHash() != null)
            .insertDts(post.getInsertDts())
            .updateDts(post.getUpdateDts())
            .comments(comments)
            .build();
    }
}
