package com.nklcbdty.api.auth.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.auth.dto.LoginRequest;
import com.nklcbdty.api.auth.dto.SignupRequest;
import com.nklcbdty.api.auth.service.LocalAuthService;

import lombok.extern.slf4j.Slf4j;

/**
 * 자체 회원가입/로그인 API. 카카오 로그인(/api/kakaoLogin)과 함께 쓰이며 응답 형태를 맞췄다.
 * (프론트 로그인 화면: 이메일+비밀번호 폼 → /api/auth/login, "카카오로 로그인" 버튼 → 기존 /api/kakaoLogin)
 *
 * - POST /api/auth/signup       : 회원가입(성공 시 곧바로 토큰 발급 = 자동 로그인)
 * - POST /api/auth/login        : 로그인
 * - GET  /api/auth/email-exists : 회원가입 화면의 이메일 중복 확인
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
public class LocalAuthController {

    private final LocalAuthService localAuthService;

    public LocalAuthController(LocalAuthService localAuthService) {
        this.localAuthService = localAuthService;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest request) {
        try {
            LocalAuthService.AuthResult result =
                localAuthService.signup(request.getEmail(), request.getPassword(), request.getNickname());
            return ResponseEntity.ok(toBody(result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            LocalAuthService.AuthResult result =
                localAuthService.login(request.getEmail(), request.getPassword());
            return ResponseEntity.ok(toBody(result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @GetMapping("/email-exists")
    public ResponseEntity<?> emailExists(@RequestParam String email) {
        return ResponseEntity.ok(Map.of("exists", localAuthService.emailExists(email)));
    }

    /** kakaoLogin 응답과 같은 키 구성(token/refreshToken/userId/nickname) */
    private Map<String, Object> toBody(LocalAuthService.AuthResult result) {
        return Map.of(
            "token", result.token(),
            "refreshToken", result.refreshToken(),
            "userId", result.userId(),
            "nickname", result.nickname()
        );
    }
}
