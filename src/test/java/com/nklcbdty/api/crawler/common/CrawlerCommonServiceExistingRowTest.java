package com.nklcbdty.api.crawler.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.nklcbdty.api.ai.nlp.PersonalHistoryEnsemble;
import com.nklcbdty.api.ai.service.GeminiService;
import com.nklcbdty.common.crawler.repository.CrawlerRepository;
import com.nklcbdty.common.vo.Job_mst;

import reactor.core.publisher.Mono;

/**
 * 크롤에 다시 잡힌 "기존 row" 를 되살리는 경로 검증.
 *
 * <p>회사가 채용 사이트를 옮기면 기존 row 는 죽은 링크를 들고 있다가 링크 점검 배치에 종료 처리되고,
 * 새 크롤러가 같은 공고를 다시 긁어와도 신규가 아니라는 이유로 아무것도 갱신되지 않아
 * 목록에서 사라진 채로 남았다(당근 이관 때 38건 중 37건이 이 상태였다).</p>
 */
class CrawlerCommonServiceExistingRowTest {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Mock
    private CrawlerRepository crawlerRepository;
    @Mock
    private GeminiService geminiService;
    @Mock
    private PersonalHistoryEnsemble personalHistoryEnsemble;

    @Captor
    private ArgumentCaptor<List<Job_mst>> savedCaptor;

