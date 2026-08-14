package com.nklcbdty.api.crawler.content;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 수집된 공고 본문 조회. 목록 응답에는 싣지 않는다(본문이 커서 목록이 무거워진다). */
@RestController
@RequestMapping("/api/jobs")
public class JobContentController {

    private final JobContentService contentService;

    public JobContentController(JobContentService contentService) {
        this.contentService = contentService;
    }

    @GetMapping(value = "/{jobId}/content", produces = "application/json;charset=UTF-8")
    public ResponseEntity<?> content(@PathVariable Long jobId) {
        return contentService.find(jobId)
            .filter(JobContent::hasContent)
            .<ResponseEntity<?>>map(row -> ResponseEntity.ok(Map.of(
                "jobId", row.getJobId(),
                "companyCd", row.getCompanyCd() == null ? "" : row.getCompanyCd(),
                "content", row.getContent(),
                "source", row.getSource() == null ? "" : row.getSource(),
                "updatedAt", String.valueOf(row.getUpdatedAt())
            )))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
