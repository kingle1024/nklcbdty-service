package com.nklcbdty.api.crawler.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.crawler.service.BaeminJobCrawlerService;
import com.nklcbdty.api.crawler.service.CoupangJobCrawlerService;
import com.nklcbdty.api.crawler.service.JobService;
import com.nklcbdty.api.crawler.service.KakaoCrawlerService;
import com.nklcbdty.api.crawler.service.LineJobCrawlerService;
import com.nklcbdty.api.crawler.service.NaverJobCrawlerService;
import com.nklcbdty.api.crawler.service.TossJobCrawlerService;
import com.nklcbdty.api.crawler.service.YanoljaCralwerService;
import com.nklcbdty.api.crawler.vo.Job_mst;

@RestController
@RequestMapping("/api")
public class JobController {
    private final NaverJobCrawlerService naverJobCrawlerService;
    private final KakaoCrawlerService kakaoCrawlerService;
    private final LineJobCrawlerService lineJobCrawlerService;
    private final TossJobCrawlerService tossJobCrawlerService;
    private final YanoljaCralwerService yanoljaCralwerService;
    private final JobService jobService;
    private final CoupangJobCrawlerService coupangJobCrawlerService;
    private final BaeminJobCrawlerService baeminJobCrawlerService;

    @Autowired
    public JobController(NaverJobCrawlerService naverJobCrawlerService, KakaoCrawlerService kakaoCrawlerService, LineJobCrawlerService lineJobCrawlerService,
        TossJobCrawlerService tossJobCrawlerService, YanoljaCralwerService yanoljaCralwerService,
        JobService jobService, CoupangJobCrawlerService coupangJobCrawlerService,
        BaeminJobCrawlerService baeminJobCrawlerService) {

        this.naverJobCrawlerService = naverJobCrawlerService;
        this.kakaoCrawlerService = kakaoCrawlerService;
        this.lineJobCrawlerService = lineJobCrawlerService;
        this.tossJobCrawlerService = tossJobCrawlerService;
        this.yanoljaCralwerService = yanoljaCralwerService;
        this.jobService = jobService;
        this.coupangJobCrawlerService = coupangJobCrawlerService;
        this.baeminJobCrawlerService = baeminJobCrawlerService;
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
            	return coupangJobCrawlerService.crawlJobs();
            }
            case "baemin": {
            	return baeminJobCrawlerService.crawlJobs();
            }
            case "daangn": {

            }
            case "toss": {
                return tossJobCrawlerService.crawlJobs();
            }
            case "yanolja": {
                return yanoljaCralwerService.crawlJobs();
            }
            default: {

            }
        }
        return lineJobCrawlerService.crawlJobs();
    }
}
