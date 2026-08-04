package com.nklcbdty.api.board.exception;

/** 작성자가 아닌 사용자가 수정/삭제를 시도한 경우. 컨트롤러에서 403 으로 변환한다. */
public class BoardForbiddenException extends RuntimeException {
    public BoardForbiddenException(String message) {
        super(message);
    }
}
