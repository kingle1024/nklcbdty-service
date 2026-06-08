package com.nklcbdty.api.jobdelete.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.jobdelete.dto.CreateJobDeleteRequestDto;
import com.nklcbdty.api.jobdelete.service.JobDeleteRequestService;
import com.nklcbdty.api.jobdelete.vo.JobDeleteRequest;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * 사용자용 채용공고 삭제요청 API.
 * - POST /api/job-delete-requests       : 삭제요청 등록(즉시 삭제 X, 관리자 검토 대기)
 * - GET  /api/job-delete-requests/pending-ids : 현재 삭제요청(PENDING) 상태인 jobId 목록(버튼 표시용)
 */
@Slf4j
@RestController
@RequestMapping("/api/job-delete-requests")
public class JobDeleteRequestController {

    private final JobDeleteRequestService service;

    public JobDeleteRequestController(JobDeleteRequestService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateJobDeleteRequestDto dto, HttpServletRequest httpRequest) {
        if (dto.getJobId() == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "jobId 는 필수입니다."));
        }
        String ip = resolveClientIp(httpRequest);
        JobDeleteRequest saved = service.create(dto.getJobId(), dto.getReason(), null, ip);
        return ResponseEntity.ok(Map.of(
            "status", "requested",
            "jobId", saved.getJobId(),
            "requestId", saved.getId(),
            "requestStatus", saved.getStatus()
        ));
    }

    @GetMapping("/pending-ids")
    public ResponseEntity<List<Long>> pendingIds() {
        return ResponseEntity.ok(service.pendingJobIds());
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
