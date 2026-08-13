package com.nklcbdty.api.board.dto;

import lombok.Data;

/** 댓글 수정 요청 */
@Data
public class BoardCommentUpdateRequest {

    private String content;

    /** 익명 댓글을 수정할 때 필요한 비밀번호 */
    private String password;
}
