package com.nklcbdty.api.admin.auth.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.admin.auth.dto.AdminLoginRequest;
import com.nklcbdty.api.admin.auth.service.AdminAuthService;

import lombok.extern.slf4j.Slf4j;

/**
 * 관리자 로그인.
 * - POST /api/admin/login : username/password 검증 후 관리자 JWT 발급
 * (이 경로만 인증 없이 접근 가능. 나머지 /api/admin/** 은 AuthFilter 에서 관리자 토큰 필요)
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AdminLoginRequest request) {
        try {
            AdminAuthService.LoginResult result = adminAuthService.login(request.getUsername(), request.getPassword());
            return ResponseEntity.ok(Map.of(
                "token", result.getToken(),
                "username", result.getUsername(),
                "displayName", result.getDisplayName()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
