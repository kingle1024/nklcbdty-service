package com.nklcbdty.api.crawler.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.common.KvCacheRefresher;
import com.nklcbdty.api.crawler.common.CrawlerCommonService;
import com.nklcbdty.api.crawler.interfaces.JobCrawler;
import com.nklcbdty.api.crawler.service.CoupangJobCrawlerService;
import com.nklcbdty.api.crawler.service.DaangnJobCrawlerService;
import com.nklcbdty.api.crawler.service.JobService;
import com.nklcbdty.api.crawler.service.KakaoCrawlerService;
import com.nklcbdty.api.crawler.service.NaverJobCrawlerService;
import com.nklcbdty.api.crawler.service.YanoljaCralwerService;
import com.nklcbdty.common.vo.Job_mst;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api")
public class JobController {
    private final NaverJobCrawlerService naverJobCrawlerService;
    private final KakaoCrawlerService kakaoCrawlerService;
    private final JobCrawler lineJobCrawlerService;
    private final JobCrawler tossJobCrawlerService;
    private final YanoljaCralwerService yanoljaCralwerService;
    private final JobService jobService;
    private final CoupangJobCrawlerService coupangJobCrawlerService;
    private final JobCrawler baeminJobCrawlerService;
    private final DaangnJobCrawlerService daangnJobCrawlerService;
    private final CrawlerCommonService commonService;
    private final KvCacheRefresher kvCacheRefresher;

    @Autowired
    public JobController(
        NaverJobCrawlerService naverJobCrawlerService,
        KakaoCrawlerService kakaoCrawlerService,
        @Qualifier("lineJobCrawlerService") JobCrawler lineJobCrawlerService,
        @Qualifier("tossJobCrawlerService") JobCrawler tossJobCrawlerService,
        YanoljaCralwerService yanoljaCralwerService,
        CoupangJobCrawlerService coupangJobCrawlerService,
        @Qualifier("baeminJobCrawlerService") JobCrawler baeminJobCrawlerService,
        DaangnJobCrawlerService daangnJobCrawlerService,
        JobService jobService, CrawlerCommonService commonService,
        KvCacheRefresher kvCacheRefresher) {

        this.naverJobCrawlerService = naverJobCrawlerService;
        this.kakaoCrawlerService = kakaoCrawlerService;
        this.lineJobCrawlerService = lineJobCrawlerService;
        this.tossJobCrawlerService = tossJobCrawlerService;
        this.yanoljaCralwerService = yanoljaCralwerService;
        this.jobService = jobService;
        this.coupangJobCrawlerService = coupangJobCrawlerService;
        this.baeminJobCrawlerService = baeminJobCrawlerService;
        this.daangnJobCrawlerService = daangnJobCrawlerService;
        this.commonService = commonService;
        this.kvCacheRefresher = kvCacheRefresher;
    }

    /**
     * 공고 목록. 응답은 캐시된 JSON 문자열을 그대로 흘려보낸다(본문은 캐시 도입 전과 동일).
     *
     * <p>charset 을 명시하는 이유: String 을 반환하면 StringHttpMessageConverter 가 쓰이는데,
     * 이 컨버터의 프레임워크 기본 charset 은 ISO-8859-1 이라 한글이 깨질 수 있다.
     * 스프링 부트가 UTF-8 로 바꿔주긴 하지만 설정에 의존하지 않도록 여기서 못박는다.</p>
     */
    @GetMapping(value = "/list", produces = "application/json;charset=UTF-8")
    public String list(@RequestParam(defaultValue = "ALL") String company) {
        return jobService.listAsJson(company);
    }

    // 크롤 결과 저장으로 목록이 바뀌면 CrawlerCommonService#saveAll 이 캐시를 비운다.
    @GetMapping("/crawler")
    public List<Job_mst> cralwer(@RequestParam String company) {
        log.info("cralwer company : {}", company);
        try {
            switch (company) {
                case "naver":
                    return crawlSaveAndRefresh(naverJobCrawlerService::crawlJobs, "NAVER");
                case "kakao":
                    return crawlSaveAndRefresh(kakaoCrawlerService::crawlJobs, "KAKAO");
                case "line":
                    return crawlSaveAndRefresh(lineJobCrawlerService::crawlJobs, "LINE");
                case "coupang":
                    return crawlSaveAndRefresh(coupangJobCrawlerService::crawlJobs, "COUPANG");
                case "baemin":
                    return crawlSaveAndRefresh(baeminJobCrawlerService::crawlJobs, "BAEMIN");
                case "daangn":
                    return crawlSaveAndRefresh(daangnJobCrawlerService::crawlJobs, "DAANGN");
                case "toss":
                    return crawlSaveAndRefresh(tossJobCrawlerService::crawlJobs, "TOSS");
                case "yanolja":
                    return crawlSaveAndRefresh(yanoljaCralwerService::crawlJobs, "YANOLJA");
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
                        commonService.refineJobData(combinedResults);
                        log.info("Combined results count: {}", combinedResults.size());

                        // 모든 비동기 작업이 완료된 후에 결과를 반환합니다.
                        List<Job_mst> saved = commonService.saveAll(combinedResults);
                        kvCacheRefresher.refreshAll();
                        return saved;

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

    /**
     * 회사 단건 크롤: 크롤 → LLM 보정 → 저장 → KV 목록 캐시 갱신.
     *
     * <p>KV 갱신을 여기 묶어둔 이유는 저장 경로마다 따로 부르면 빠뜨리기 때문이다. 크롤러 타입이
     * 제각각(일부만 {@link JobCrawler} 구현)이라 메서드 참조로 받는다.</p>
     *
     * <p>순서가 중요하다. {@code saveAll} 의 {@code @CacheEvict} 는 기본이
     * {@code beforeInvocation=false} 라 메서드가 <b>반환된 뒤</b>에 Redis 를 비운다. 그 전에 목록을
     * 만들면 방금 무효화될 예정인 옛 값을 그대로 KV 에 얹게 되므로, 반드시 저장이 끝난 다음에 부른다.</p>
     */
    private List<Job_mst> crawlSaveAndRefresh(
        Supplier<CompletableFuture<List<Job_mst>>> crawl, String companyCd) throws Exception {

        List<Job_mst> items = crawl.get().get();
        commonService.refineJobItemBygemini(items);
        List<Job_mst> saved = commonService.saveAll(items);
        kvCacheRefresher.refreshAfterCrawl(companyCd);
        return saved;
    }
}
