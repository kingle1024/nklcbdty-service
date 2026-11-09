package com.nklcbdty.api.ai.rag;

import com.nklcbdty.common.vo.Job_mst;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobEmbeddingIndexerTest {

    private final JobEmbeddingRepository repo = mock(JobEmbeddingRepository.class);
    private final EmbeddingService embedder = mock(EmbeddingService.class);
    private final JobEmbeddingCache cache = mock(JobEmbeddingCache.class);
    private final JobEmbeddingIndexer indexer = new JobEmbeddingIndexer(repo, embedder, cache);

    private static Job_mst job(long id) {
        Job_mst j = new Job_mst();
        j.setId(id);
        j.setAnnoSubject("백엔드 개발자 " + id);
        return j;
    }

    @Test
    @DisplayName("한도 초과(429)를 받으면 다음 주기부터 한동안 API 를 다시 부르지 않는다")
    void 한도_초과면_쉰다() {
        when(embedder.isAvailable()).thenReturn(true);
        when(embedder.modelVersion()).thenReturn("gemini-embedding-001-512");
        when(repo.findStale(anyString(), anyString(), any())).thenReturn(List.of(job(1L), job(2L)));
        when(embedder.embedAll(anyList())).thenReturn(Arrays.asList(null, null));
        when(embedder.lastFailure()).thenReturn("HTTP 429 TOO_MANY_REQUESTS @ 2026-11-09T16:29:46");

        indexer.indexBatch();
        indexer.indexBatch();

        verify(embedder, times(1)).embedAll(anyList());
        assertNotNull(indexer.pausedUntil());
        assertFalse(indexer.isCaughtUp());
    }

    @Test
    @DisplayName("다른 실패(일시 오류)는 쉬지 않고 다음 주기에 다시 시도한다")
    void 일시_오류는_재시도() {
        when(embedder.isAvailable()).thenReturn(true);
        when(embedder.modelVersion()).thenReturn("v");
        when(repo.findStale(anyString(), anyString(), any())).thenReturn(List.of(job(1L)));
        when(embedder.embedAll(anyList())).thenReturn(Arrays.asList((float[]) null));
        when(embedder.lastFailure()).thenReturn("HTTP 503 SERVICE_UNAVAILABLE @ 2026-11-09T16:29:46");

        indexer.indexBatch();
        indexer.indexBatch();

        verify(embedder, times(2)).embedAll(anyList());
        assertNull(indexer.pausedUntil());
    }

    @Test
    @DisplayName("남은 대상이 없으면 따라잡은 것으로 본다")
    void 대상_없으면_완료() {
        when(embedder.isAvailable()).thenReturn(true);
        when(embedder.modelVersion()).thenReturn("v");
        when(repo.findStale(anyString(), anyString(), any())).thenReturn(List.of());

        indexer.indexBatch();

        assertTrue(indexer.isCaughtUp());
    }
}
