package com.nklcbdty.api.crawler.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.nklcbdty.api.crawler.common.CrawlerCommonService;
import com.nklcbdty.api.crawler.dto.PersonalHistoryDto;
import com.nklcbdty.common.vo.Job_mst;

/**
 * 당근 크롤러가 Greenhouse 보드 응답을 제대로 읽는지 확인한다.
 * (about.daangn.com 의 page-data.json 이 404 가 되면서 보드 API 로 교체됐다)
 */
class DaangnJobCrawlerServiceTest {

    @Mock
    private CrawlerCommonService crawlerCommonService;

    private DaangnJobCrawlerService daangnJobCrawlerService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        daangnJobCrawlerService = new DaangnJobCrawlerService(crawlerCommonService);

        PersonalHistoryDto history = new PersonalHistoryDto();
        history.setFrom(3);
        history.setTo(7);
        when(crawlerCommonService.getPersonalHistory(anyString())).thenReturn(history);
        // getNotSaveJobItem 은 DB 접근이라 여기서는 파싱 결과를 그대로 돌려주게 둔다.
        when(crawlerCommonService.getNotSaveJobItem(eq("DAANGN"), anyList()))
            .thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    void 보드_응답을_공고로_변환한다() throws Exception {
        when(crawlerCommonService.fetchApiResponse(anyString())).thenReturn(boardResponse());

        List<Job_mst> jobs = daangnJobCrawlerService.crawlJobs().get();

        assertEquals(2, jobs.size());

        Job_mst backend = jobs.get(0);
        assertEquals("4351200003", backend.getAnnoId());
        assertEquals("Software Engineer, Backend - 광고", backend.getAnnoSubject());
        assertEquals("https://careers.daangn.com/jobs/role/4351200003/", backend.getJobDetailLink());
        assertEquals("정규", backend.getEmpTypeCdNm());
        // Corporate 가 "당근" 으로 와도 화면 표기는 "당근마켓" 으로 통일한다.
        assertEquals("당근마켓", backend.getSysCompanyCdNm());
        assertEquals("Software Engineer", backend.getClassCdNm());
        assertEquals("Backend", backend.getSubJobCdNm());
        assertEquals(3, backend.getPersonalHistory());
        assertEquals(7, backend.getPersonalHistoryEnd());
        // Valid Through 가 비어 있으면 상시채용으로 둔다.
        assertNull(backend.getEndDate());

        Job_mst designer = jobs.get(1);
        assertEquals("6589282003", designer.getAnnoId());
        assertEquals("계약직", designer.getEmpTypeCdNm());
        assertEquals("Design", designer.getClassCdNm());
        assertEquals("2026-12-31 23:59:59", designer.getEndDate());
    }

    @Test
    void 필수값이_없는_공고는_건너뛴다() throws Exception {
        when(crawlerCommonService.fetchApiResponse(anyString())).thenReturn(
            "{\"jobs\":[{\"id\":123,\"title\":\"\"},{\"title\":\"제목만 있는 공고\"}]}");

        List<Job_mst> jobs = daangnJobCrawlerService.crawlJobs().get();

        assertEquals(0, jobs.size());
    }

    @Test
    void 응답_형식이_바뀌면_빈_목록을_돌려준다() throws Exception {
        when(crawlerCommonService.fetchApiResponse(anyString())).thenReturn("{\"message\":\"Not Found\"}");

        List<Job_mst> jobs = daangnJobCrawlerService.crawlJobs().get();

        assertEquals(0, jobs.size());
    }

    private String boardResponse() {
        return "{\"jobs\":["
            + "{"
            + "  \"id\": 4351200003,"
            + "  \"title\": \"Software Engineer, Backend - 광고\","
            + "  \"content\": \"&lt;p&gt;백엔드 개발 경력 3년 이상&lt;/p&gt;\","
            + "  \"departments\": [{\"id\": 4057801003, \"name\": \"Software Engineer, Backend\"}],"
            + "  \"metadata\": ["
            + "    {\"name\": \"Corporate\", \"value\": \"당근\"},"
            + "    {\"name\": \"Employment Type\", \"value\": \"정규직\"},"
            + "    {\"name\": \"Valid Through\", \"value\": \"\"}"
            + "  ]"
            + "},"
            + "{"
            + "  \"id\": 6589282003,"
            + "  \"title\": \"Brand Designer (계약직) - 브랜딩\","
            + "  \"content\": \"&lt;p&gt;브랜드 디자인&lt;/p&gt;\","
            + "  \"departments\": [{\"id\": 4045616003, \"name\": \"Design\"}],"
            + "  \"metadata\": ["
            + "    {\"name\": \"Corporate\", \"value\": \"당근마켓\"},"
            + "    {\"name\": \"Employment Type\", \"value\": \"계약직\"},"
            + "    {\"name\": \"Valid Through\", \"value\": \"2026-12-31\"}"
            + "  ]"
            + "}"
            + "]}";
    }
}
