package com.nklcbdty.api.ai.rag;

import com.nklcbdty.common.vo.Job_mst;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final KeywordJobMatcher keywords;
    private final JobEmbeddingIndexer indexer;

    public SemanticSearchService(EmbeddingService embedder,
                                 JobEmbeddingCache cache,
                                 JobEmbeddingRepository repo,
                                 KeywordJobMatcher keywords,
                                 JobEmbeddingIndexer indexer) {
        this.embedder = embedder;
        this.cache = cache;
        this.repo = repo;
        this.keywords = keywords;
        this.indexer = indexer;
    }

    /**
     * 의미 검색이 되면 그것을, 안 되면(공급자 없음·인덱스 0건·질의 임베딩 실패) 키워드 매칭을 쓴다.
     * 임베딩이 쿼터 소진(429)으로 멈춰도 이력서 매칭·검색이 빈손으로 끝나지 않게 하려는 것이다.
     */
    public List<Result> search(String query, int topK) {
        if (query == null || query.isBlank()) return List.of();
        final int k = Math.max(1, topK);

        // 캐시에는 색인 뒤 마감된 공고와 같은 공고의 중복 행도 있어, 그걸 거르고도 k 건이 남도록 넉넉히 뽑는다.
        final int pool = k * 8;
        List<JobEmbeddingCache.Hit> hits = semanticHits(query, pool);
        if (hits.isEmpty()) {
            hits = keywords.search(query, pool).stream()
                    .map(h -> new JobEmbeddingCache.Hit(h.jobId(), h.score()))
                    .collect(Collectors.toList());
        }
        if (hits.isEmpty()) return List.of();

        List<Long> ids = hits.stream().map(h -> h.jobId).collect(Collectors.toList());
        Map<Long, Job_mst> byId = new HashMap<>();
        for (Job_mst j : repo.findAllById(ids)) byId.put(j.getId(), j);

        LocalDateTime now = LocalDateTime.now();
        List<Result> out = new ArrayList<>(k);
        Set<String> seen = new HashSet<>();
        for (JobEmbeddingCache.Hit h : hits) {
            if (out.size() >= k) break;
            Job_mst j = byId.get(h.jobId);
            if (j == null || KeywordJobMatcher.isClosed(j.getEndDate(), now)) continue;
            // 크롤러가 같은 공고를 여러 행으로 넣어 둔 경우가 있다(2026-11-14: 토스 공고 하나가 14행).
            // 벡터도 같아서 그대로 두면 한 공고가 상위를 다 차지한다. 점수순이라 처음 것만 남긴다.
            if (!seen.add(postingKey(j))) continue;
            Result r = new Result();
            r.job = j;
            r.score = h.score;
            out.add(r);
        }
        return out;
    }

    /** 같은 공고인지 가리는 키. 회사 + 공고번호, 공고번호가 없으면 행 id 로(합치지 않는다). */
    static String postingKey(Job_mst j) {
        String anno = j.getAnnoId();
        if (anno == null || anno.isBlank()) return "id:" + j.getId();
        return j.getCompanyCd() + "|" + anno;
    }

    /** 의미 검색을 쓸 수 있는지 — 공급자가 살아 있고, 공고 색인을 끝까지 따라잡았을 때만. */
    public boolean semanticReady() {
        return embedder.isAvailable() && cache.size() > 0 && indexer.isCaughtUp();
    }

    private List<JobEmbeddingCache.Hit> semanticHits(String query, int k) {
        if (!semanticReady()) {
            log.info("의미 검색 불가(available={}, indexedJobs={}, caughtUp={}, lastFailure={}) → 키워드 매칭으로 대신한다",
                    embedder.isAvailable(), cache.size(), indexer.isCaughtUp(), embedder.lastFailure());
            return List.of();
        }
        float[] q = embedder.embed(query);
        if (q == null) {
            log.info("질의 임베딩 실패(lastFailure={}) → 키워드 매칭으로 대신한다", embedder.lastFailure());
            return List.of();
        }
        return cache.search(q, k);
    }
}
