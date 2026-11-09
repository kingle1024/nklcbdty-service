package com.nklcbdty.api.ai.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KeywordJobMatcherTest {

    private static KeywordJobMatcher.JobDoc doc(long id, String text) {
        return new KeywordJobMatcher.JobDoc(id, KeywordJobMatcher.tokenize(text));
    }

    private static final List<KeywordJobMatcher.JobDoc> DOCS = List.of(
            doc(1, "[배민] 백엔드 개발자 (Java/Spring, Kafka) 서버 개발 판교"),
            doc(2, "프론트엔드 개발자 React TypeScript 웹 개발 서울"),
            doc(3, "데이터 엔지니어 Spark Airflow 데이터 개발 판교"),
            doc(4, "Go 언어 서버 개발자 서버 개발 판교"),
            doc(5, "마케팅 매니저 마케팅 서울"));

    private static List<Long> rankIds(String resume) {
        Map<String, Double> idf = KeywordJobMatcher.idfOf(DOCS);
        return KeywordJobMatcher.rank(DOCS, idf, resume, 5).stream().map(KeywordJobMatcher.Hit::jobId).toList();
    }

    @Test
    @DisplayName("이력서의 기술 스택과 많이 겹치는 공고가 먼저 온다")
    void 기술스택_순위() {
        List<Long> ids = rankIds("5년차 백엔드 개발자입니다. Java, Spring Boot, JPA, Kafka 로 결제 서버를 만들었습니다.");
        assertEquals(1L, ids.get(0));
        assertFalse(ids.contains(5L), "겹치는 낱말이 없는 공고는 빠진다");
    }

    @Test
    @DisplayName("한글 낱말은 조사가 붙어도 맞는다")
    void 조사() {
        List<Long> ids = rankIds("React와 TypeScript를 써서 프론트엔드를 개발했습니다");
        assertEquals(2L, ids.get(0));
    }

    @Test
    @DisplayName("영문 낱말은 통째로만 맞는다 — google 안의 go 는 Go 공고로 안 잡힌다")
    void 영문_부분일치_안함() {
        Map<String, Double> idf = KeywordJobMatcher.idfOf(DOCS);
        List<KeywordJobMatcher.Hit> hits = KeywordJobMatcher.rank(List.of(DOCS.get(3)), idf, "google cloud 경험", 5);
        assertTrue(hits.isEmpty());
    }

    @Test
    @DisplayName("이력서에 우연히 있는 드문 낱말이 직군이 다른 공고를 끌어올리지 않는다")
    void 직군_우선() {
        List<KeywordJobMatcher.JobDoc> docs = List.of(
                doc(1, "서버 개발자 Backend"),
                doc(2, "Service Operations Manager 정산"),
                doc(3, "DBA MySQL"));
        Map<String, Double> idf = KeywordJobMatcher.idfOf(docs);
        List<Long> ids = KeywordJobMatcher.rank(docs, idf,
                "Java, Spring Boot, JPA, MySQL, Redis, Kafka 로 결제·정산 서버를 개발했습니다", 3)
                .stream().map(KeywordJobMatcher.Hit::jobId).toList();
        assertEquals(List.of(1L), ids);
    }

    @Test
    @DisplayName("기술 스택만 적힌 이력서도 직군을 짐작한다")
    void 스택으로_직군() {
        assertEquals(java.util.Set.of("frontend"),
                KeywordJobMatcher.familiesOf("React, TypeScript, Next.js 로 디자인 시스템 구축"));
    }

    @Test
    @DisplayName("점수는 0~1 이다 (화면이 일치도 % 로 보여 준다)")
    void 점수_범위() {
        Map<String, Double> idf = KeywordJobMatcher.idfOf(DOCS);
        for (KeywordJobMatcher.Hit h : KeywordJobMatcher.rank(DOCS, idf, "백엔드 개발자 Java Spring Kafka 서버 개발 판교 백엔드를", 5)) {
            assertTrue(h.score() > 0 && h.score() <= 1.0f, "score=" + h.score());
        }
    }

    @Test
    @DisplayName("마감 지난 공고와 링크 점검 실패 표시는 빼고, 상시채용은 남긴다")
    void 마감판정() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 9, 12, 0);
        assertTrue(KeywordJobMatcher.isClosed("2026-11-08 23:59:59", now));
        assertTrue(KeywordJobMatcher.isClosed("2000-01-01 00:00", now));
        assertFalse(KeywordJobMatcher.isClosed("2026-12-01 23:59:59", now));
        assertFalse(KeywordJobMatcher.isClosed(null, now));
        assertFalse(KeywordJobMatcher.isClosed("영입종료시", now));
    }

    @Test
    @DisplayName("DB 에서 읽어 마감된 공고를 빼고 순위를 매긴다")
    void 적재_후_검색() {
        JobEmbeddingRepository repo = mock(JobEmbeddingRepository.class);
        when(repo.findKeywordRows()).thenReturn(List.of(
                new Object[]{10L, "백엔드 개발자", "Server", null, "배민", "판교", null},
                new Object[]{11L, "백엔드 개발자 Java", "Server", null, "배민", "판교", "2000-01-01 00:00"},
                new Object[]{12L, "디자이너", "Design", null, "토스", "서울", null}));

        List<KeywordJobMatcher.Hit> hits = new KeywordJobMatcher(repo).search("Java 백엔드 개발", 5);

        assertEquals(List.of(10L), hits.stream().map(KeywordJobMatcher.Hit::jobId).toList());
    }
}
