package com.nklcbdty.api.board.exception;

/** 작성 권한이 없거나 비밀번호가 틀린 경우 → 403 */
public class BoardAccessDeniedException extends RuntimeException {

    public BoardAccessDeniedException(String message) {
        super(message);
    }
}
