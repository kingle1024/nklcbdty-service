package com.nklcbdty.api.board.dto;

import lombok.Data;

/** 글 작성/수정 요청 */
@Data
public class BoardPostRequest {
    private String title;
    private String content;
}
