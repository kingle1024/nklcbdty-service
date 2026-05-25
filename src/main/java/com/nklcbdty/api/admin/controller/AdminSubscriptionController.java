package com.nklcbdty.api.admin.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.admin.service.AdminSubscriptionService;
import com.nklcbdty.api.user.dto.UserSettingsRequest;

// TODO: 다음 단계에서 관리자 로그인 인증 적용 (SecurityConfig의 /api/admin/** 매처와 함께)
@RestController
@RequestMapping("/api/admin/subscriptions")
public class AdminSubscriptionController {

    private final AdminSubscriptionService adminSubscriptionService;

    @Autowired
    public AdminSubscriptionController(AdminSubscriptionService adminSubscriptionService) {
        this.adminSubscriptionService = adminSubscriptionService;
    }

    @GetMapping
    public ResponseEntity<?> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String keyword
    ) {
        return ResponseEntity.ok(adminSubscriptionService.list(page, size, keyword));
    }

    @GetMapping("/stats")
    public ResponseEntity<?> stats() {
        return ResponseEntity.ok(adminSubscriptionService.stats());
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> detail(@PathVariable String userId) {
        return ResponseEntity.ok(adminSubscriptionService.detail(userId));
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<?> deleteItem(@PathVariable Long id) {
        adminSubscriptionService.deleteItem(id);
        return ResponseEntity.ok(Map.of("status", "deleted", "id", id));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<?> update(
        @PathVariable String userId,
        @RequestBody UserSettingsRequest request
    ) {
        return ResponseEntity.ok(adminSubscriptionService.updateSubscriptions(userId, request));
    }

    @PostMapping("/{userId}/send-email")
    public ResponseEntity<?> sendJobEmail(@PathVariable String userId) {
        adminSubscriptionService.sendJobEmail(userId);
        return ResponseEntity.accepted().body(Map.of("status", "queued", "userId", userId));
    }
}
