package com.nklcbdty.api.crawler.recovery;

import java.util.List;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.nklcbdty.api.crawler.common.CrawlerCommonService;
import com.nklcbdty.api.crawler.interfaces.JobCrawler;
import com.nklcbdty.common.vo.Job_mst;

import lombok.extern.slf4j.Slf4j;

/**
 * 링크 점검 배치가 매일 죽여 놓은 배민 공고를 다시 살린다.
 *
 * <p><b>이건 우회다.</b> 제대로 된 자리는 batch 저장소의 {@code BaeminLivenessChecker}(2026-08-04)이고,
 * 그건 배민 채용 API 로 생존을 판정해 애초에 종료 처리를 하지 않는다. 그런데 그 코드가 운영에
 * 배포되지 않는다 — CloudType 의 batch 앱이 새 이미지를 안 가져간다. 배포된 코드가 옛것이라는 건
 * 링크 점검 cron 으로 확인할 수 있다(소스는 {@code 0 30 9}, 운영은 {@code 0 30 7}):</p>
 *
 * <pre>
 * curl -s https://port-0-nklcbdty-batch-m6qh1fte0c037b76.sel4.cloudtype.app/actuator/scheduledtasks
 * </pre>
 *
 * <p>배민 상세페이지는 Next.js 클라이언트 렌더링이라 원본 HTML 에 공고명이 없다. 링크 점검은
 * "상세페이지 HTML 에 공고명이 있는가"로 판정하므로 살아있는 공고를 전부 종료로 찍는다.
 * 07:00 크롤이 살리면 07:30 링크 점검이 다시 죽이는 하루 주기가 된다(운영 실행 이력 기준
 * 링크 점검은 07:30~08:23, 이어서 08:23 에 메일). 그래서 점검이 끝난 뒤에 한 번 더 크롤해
 * 되살린다.</p>
 *
 * <p>크롤 결과에 있는 공고는 {@code CrawlerCommonService#getNotSaveJobItem} 이 endDate 를 최신값
 * (배민은 {@code 2999-12-31})으로 되돌린다. 즉 이 스케줄러는 크롤을 한 번 더 부르는 것 말고
 * 하는 일이 없다 — 배민 채용 API 한 번 호출이라 3초면 끝난다.</p>
 *
 * <p>batch 가 제대로 배포되면 이 작업은 아무것도 바꾸지 않는 무해한 중복이 된다.
 * 그때 {@code crawler.recovery.baemin.enabled=false} 로 끄거나 지우면 된다.</p>
 */
@Slf4j
@Service
public class BaeminRecoveryScheduler {

    private final JobCrawler baeminJobCrawlerService;
    private final CrawlerCommonService commonService;
    private final boolean enabled;

    public BaeminRecoveryScheduler(
        @Qualifier("baeminJobCrawlerService") JobCrawler baeminJobCrawlerService,
        CrawlerCommonService commonService,
        @Value("${crawler.recovery.baemin.enabled:true}") boolean enabled) {

        this.baeminJobCrawlerService = baeminJobCrawlerService;
        this.commonService = commonService;
        this.enabled = enabled;
    }

    /** 링크 점검(07:30~08:23)과 메일(08:23)이 모두 끝난 뒤. */
    @Scheduled(cron = "${crawler.recovery.baemin.cron:0 0 9 * * *}", zone = "Asia/Seoul")
    public void recover() {
        if (!enabled) {
            return;
        }

        try {
            List<Job_mst> items = baeminJobCrawlerService.crawlJobs().get();
            if (items == null) {
                log.warn("배민 공고 복구: 크롤 결과가 null");
                return;
            }
            // 신규 공고는 여기서 저장된다. 기존 행의 endDate 되살리기는 crawlJobs() 안에서 이미 끝난다.
            commonService.refineJobItemBygemini(items);
            commonService.saveAll(items);
            log.info("배민 공고 복구 크롤 완료 — 신규 {}건", items.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("배민 공고 복구 중단됨");
        } catch (ExecutionException e) {
            log.error("배민 공고 복구 실패: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("배민 공고 복구 실패: {}", e.getMessage(), e);
        }
    }
}
