package com.nklcbdty.api.admin.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.admin.dto.AdminVisitorPageResponse;
import com.nklcbdty.api.admin.service.AdminVisitorService;

/**
 * 사용자 접속 로그. /api/admin/** 이므로 AuthFilter 가 ADMIN 토큰을 요구한다.
 * domestic=true 면 국내(한국 할당 IP + 팀원) 접속만 돌려준다.
 */
@RestController
@RequestMapping("/api/admin/visitors")
public class AdminVisitorController {

    private final AdminVisitorService adminVisitorService;

    public AdminVisitorController(AdminVisitorService adminVisitorService) {
        this.adminVisitorService = adminVisitorService;
    }

    @GetMapping
    public ResponseEntity<AdminVisitorPageResponse> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size,
        @RequestParam(defaultValue = "false") boolean domestic,
        @RequestParam(defaultValue = "false") boolean excludeInternal,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(adminVisitorService.list(page, size, domestic, excludeInternal, keyword, from, to));
    }
}
