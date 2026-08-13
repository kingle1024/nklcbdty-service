package com.nklcbdty.api.board.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.nklcbdty.api.board.exception.BoardAccessDeniedException;
import com.nklcbdty.api.board.exception.BoardNotFoundException;

/**
 * 게시판 API 전용 예외 처리. 다른 모듈에 영향이 없도록 board 패키지로 범위를 한정한다.
 * 응답 형태는 기존 API 와 동일하게 {"status":"error","message":"..."}.
 */
@RestControllerAdvice(basePackages = "com.nklcbdty.api.board")
public class BoardExceptionHandler {

    @ExceptionHandler(BoardNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(BoardNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error(ex.getMessage()));
    }

    @ExceptionHandler(BoardAccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(BoardAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(error(ex.getMessage()));
    }

    private Map<String, String> error(String message) {
        return Map.of("status", "error", "message", message == null ? "요청을 처리할 수 없습니다." : message);
    }
}
