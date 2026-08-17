package com.nklcbdty.api.crawler.recovery;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.nklcbdty.api.crawler.common.CrawlerCommonService;
import com.nklcbdty.api.crawler.interfaces.JobCrawler;
import com.nklcbdty.common.vo.Job_mst;

class BaeminRecoverySchedulerTest {

    @Mock
    private JobCrawler baeminJobCrawlerService;
    @Mock
    private CrawlerCommonService commonService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private Job_mst job() {
        Job_mst job = new Job_mst();
        job.setAnnoId("25497");
        job.setAnnoSubject("Server(배차시스템)");
        return job;
    }

    @Test
    void 크롤을_다시_돌려_공고를_되살린다() {
        when(baeminJobCrawlerService.crawlJobs())
            .thenReturn(CompletableFuture.completedFuture(List.of(job())));

        new BaeminRecoveryScheduler(baeminJobCrawlerService, commonService, true).recover();

        verify(baeminJobCrawlerService).crawlJobs();
        verify(commonService).saveAll(anyList());
    }

    @Test
    void 꺼두면_아무것도_하지_않는다() {
        new BaeminRecoveryScheduler(baeminJobCrawlerService, commonService, false).recover();

        verify(baeminJobCrawlerService, never()).crawlJobs();
        verify(commonService, never()).saveAll(anyList());
    }

    @Test
    void 크롤이_실패해도_예외를_밖으로_던지지_않는다() {
        // 스케줄러에서 예외가 새면 다음 주기가 아니라 그 실행만 죽는다. 로그만 남기고 넘어가야 한다.
        when(baeminJobCrawlerService.crawlJobs())
            .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("배민 API 502")));

        new BaeminRecoveryScheduler(baeminJobCrawlerService, commonService, true).recover();

        verify(commonService, never()).saveAll(anyList());
    }

    @Test
    void 신규가_없어도_캐시를_비우도록_저장을_부른다() {
        // saveAll 에 @CacheEvict 가 걸려 있다. 되살린 기존 행이 목록에 다시 보이려면 캐시를 비워야 한다.
        when(baeminJobCrawlerService.crawlJobs())
            .thenReturn(CompletableFuture.completedFuture(List.of()));

        new BaeminRecoveryScheduler(baeminJobCrawlerService, commonService, true).recover();

        verify(commonService).saveAll(anyList());
    }
}
