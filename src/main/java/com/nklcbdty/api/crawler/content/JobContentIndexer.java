package com.nklcbdty.api.crawler.content;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.nklcbdty.common.vo.Job_mst;

import lombok.extern.slf4j.Slf4j;

/**
 * 본문이 없는 공고를 조금씩 수집한다.
 *
 * <p>크롤(<code>/api/crawler</code>) 안에서 본문까지 받지 않는 이유: 목록 크롤만으로 이미 24분이
 * 걸리고, 공고 하나마다 상세를 한 번 더 받으면 배치가 몇 배로 길어진다. {@code JobEmbeddingIndexer}
 * 와 같은 방식으로 뒤에서 천천히 채운다. 신규 공고도 저장된 뒤 한 주기 안에 잡힌다.</p>
 *
 * <p>같은 사이트를 연달아 때리지 않도록 한 건마다 쉬어간다. 한 주기에 {@code BATCH_SIZE} 건만
 * 처리하므로 3천여 건을 다 채우는 데 며칠이 걸리지만, 그 사이에도 최신 공고부터(id 내림차순)
 * 채워진다.</p>
 */
@Slf4j
@Service
public class JobContentIndexer {

    private static final int BATCH_SIZE = 20;
    private static final long PAUSE_MS = 700L;

    /** 이 기간이 지나면 다시 확인한다. 공고 본문은 자주 바뀌지 않는다. */
    private static final int REFRESH_DAYS = 14;

    private final JobContentRepository repository;
    private final JobContentService contentService;

    /** 로컬 개발에서 외부 사이트를 때리지 않도록 끌 수 있게 한다. */
    private final boolean enabled;

    public JobContentIndexer(JobContentRepository repository,
                             JobContentService contentService,
                             @Value("${crawler.content.indexer.enabled:true}") boolean enabled) {
        this.repository = repository;
        this.contentService = contentService;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelay = 120_000L, initialDelay = 90_000L)
    public void indexBatch() {
        if (!enabled) {
            return;
        }

        List<Job_mst> targets;
        try {
            targets = repository.findNeedingContent(
                LocalDateTime.now().minusDays(REFRESH_DAYS), PageRequest.of(0, BATCH_SIZE));
        } catch (Exception e) {
            log.warn("본문 수집 대상 조회 실패: {}", e.getMessage());
            return;
        }
        if (targets.isEmpty()) {
            return;
        }

        long t0 = System.currentTimeMillis();
        int updated = 0;
        for (Job_mst job : targets) {
            try {
                if (contentService.collect(job)) {
                    updated++;
                }
            } catch (Exception e) {
                // collect 안에서 이미 실패를 기록한다. 여기까지 온 건 저장 자체가 실패한 경우다.
                log.warn("본문 수집 처리 실패 jobId={}: {}", job.getId(), e.getMessage());
            }
            pause();
        }
        log.info("본문 수집 배치: {}/{}건 갱신 ({}ms)", updated, targets.size(), System.currentTimeMillis() - t0);
    }

    private void pause() {
        try {
            Thread.sleep(PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
