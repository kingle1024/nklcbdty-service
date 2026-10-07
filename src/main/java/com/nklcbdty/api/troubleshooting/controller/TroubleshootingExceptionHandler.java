package com.nklcbdty.api.troubleshooting.controller;

import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 트러블슈팅 API 전용 예외 처리. 다른 모듈에 영향이 없도록 troubleshooting 패키지로 범위를 한정한다
 * (게시판·경력 advice 와 같은 방식, 같은 응답 모양).
 */
@RestControllerAdvice(basePackages = "com.nklcbdty.api.troubleshooting")
public class TroubleshootingExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(error(ex.getMessage()));
    }

    private Map<String, String> error(String message) {
        return Map.of("status", "error", "message", message == null ? "요청을 처리할 수 없습니다." : message);
    }
}
