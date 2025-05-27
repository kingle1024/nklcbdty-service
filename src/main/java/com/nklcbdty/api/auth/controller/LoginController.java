package com.nklcbdty.api.auth.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.auth.dto.RefreshRequest;
import com.nklcbdty.api.auth.dto.TokenResponse;
import com.nklcbdty.api.auth.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class LoginController {
    private final AuthService authService;

    @Autowired
    public LoginController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest request) {

        try {
            TokenResponse token = authService.refreshAccessToken(request.getUserVo().getUserId(), request.getRefreshToken());
            return ResponseEntity.ok(token);
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }
}
