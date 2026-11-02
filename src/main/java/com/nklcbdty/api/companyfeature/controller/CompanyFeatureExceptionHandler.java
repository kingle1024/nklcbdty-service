package com.nklcbdty.api.companyfeature.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 재택/복지 API 전용 예외 처리. 입력 검증 실패(IllegalArgumentException)를 400 으로.
 * 응답 형태는 기존 API 와 동일하게 {"status":"error","message":"..."}.
 */
@RestControllerAdvice(basePackages = "com.nklcbdty.api.companyfeature")
public class CompanyFeatureExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        String message = ex.getMessage() == null ? "요청을 처리할 수 없습니다." : ex.getMessage();
        return ResponseEntity.badRequest().body(Map.of("status", "error", "message", message));
    }
}
