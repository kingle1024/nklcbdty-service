package com.nklcbdty.api.board.dto;

import lombok.Data;

/** 게시글 수정 요청 */
@Data
public class BoardPostUpdateRequest {

    private String title;

    private String content;

    /** 익명 글을 수정할 때 필요한 비밀번호 */
    private String password;

    /** 상단 고정 여부. 관리자 요청일 때만 반영된다. null 이면 기존 값 유지 */
    private Boolean pinned;
}