    private CrawlerCommonService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new CrawlerCommonService(crawlerRepository, geminiService, personalHistoryEnsemble);
        when(geminiService.classifyExperienceLevels(anyList())).thenReturn(Mono.just(Collections.emptyMap()));
        when(geminiService.classifyJobTitles(anyList())).thenReturn(Mono.just(Collections.emptyMap()));
    }

    private Job_mst job(String annoId, String subject) {
        Job_mst job = new Job_mst();
        job.setAnnoId(annoId);
        job.setAnnoSubject(subject);
        return job;
    }

    @Test
    void 종료로_찍힌_상시채용_공고가_크롤에_다시_잡히면_되살린다() {
        Job_mst existing = job("6498758003", "Software Engineer, Backend - 숏폼");
        existing.setEndDate(LocalDateTime.now().minusDays(1).format(FORMATTER)); // 링크 점검 배치가 종료 처리
        existing.setSubJobCdNm("Backend");
        existing.setJobDetailLink("https://about.daangn.com?gh_jid=6498758003");

        Job_mst crawled = job("6498758003", "Software Engineer, Backend - 숏폼");
        crawled.setSubJobCdNm("Backend");
        crawled.setJobDetailLink("https://careers.daangn.com/jobs/role/6498758003/");
        // 상시채용이라 크롤러가 마감일을 주지 않는다.

        when(crawlerRepository.findAllByAnnoIdIn(anyList())).thenReturn(List.of(existing));
        when(crawlerRepository.findAllByCompanyCd("DAANGN")).thenReturn(List.of(existing));

        List<Job_mst> toSave = service.getNotSaveJobItem("DAANGN", List.of(crawled));

        assertTrue(toSave.isEmpty(), "이미 있는 공고는 신규 저장 대상이 아니다");
        verify(crawlerRepository).saveAll(savedCaptor.capture());
        Job_mst updated = savedCaptor.getValue().get(0);
        assertNull(updated.getEndDate(), "크롤에 잡혔으면 상시채용으로 되살아나야 한다");
        // 죽은 링크를 계속 들고 있으면 다음 링크 점검에서 또 종료 처리된다.
        assertEquals("https://careers.daangn.com/jobs/role/6498758003/", updated.getJobDetailLink());
    }

    @Test
    void 크롤러가_준_마감일이_있으면_그_값을_쓴다() {
        Job_mst existing = job("25497", "Server(배차시스템)");
        existing.setEndDate(LocalDateTime.now().minusDays(1).format(FORMATTER));
        existing.setSubJobCdNm("Backend");

        Job_mst crawled = job("25497", "Server(배차시스템)");
        crawled.setEndDate("2999-12-31 00:00:00");
        crawled.setSubJobCdNm("Backend");

        when(crawlerRepository.findAllByAnnoIdIn(anyList())).thenReturn(List.of(existing));
        when(crawlerRepository.findAllByCompanyCd("BAEMIN")).thenReturn(List.of(existing));

        service.getNotSaveJobItem("BAEMIN", List.of(crawled));

        verify(crawlerRepository).saveAll(savedCaptor.capture());
        assertEquals("2999-12-31 00:00:00", savedCaptor.getValue().get(0).getEndDate());
    }

    @Test
    void 아직_안_끝난_마감일은_건드리지_않는다() {
        Job_mst existing = job("111", "살아있는 공고");
        String future = LocalDateTime.now().plusDays(10).format(FORMATTER);
        existing.setEndDate(future);
        existing.setSubJobCdNm("Backend");
        existing.setJobDetailLink("https://example.com/111");

        Job_mst crawled = job("111", "살아있는 공고");
        crawled.setSubJobCdNm("Backend");
        crawled.setJobDetailLink("https://example.com/111");

        when(crawlerRepository.findAllByAnnoIdIn(anyList())).thenReturn(List.of(existing));
        when(crawlerRepository.findAllByCompanyCd("TOSS")).thenReturn(List.of(existing));

        service.getNotSaveJobItem("TOSS", List.of(crawled));

        verify(crawlerRepository, never()).saveAll(anyList());
        assertEquals(future, existing.getEndDate());
    }

    @Test
    void 분류가_비어있는_기존_row_는_LLM_분류를_다시_돌린다() {
        Job_mst existing = job("25548", "로보틱스 S/W 엔지니어링(자율주행 ML/팀장급)");
        existing.setSubJobCdNm(null); // 크롤러 키워드 규칙이 못 맞춰 null 로 저장돼 있던 row
        existing.setEndDate("2999-12-31 00:00:00");

        Job_mst crawled = job("25548", "로보틱스 S/W 엔지니어링(자율주행 ML/팀장급)");
        crawled.setEndDate("2999-12-31 00:00:00");

        when(crawlerRepository.findAllByAnnoIdIn(anyList())).thenReturn(List.of(existing));
        when(crawlerRepository.findAllByCompanyCd("BAEMIN")).thenReturn(List.of(existing));
        when(geminiService.classifyJobTitles(anyList()))
            .thenReturn(Mono.just(Map.of("로보틱스 S/W 엔지니어링(자율주행 ML/팀장급)", "ML")));

        service.getNotSaveJobItem("BAEMIN", List.of(crawled));

        verify(crawlerRepository).saveAll(savedCaptor.capture());
        assertEquals("ML", savedCaptor.getValue().get(0).getSubJobCdNm());
    }

    @Test
    void 이미_분류된_기존_row_는_LLM_을_다시_부르지_않는다() {
        Job_mst existing = job("222", "이미 분류된 공고");
        existing.setSubJobCdNm("Backend");
        existing.setEndDate("2999-12-31 00:00:00");

        Job_mst crawled = job("222", "이미 분류된 공고");
        crawled.setEndDate("2999-12-31 00:00:00");

        when(crawlerRepository.findAllByAnnoIdIn(anyList())).thenReturn(List.of(existing));
        when(crawlerRepository.findAllByCompanyCd("KAKAO")).thenReturn(List.of(existing));

        service.getNotSaveJobItem("KAKAO", List.of(crawled));

        verify(geminiService, never()).classifyJobTitles(anyList());
    }

    @Test
    void 신규_공고는_기존_row_갱신_대상이_아니다() {
        Job_mst crawled = job("999", "새로 올라온 공고");

        when(crawlerRepository.findAllByAnnoIdIn(anyList())).thenReturn(Collections.emptyList());
        when(crawlerRepository.findAllByCompanyCd(anyString())).thenReturn(Collections.emptyList());

        List<Job_mst> toSave = service.getNotSaveJobItem("DAANGN", List.of(crawled));

        assertEquals(1, toSave.size());
        assertEquals("DAANGN", toSave.get(0).getCompanyCd());
        verify(crawlerRepository, never()).saveAll(anyList());
    }
}
