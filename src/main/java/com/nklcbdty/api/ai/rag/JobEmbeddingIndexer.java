package com.nklcbdty.api.ai.rag;

import com.nklcbdty.common.vo.Job_mst;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 임베딩이 없거나 버전이 낡은 공고를 주기적으로 인덱싱한다.
 * 공급자가 비가용(API 키 없음, 모델 로드 실패)일 때는 아무것도 하지 않고 다음 주기를 기다린다.
 */
@Slf4j
@Service
public class JobEmbeddingIndexer {

    /** 한 주기에 처리할 공고 수. {@link OpenAiEmbeddingProvider#MAX_BATCH} 와 맞춰 API 호출 1회로 끝낸다. */
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

        String version = embedder.modelVersion();

        List<Job_mst> targets;
        try {
            targets = repo.findStale(version, PageRequest.of(0, BATCH_SIZE));
        } catch (Exception e) {
            log.warn("임베딩 대상 조회 실패: {}", e.getMessage());
            return;
        }
        if (targets.isEmpty()) return;

        List<Job_mst> jobs = new ArrayList<>(targets.size());
        List<String> texts = new ArrayList<>(targets.size());
        for (Job_mst j : targets) {
            String text = textFor(j);
            if (text.isBlank()) continue;
            jobs.add(j);
            texts.add(text);
        }
        if (jobs.isEmpty()) return;

        // 배치로 한 번에 임베딩한다. 외부 API 는 왕복 지연이 지배적이라
        // 건당 호출로 하면 초기 백필이 같은 건수에 수십 배 오래 걸린다.
        long t0 = System.currentTimeMillis();
        List<float[]> vectors = embedder.embedAll(texts);

        int ok = 0;
        for (int i = 0; i < jobs.size(); i++) {
            float[] vec = i < vectors.size() ? vectors.get(i) : null;
            if (vec == null) continue; // 개별 실패 → 버전을 갱신하지 않으니 다음 주기에 다시 잡힌다
            Job_mst j = jobs.get(i);
            j.setEmbedding(Vectors.toBytes(vec));
            j.setEmbeddingVersion(version);
            cache.put(j.getId(), vec);
            ok++;
        }
        log.info("임베딩 배치: {}/{}건 ({}ms, version={})",
                ok, jobs.size(), System.currentTimeMillis() - t0, version);
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
