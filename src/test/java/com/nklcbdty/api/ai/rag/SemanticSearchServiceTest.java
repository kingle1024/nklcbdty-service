package com.nklcbdty.api.ai.rag;

import com.nklcbdty.common.vo.Job_mst;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SemanticSearchServiceTest {

    private final EmbeddingService embedder = mock(EmbeddingService.class);
    private final JobEmbeddingCache cache = mock(JobEmbeddingCache.class);
    private final JobEmbeddingRepository repo = mock(JobEmbeddingRepository.class);
    private final KeywordJobMatcher keywords = mock(KeywordJobMatcher.class);
    private final JobEmbeddingIndexer indexer = mock(JobEmbeddingIndexer.class);
    private final SemanticSearchService service = new SemanticSearchService(embedder, cache, repo, keywords, indexer);

    private static Job_mst job(long id) {
        Job_mst j = new Job_mst();
        j.setId(id);
        return j;
    }

    @Test
    @DisplayName("임베딩 인덱스가 0건이면(쿼터 소진) 키워드 매칭 결과를 준다")
    void 인덱스_없으면_키워드() {
        when(embedder.isAvailable()).thenReturn(true);
        when(cache.size()).thenReturn(0);
        when(keywords.search(anyString(), anyInt())).thenReturn(List.of(new KeywordJobMatcher.Hit(7L, 0.4f)));
        when(repo.findAllById(List.of(7L))).thenReturn(List.of(job(7L)));

        List<SemanticSearchService.Result> out = service.search("Java 백엔드", 5);

        assertEquals(1, out.size());
        assertEquals(7L, out.get(0).job.getId());
        assertEquals(0.4f, out.get(0).score);
        verify(embedder, never()).embed(any());
    }

    @Test
    @DisplayName("질의 임베딩이 실패해도(429) 키워드 매칭으로 대신한다")
    void 질의_임베딩_실패() {
        when(embedder.isAvailable()).thenReturn(true);
        when(cache.size()).thenReturn(100);
        when(indexer.isCaughtUp()).thenReturn(true);
        when(embedder.embed(anyString())).thenReturn(null);
        when(keywords.search(anyString(), anyInt())).thenReturn(List.of(new KeywordJobMatcher.Hit(8L, 0.3f)));
        when(repo.findAllById(List.of(8L))).thenReturn(List.of(job(8L)));

        assertEquals(8L, service.search("React", 3).get(0).job.getId());
    }

    @Test
    @DisplayName("의미 검색이 되면 키워드 매칭은 부르지 않는다")
    void 의미검색_우선() {
        when(embedder.isAvailable()).thenReturn(true);
        when(cache.size()).thenReturn(100);
        when(indexer.isCaughtUp()).thenReturn(true);
        when(embedder.embed(anyString())).thenReturn(new float[]{1f, 0f});
        when(cache.search(any(), anyInt())).thenReturn(List.of(new JobEmbeddingCache.Hit(9L, 0.6f)));
        when(repo.findAllById(List.of(9L))).thenReturn(List.of(job(9L)));

        assertEquals(9L, service.search("Kafka", 3).get(0).job.getId());
        verify(keywords, never()).search(anyString(), anyInt());
    }

    @Test
    @DisplayName("색인을 다 따라잡기 전에는(공급자 교체 직후) 일부 공고만으로 고르지 않고 키워드 매칭을 쓴다")
    void 색인_중이면_키워드() {
        when(embedder.isAvailable()).thenReturn(true);
        when(cache.size()).thenReturn(50);
        when(indexer.isCaughtUp()).thenReturn(false);
        when(keywords.search(anyString(), anyInt())).thenReturn(List.of(new KeywordJobMatcher.Hit(5L, 0.5f)));
        when(repo.findAllById(List.of(5L))).thenReturn(List.of(job(5L)));

        assertEquals(5L, service.search("Java", 3).get(0).job.getId());
        verify(embedder, never()).embed(any());
    }

    private static Job_mst posting(long id, String company, String annoId) {
        Job_mst j = job(id);
        j.setCompanyCd(company);
        j.setAnnoId(annoId);
        return j;
    }

    @Test
    @DisplayName("같은 공고가 여러 행으로 들어 있어도 한 번만 보여 주고, 빈자리는 다음 공고로 채운다")
    void 같은_공고_중복_제거() {
        when(embedder.isAvailable()).thenReturn(true);
        when(cache.size()).thenReturn(100);
        when(indexer.isCaughtUp()).thenReturn(true);
        when(embedder.embed(anyString())).thenReturn(new float[]{1f, 0f});
        when(cache.search(any(), anyInt())).thenReturn(List.of(
                new JobEmbeddingCache.Hit(1L, 0.9f), new JobEmbeddingCache.Hit(2L, 0.9f),
                new JobEmbeddingCache.Hit(3L, 0.9f), new JobEmbeddingCache.Hit(4L, 0.8f),
                new JobEmbeddingCache.Hit(5L, 0.7f), new JobEmbeddingCache.Hit(6L, 0.6f)));
        when(repo.findAllById(List.of(1L, 2L, 3L, 4L, 5L, 6L))).thenReturn(List.of(
                posting(1L, "TOSS", "6434513003"), posting(2L, "TOSS", "6434513003"),
                posting(3L, "TOSS", "6434513003"),
                posting(4L, "KAKAO", "6434513003"), // 회사가 다르면 번호가 같아도 다른 공고
                posting(5L, "NAVER", null), posting(6L, "NAVER", null))); // 번호 없으면 합치지 않는다

        List<SemanticSearchService.Result> out = service.search("재무 데이터", 4);

        assertEquals(List.of(1L, 4L, 5L, 6L), out.stream().map(r -> r.job.getId()).toList());
    }

    @Test
    @DisplayName("색인 뒤에 마감된 공고는 의미 검색 결과에서 뺀다")
    void 마감_공고_제외() {
        when(embedder.isAvailable()).thenReturn(true);
        when(cache.size()).thenReturn(100);
        when(indexer.isCaughtUp()).thenReturn(true);
        when(embedder.embed(anyString())).thenReturn(new float[]{1f, 0f});
        when(cache.search(any(), anyInt())).thenReturn(List.of(
                new JobEmbeddingCache.Hit(1L, 0.9f), new JobEmbeddingCache.Hit(2L, 0.8f)));
        Job_mst closed = job(1L);
        closed.setEndDate("2020-01-01 00:00:00");
        Job_mst open = job(2L);
        open.setEndDate("2999-12-31 23:59:59");
        when(repo.findAllById(List.of(1L, 2L))).thenReturn(List.of(closed, open));

        List<SemanticSearchService.Result> out = service.search("Java", 3);

        assertEquals(List.of(2L), out.stream().map(r -> r.job.getId()).toList());
    }
}
