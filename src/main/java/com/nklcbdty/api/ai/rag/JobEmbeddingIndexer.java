package com.nklcbdty.api.ai.rag;

import com.nklcbdty.common.vo.Job_mst;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 임베딩이 없는 신규/기존 공고를 주기적으로 인덱싱한다.
 * 모델 로드 실패 시에는 아무것도 하지 않고 다음 주기를 기다린다.
 */
@Slf4j
@Service
public class JobEmbeddingIndexer {

    private static final int BATCH_SIZE = 50;

    private final JobEmbeddingRepository repo;
    private final EmbeddingService embedder;
    private final JobEmbeddingCache cache;

    public JobEmbeddingIndexer(JobEmbeddingRepository repo,
                               EmbeddingService embedder,
                               JobEmbeddingCache cache) {
        this.repo = repo;
        this.embedder = embedder;
        this.cache = cache;
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 30_000L)
    @Transactional
    public void indexBatch() {
        if (!embedder.isAvailable()) return;

        List<Job_mst> targets;
        try {
            targets = repo.findUnindexed(PageRequest.of(0, BATCH_SIZE));
        } catch (Exception e) {
            log.warn("임베딩 미인덱스 조회 실패: {}", e.getMessage());
            return;
        }
        if (targets.isEmpty()) return;

        long t0 = System.currentTimeMillis();
        int ok = 0;
        for (Job_mst j : targets) {
            try {
                String text = textFor(j);
                if (text.isBlank()) continue;
                float[] vec = embedder.embed(text);
                if (vec == null) continue;
                j.setEmbedding(Vectors.toBytes(vec));
                j.setEmbeddingVersion(EmbeddingService.MODEL_VERSION);
                cache.put(j.getId(), vec);
                ok++;
            } catch (Exception e) {
                log.warn("임베딩 실패 jobId={}: {}", j.getId(), e.getMessage());
            }
        }
        log.info("임베딩 배치: {}/{}건 ({}ms)", ok, targets.size(), System.currentTimeMillis() - t0);
    }

    private String textFor(Job_mst j) {
        StringBuilder b = new StringBuilder();
        if (j.getAnnoSubject() != null) b.append(j.getAnnoSubject()).append(' ');
        if (j.getSubJobCdNm() != null) b.append(j.getSubJobCdNm()).append(' ');
        if (j.getClassCdNm() != null) b.append(j.getClassCdNm()).append(' ');
        if (j.getSysCompanyCdNm() != null) b.append(j.getSysCompanyCdNm()).append(' ');
        if (j.getWorkplace() != null) b.append(j.getWorkplace()).append(' ');
        if (j.getEmpTypeCdNm() != null) b.append(j.getEmpTypeCdNm());
        return b.toString().trim();
    }
}
