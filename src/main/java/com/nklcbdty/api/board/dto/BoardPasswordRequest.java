package com.nklcbdty.api.board.dto;

import lombok.Data;

/** 익명 글/댓글 삭제 시 비밀번호만 담아 보내는 요청 본문 */
@Data
public class BoardPasswordRequest {

    private String password;
}
