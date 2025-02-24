package com.nklcbdty.api.crawler.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.crawler.service.LineJobCrawlerService;
import com.nklcbdty.api.crawler.service.NaverJobCrawlerService;
import com.nklcbdty.api.crawler.vo.Job_mst;

@RestController
public class JobController {
    private final NaverJobCrawlerService naverJobCrawlerService;
    private final LineJobCrawlerService lineJobCrawlerService;

    @Autowired
    public JobController(NaverJobCrawlerService naverJobCrawlerService, LineJobCrawlerService lineJobCrawlerService) {
        this.naverJobCrawlerService = naverJobCrawlerService;
        this.lineJobCrawlerService = lineJobCrawlerService;
    }

    @GetMapping("/crawler")
    public List<Job_mst> cralwer(@RequestParam String company) {
        switch (company) {
            case "naver": {
                return naverJobCrawlerService.crawlJobs();
            }
            case "kakao": {

            }
            case "line": {
                return lineJobCrawlerService.crawlJobs();
            }
            default: {

            }
        }
        return lineJobCrawlerService.crawlJobs();
    }
}
