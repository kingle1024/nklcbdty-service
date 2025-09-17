package com.nklcbdty.api.search.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.search.dto.JobSearchDto;
import com.nklcbdty.api.search.service.JobMstSearchService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/job")
@RequiredArgsConstructor
public class JobSearchController {
    private final JobMstSearchService jobMstSearchService;

    @GetMapping("/search")
    public ResponseEntity<List<JobSearchDto>> searchJobs(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        List<JobSearchDto> searchResults = jobMstSearchService.searchAsDto(keyword, page, size);
        return ResponseEntity.ok(searchResults);
    }
}
