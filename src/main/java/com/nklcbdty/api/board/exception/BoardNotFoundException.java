package com.nklcbdty.api.board.exception;

/** 글/댓글이 없거나 이미 삭제된 경우. 컨트롤러에서 404 로 변환한다. */
public class BoardNotFoundException extends RuntimeException {
    public BoardNotFoundException(String message) {
        super(message);
    }
}
