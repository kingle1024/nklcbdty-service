package com.nklcbdty.api.ai.rag;

import com.nklcbdty.common.vo.Job_mst;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 자연어 쿼리 → 의미 유사 공고 top-K 반환.
 * (1단계 MVP: 생성 LLM 없음. 검색 결과만 반환 → 토큰 비용 0)
 */
@Slf4j
@Service
public class SemanticSearchService {

    public static final class Result {
        public Job_mst job;
        public float score;
    }

    private final EmbeddingService embedder;
    private final JobEmbeddingCache cache;
    private final JobEmbeddingRepository repo;

    public SemanticSearchService(EmbeddingService embedder,
                                 JobEmbeddingCache cache,
                                 JobEmbeddingRepository repo) {
        this.embedder = embedder;
        this.cache = cache;
        this.repo = repo;
    }

    public List<Result> search(String query, int topK) {
        if (query == null || query.isBlank()) return List.of();
        if (!embedder.isAvailable()) {
            log.warn("임베딩 모델 미로드 → 의미 검색 불가 (모델 다운로드/로드 중일 수 있음)");
            return List.of();
        }

        float[] q = embedder.embed(query);
        if (q == null) return List.of();

        List<JobEmbeddingCache.Hit> hits = cache.search(q, Math.max(1, topK));
        if (hits.isEmpty()) return List.of();

        List<Long> ids = hits.stream().map(h -> h.jobId).collect(Collectors.toList());
        Map<Long, Job_mst> byId = new HashMap<>();
        for (Job_mst j : repo.findAllById(ids)) byId.put(j.getId(), j);

        List<Result> out = new ArrayList<>(hits.size());
        for (JobEmbeddingCache.Hit h : hits) {
            Job_mst j = byId.get(h.jobId);
            if (j == null) continue;
            Result r = new Result();
            r.job = j;
            r.score = h.score;
            out.add(r);
        }
        return out;
    }
}
