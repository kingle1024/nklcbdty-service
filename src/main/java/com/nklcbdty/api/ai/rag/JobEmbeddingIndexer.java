package com.nklcbdty.api.ai.rag;

import com.nklcbdty.common.vo.Job_mst;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    /**
     * 남은 공고를 다 색인했는지. 다 되기 전에 의미 검색을 쓰면 색인된 일부 공고 안에서만 고르게 돼서
     * (공급자를 바꾼 직후 50건만 있는 식) 키워드 매칭보다 못하다. 한 번 따라잡으면 재시작 전까지 유지한다 —
     * 그 뒤 매일 들어오는 새 공고는 몇 분 안에 따라잡는다.
     */
    private volatile boolean caughtUp;

    /**
     * 한도 초과(429)를 받으면 이 시각까지 쉰다. 무료 한도는 하루 단위라 1분마다 다시 두드려 봐야 계속 429 이고
     * 로그만 쌓인다. 한 시간 뒤에 한 번 다시 본다.
     */
    private volatile LocalDateTime pausedUntil;

    static final long PAUSE_MINUTES_ON_429 = 60;
    private static final DateTimeFormatter END_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
        LocalDateTime now = LocalDateTime.now();
        if (pausedUntil != null && now.isBefore(pausedUntil)) return;

        String version = embedder.modelVersion();

        List<Job_mst> targets;
        try {
            targets = repo.findStale(version, now.format(END_DATE), PageRequest.of(0, BATCH_SIZE));
        } catch (Exception e) {
            log.warn("임베딩 대상 조회 실패: {}", e.getMessage());
            return;
        }
        if (targets.isEmpty()) {
            caughtUp = true;
            return;
        }

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
        String failure = embedder.lastFailure();
        if (ok == 0 && failure != null && failure.contains("429")) {
            pausedUntil = LocalDateTime.now().plusMinutes(PAUSE_MINUTES_ON_429);
            log.warn("임베딩 한도 초과(429) → {} 까지 색인을 쉰다. 그동안 매칭은 키워드로 한다", pausedUntil);
            return;
        }
        // 마지막 페이지(50건 미만)까지 전부 성공했으면 따라잡은 것이다.
        if (targets.size() < BATCH_SIZE && ok == jobs.size()) caughtUp = true;
        log.info("임베딩 배치: {}/{}건 ({}ms, version={})",
                ok, jobs.size(), System.currentTimeMillis() - t0, version);
    }

    /** 429 로 쉬는 중이면 다시 시도할 시각, 아니면 null. */
    public LocalDateTime pausedUntil() {
        LocalDateTime p = pausedUntil;
        return p != null && LocalDateTime.now().isBefore(p) ? p : null;
    }

    public boolean isCaughtUp() {
        return caughtUp;
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
