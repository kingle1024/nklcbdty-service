package com.nklcbdty.api.board.dto;

import lombok.Data;

/** 댓글 작성 요청 */
@Data
public class BoardCommentRequest {
    private String content;
}
