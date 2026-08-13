package com.nklcbdty.api.board.dto;

import java.time.LocalDateTime;

import com.nklcbdty.api.board.vo.BoardPost;

import lombok.Builder;
import lombok.Getter;

/** 목록 화면용 게시글 요약. 본문(content)은 담지 않는다. */
@Builder
@Getter
public class BoardPostSummaryDto {

    private Long id;
    private String boardType;
    private String title;
    private String authorName;
    private int viewCount;
    private long commentCount;
    private boolean pinned;
    /** 관리자가 작성한 글인지(공지 뱃지 표시용) */
    private boolean writtenByAdmin;
    private LocalDateTime insertDts;
    private LocalDateTime updateDts;

    public static BoardPostSummaryDto from(BoardPost post, long commentCount) {
        return BoardPostSummaryDto.builder()
            .id(post.getId())
            .boardType(post.getBoardType().name())
            .title(post.getTitle())
            .authorName(post.getAuthorName())
            .viewCount(post.getViewCount())
            .commentCount(commentCount)
            .pinned(post.isPinned())
            .writtenByAdmin(post.getAdminAuthor() != null)
            .insertDts(post.getInsertDts())
            .updateDts(post.getUpdateDts())
            .build();
    }
}
