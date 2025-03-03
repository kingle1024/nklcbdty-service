package com.nklcbdty.api.crawler.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.crawler.service.JobService;
import com.nklcbdty.api.crawler.service.KakaoCrawlerService;
import com.nklcbdty.api.crawler.service.LineJobCrawlerService;
import com.nklcbdty.api.crawler.service.NaverJobCrawlerService;
import com.nklcbdty.api.crawler.vo.Job_mst;

@RestController
@RequestMapping("/api")
public class JobController {
    private final NaverJobCrawlerService naverJobCrawlerService;
    private final KakaoCrawlerService kakaoCrawlerService;
    private final LineJobCrawlerService lineJobCrawlerService;
    private final JobService jobService;

    @Autowired
    public JobController(NaverJobCrawlerService naverJobCrawlerService, KakaoCrawlerService kakaoCrawlerService, LineJobCrawlerService lineJobCrawlerService,
        JobService jobService) {
        this.naverJobCrawlerService = naverJobCrawlerService;
        this.kakaoCrawlerService = kakaoCrawlerService;
        this.lineJobCrawlerService = lineJobCrawlerService;
        this.jobService = jobService;
    }

    @GetMapping("/list")
    public List<Job_mst> list(@RequestParam(defaultValue = "NAVER") String company) {
        return jobService.list(company);
    }

    @GetMapping("/crawler")
    public List<Job_mst> cralwer(@RequestParam String company) {
        switch (company) {
            case "naver": {
                return naverJobCrawlerService.crawlJobs();
            }
            case "kakao": {
                return kakaoCrawlerService.crawlJobs();
            }
            case "line": {
                return lineJobCrawlerService.crawlJobs();
            }
            case "coupang": {

            }
            case "baemin": {

            }
            case "daangn": {

            }
            case "toss": {

            }
            case "yanolja": {

            }
            default: {

            }
        }
        return lineJobCrawlerService.crawlJobs();
    }
}
