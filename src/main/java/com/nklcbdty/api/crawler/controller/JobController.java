package com.nklcbdty.api.crawler.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.crawler.interfaces.JobCrawler;
import com.nklcbdty.api.crawler.service.BaeminJobCrawlerService;
import com.nklcbdty.api.crawler.service.CoupangJobCrawlerService;
import com.nklcbdty.api.crawler.service.DaangnJobCrawlerService;
import com.nklcbdty.api.crawler.service.JobService;
import com.nklcbdty.api.crawler.service.KakaoCrawlerService;
import com.nklcbdty.api.crawler.service.LineJobCrawlerService;
import com.nklcbdty.api.crawler.service.NaverJobCrawlerService;
import com.nklcbdty.api.crawler.service.TossJobCrawlerService;
import com.nklcbdty.api.crawler.service.YanoljaCralwerService;
import com.nklcbdty.api.crawler.vo.Job_mst;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api")
public class JobController {
    private final JobCrawler naverJobCrawlerService;
    private final JobCrawler kakaoCrawlerService;
    private final JobCrawler lineJobCrawlerService;
    private final JobCrawler tossJobCrawlerService;
    private final JobCrawler yanoljaCralwerService;
    private final JobService jobService;
    private final JobCrawler coupangJobCrawlerService;
    private final JobCrawler baeminJobCrawlerService;
    private final JobCrawler daangnJobCrawlerService;

    @Autowired
    public JobController(
        @Qualifier("naverJobCrawlerService") JobCrawler naverJobCrawlerService,
        @Qualifier("kakaoCrawlerService") JobCrawler kakaoCrawlerService,
        @Qualifier("lineJobCrawlerService") JobCrawler lineJobCrawlerService,
        @Qualifier("tossJobCrawlerService") JobCrawler tossJobCrawlerService,
        @Qualifier("yanoljaCralwerService") JobCrawler yanoljaCralwerService,
        @Qualifier("coupangJobCrawlerService") JobCrawler coupangJobCrawlerService,
        @Qualifier("baeminJobCrawlerService") JobCrawler baeminJobCrawlerService,
        @Qualifier("daangnJobCrawlerService") JobCrawler daangnJobCrawlerService,
        JobService jobService
    ) {

        this.naverJobCrawlerService = naverJobCrawlerService;
        this.kakaoCrawlerService = kakaoCrawlerService;
        this.lineJobCrawlerService = lineJobCrawlerService;
        this.tossJobCrawlerService = tossJobCrawlerService;
        this.yanoljaCralwerService = yanoljaCralwerService;
        this.jobService = jobService;
        this.coupangJobCrawlerService = coupangJobCrawlerService;
        this.baeminJobCrawlerService = baeminJobCrawlerService;
        this.daangnJobCrawlerService = daangnJobCrawlerService;
    }

    @GetMapping("/list")
    public List<Job_mst> list(@RequestParam(defaultValue = "ALL") String company) {
        return jobService.list(company);
    }

    @GetMapping("/crawler")
    public List<Job_mst> cralwer(@RequestParam String company) {
        log.info("cralwer company : {}", company);
        try {
            switch (company) {
                case "naver": {
                    return naverJobCrawlerService.crawlJobs().get();
                }
                case "kakao": {
                    return kakaoCrawlerService.crawlJobs().get();
                }
                case "line": {
                    return lineJobCrawlerService.crawlJobs().get();
                }
                case "coupang": {
                    return coupangJobCrawlerService.crawlJobs().get();
                }
                case "baemin": {
                    return baeminJobCrawlerService.crawlJobs().get();
                }
                case "daangn": {
                    return daangnJobCrawlerService.crawlJobs().get();
                }
                case "toss": {
                    return tossJobCrawlerService.crawlJobs().get();
                }
                case "yanolja": {
                    return yanoljaCralwerService.crawlJobs().get();
                }
                case "all": {
                    jobService.deleteAll();

                    CompletableFuture<List<Job_mst>> naverFuture = naverJobCrawlerService.crawlJobs();
                    CompletableFuture<List<Job_mst>> kakaoFuture = kakaoCrawlerService.crawlJobs();
                    CompletableFuture<List<Job_mst>> lineFuture = lineJobCrawlerService.crawlJobs();
                    CompletableFuture<List<Job_mst>> coupangFuture = coupangJobCrawlerService.crawlJobs();
                    CompletableFuture<List<Job_mst>> baeminFuture = baeminJobCrawlerService.crawlJobs();
                    CompletableFuture<List<Job_mst>> daangnFuture = daangnJobCrawlerService.crawlJobs();
                    CompletableFuture<List<Job_mst>> tossFuture = tossJobCrawlerService.crawlJobs();
                    CompletableFuture<List<Job_mst>> yanoljaFuture = yanoljaCralwerService.crawlJobs();

                    CompletableFuture<Void> allFutures = CompletableFuture.allOf(naverFuture, kakaoFuture, lineFuture,
                        tossFuture, yanoljaFuture, coupangFuture, baeminFuture, daangnFuture
                        // 모든 CompletableFuture 객체를 여기에 나열합니다.
                    );

                    try {
                        allFutures.get(); // 모든 비동기 작업 완료 대기

                        log.info("All async crawlers completed.");

                        List<Job_mst> combinedResults = new ArrayList<>();
                        combinedResults.addAll(naverFuture.get());
                        combinedResults.addAll(kakaoFuture.get());
                        combinedResults.addAll(lineFuture.get());
                        combinedResults.addAll(coupangFuture.get());
                        combinedResults.addAll(baeminFuture.get());
                        combinedResults.addAll(daangnFuture.get());
                        combinedResults.addAll(tossFuture.get());
                        combinedResults.addAll(yanoljaFuture.get());

                        log.info("Combined results count: {}", combinedResults.size());

                        // 모든 비동기 작업이 완료된 후에 결과를 반환합니다.
                        return combinedResults;

                    } catch (InterruptedException | ExecutionException e) {
                        log.error("Error during async crawler execution", e);
                        return Collections.emptyList();
                    } catch (Exception e) {
                        log.error("Error during async crawler execution", e);
                    }
                }
                default: {

                }
            }
        } catch (Exception e) {
            log.error("Error during async crawler execution", e);
        }
        return Collections.emptyList();
    }
}
