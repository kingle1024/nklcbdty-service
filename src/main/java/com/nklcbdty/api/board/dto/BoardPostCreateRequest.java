package com.nklcbdty.api.board.dto;

import lombok.Data;

/** 게시글 작성 요청 */
@Data
public class BoardPostCreateRequest {

    private String title;

    private String content;

    /** 표시할 작성자 이름. 익명 작성 시 필수, 로그인/관리자면 생략 가능 */
    private String authorName;

    /** 익명 작성 시 필수. 나중에 수정/삭제할 때 쓰는 비밀번호 */
    private String password;

    /** 상단 고정 여부. 관리자만 true 로 지정할 수 있다. */
    private boolean pinned;
}
