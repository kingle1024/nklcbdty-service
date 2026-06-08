package com.nklcbdty.api.jobdelete.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.jobdelete.service.JobDeleteRequestService;
import com.nklcbdty.api.jobdelete.vo.JobDeleteRequest;

/**
 * 관리자용 채용공고 삭제요청 검토 API.
 * - GET  /api/admin/job-delete-requests           : 요청 목록(status 파라미터로 필터)
 * - POST /api/admin/job-delete-requests/{id}/approve : 승인(실제 공고 삭제)
 * - POST /api/admin/job-delete-requests/{id}/reject  : 반려(공고 유지)
 *
 * TODO: 관리자 로그인 도입 시 SecurityConfig 의 /api/admin/** 매처와 함께 인증 적용
 */
@RestController
@RequestMapping("/api/admin/job-delete-requests")
public class AdminJobDeleteRequestController {

    private final JobDeleteRequestService service;

    public AdminJobDeleteRequestController(JobDeleteRequestService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<JobDeleteRequest>> list(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(service.list(status));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id,
                                     @RequestParam(required = false, defaultValue = "admin") String processedBy) {
        JobDeleteRequest result = service.approve(id, processedBy);
        return ResponseEntity.ok(Map.of("status", "approved", "id", result.getId()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id,
                                    @RequestParam(required = false, defaultValue = "admin") String processedBy) {
        JobDeleteRequest result = service.reject(id, processedBy);
        return ResponseEntity.ok(Map.of("status", "rejected", "id", result.getId()));
    }
}
