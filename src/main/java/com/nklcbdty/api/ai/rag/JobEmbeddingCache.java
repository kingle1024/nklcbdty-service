package com.nklcbdty.api.ai.rag;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 임베딩 벡터 인메모리 캐시. 부팅 시 DB → 메모리로 한 번에 적재하고,
 * 신규 인덱싱은 {@link #put} 으로 hot-add.
 *
 * <p>검색 비용: O(N) linear scan. N=10,000, 384차원 기준 단일 쿼리 ~10ms.
 * 수십만 건 이상에서는 ANN(HNSW) 인덱스나 ES kNN으로 진화 필요.</p>
 */
@Slf4j
@Service
public class JobEmbeddingCache {

    public static final class Entry {
        public final Long jobId;
        public final float[] vec;
        public Entry(Long jobId, float[] vec) { this.jobId = jobId; this.vec = vec; }
    }

    public static final class Hit {
        public final Long jobId;
        public final float score;
        public Hit(Long jobId, float score) { this.jobId = jobId; this.score = score; }
    }

    private final CopyOnWriteArrayList<Entry> entries = new CopyOnWriteArrayList<>();
    private final JobEmbeddingRepository repo;

    public JobEmbeddingCache(JobEmbeddingRepository repo) {
        this.repo = repo;
    }

    @PostConstruct
    public void warmUp() {
        long t0 = System.currentTimeMillis();
        try {
            List<Object[]> rows = repo.findAllEmbeddingsRaw();
            for (Object[] row : rows) {
                Long id = (Long) row[0];
                byte[] bytes = (byte[]) row[1];
                float[] v = Vectors.fromBytes(bytes);
                if (v.length > 0) entries.add(new Entry(id, v));
            }
            log.info("임베딩 캐시 워밍업 완료: {}건 ({}ms)", entries.size(), System.currentTimeMillis() - t0);
        } catch (Exception e) {
            log.warn("임베딩 캐시 워밍업 실패 (테이블 컬럼 미생성 가능): {}", e.getMessage());
        }
    }

    public void put(Long jobId, float[] vec) {
        entries.removeIf(e -> e.jobId.equals(jobId));
        entries.add(new Entry(jobId, vec));
    }

    public List<Hit> search(float[] query, int topK) {
        if (query == null || query.length == 0 || entries.isEmpty()) return List.of();
        List<Hit> hits = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            if (e.vec.length != query.length) continue;
            hits.add(new Hit(e.jobId, Vectors.dot(query, e.vec)));
        }
        hits.sort(Comparator.comparingDouble((Hit h) -> -h.score));
        return hits.subList(0, Math.min(topK, hits.size()));
    }

    public int size() { return entries.size(); }
}
