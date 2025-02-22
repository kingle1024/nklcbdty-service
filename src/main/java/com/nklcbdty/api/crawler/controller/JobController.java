package com.nklcbdty.api.crawler.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.crawler.service.NaverJobCrawlerService;
import com.nklcbdty.api.crawler.vo.Job_mst;

@RestController
public class JobController {
    private final NaverJobCrawlerService naverJobCrawlerService;

    @Autowired
    public JobController(NaverJobCrawlerService naverJobCrawlerService) {
        this.naverJobCrawlerService = naverJobCrawlerService;
    }

    @GetMapping("/jobs")
    public List<Job_mst> getJobs() {
        return naverJobCrawlerService.crawlJobs();
    }
}
