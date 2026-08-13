package com.nklcbdty.api.board.dto;

import lombok.Data;

/** 댓글 작성 요청 */
@Data
public class BoardCommentCreateRequest {

    private String content;

    /** 익명 작성 시 필수 */
    private String authorName;

    /** 익명 작성 시 필수 */
    private String password;
}
