package com.nklcbdty.api.crawler.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * 쿠팡 크롤러가 Greenhouse 보드 응답을 제대로 읽는지 확인한다.
 * (www.coupang.jobs 가 Cloudflare 봇 차단 뒤로 들어가 목록 HTML 스크래핑이 서버에서 항상 403 이 되면서
 *  같은 공고를 담고 있는 Greenhouse 공개 board API 로 교체됐다)
 */
class CoupangJobCrawlerServiceTest {

    @Mock
    private CrawlerCommonService crawlerCommonService;

    private CoupangJobCrawlerService coupangJobCrawlerService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        coupangJobCrawlerService = new CoupangJobCrawlerService(crawlerCommonService);

        PersonalHistoryDto history = new PersonalHistoryDto();
        history.setFrom(5);
        history.setTo(10);
        when(crawlerCommonService.getPersonalHistory(anyString())).thenReturn(history);
        // getNotSaveJobItem 은 DB 접근이라 여기서는 파싱 결과를 그대로 돌려주게 둔다.
        when(crawlerCommonService.getNotSaveJobItem(eq("COUPANG"), anyList()))
            .thenAnswer(invocation -> invocation.getArgument(1));
    }

    private void stubBoard(String listJson) {
        when(crawlerCommonService.fetchApiResponse(anyString())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            if (url.contains("content=false")) {
                return listJson;
            }
            return "{\"id\":1,\"content\":\"&lt;p&gt;백엔드 개발 경력 5년 이상&lt;/p&gt;\"}";
        });
    }

    @Test
    void 보드_응답을_공고로_변환한다() throws Exception {
        stubBoard(boardResponse());

        List<Job_mst> jobs = coupangJobCrawlerService.crawlJobs().get();

        assertEquals(2, jobs.size());

        Job_mst backend = jobs.get(0);
        // annoId 는 gh_jid — 예전 HTML 크롤러가 쓰던 data-id 와 같은 값이라 저장된 행과 이어진다.
        assertEquals("8096053", backend.getAnnoId());
        assertEquals("[쿠팡] Backend Engineer", backend.getAnnoSubject());
        assertEquals("https://www.coupang.jobs/kr/jobs/?gh_jid=8096053", backend.getJobDetailLink());
        assertEquals("서울", backend.getWorkplace());
        assertEquals("쿠팡", backend.getSysCompanyCdNm());
        assertEquals("Backend", backend.getSubJobCdNm());
        assertEquals(5, backend.getPersonalHistory());
        assertEquals(10, backend.getPersonalHistoryEnd());

        // 제목의 대괄호 표기로 계열사를 갈라내던 기존 동작은 그대로 유지한다.
        Job_mst play = jobs.get(1);
        assertEquals("쿠팡플레이", play.getSysCompanyCdNm());
    }

    @Test
    void 서울이_아닌_공고는_제외한다() throws Exception {
        stubBoard("{\"jobs\":["
            + "{\"id\":1,\"title\":\"[쿠팡] Backend Engineer\",\"location\":{\"name\":\"Seoul, South Korea\"}},"
            + "{\"id\":2,\"title\":\"Backend Engineer\",\"location\":{\"name\":\"Taipei, Taiwan\"}},"
            + "{\"id\":3,\"title\":\"Backend Engineer\",\"location\":{\"name\":\"Mountain View, USA\"}}"
            + "]}");

        List<Job_mst> jobs = coupangJobCrawlerService.crawlJobs().get();

        assertEquals(1, jobs.size());
        assertEquals("1", jobs.get(0).getAnnoId());
    }

    @Test
    void 필수값이_없는_공고는_건너뛴다() throws Exception {
        stubBoard("{\"jobs\":["
            + "{\"id\":123,\"title\":\"\",\"location\":{\"name\":\"Seoul, South Korea\"}},"
            + "{\"title\":\"번호 없는 공고\",\"location\":{\"name\":\"Seoul, South Korea\"}},"
            + "{\"id\":124,\"title\":\"지역 없는 공고\"}"
            + "]}");

        List<Job_mst> jobs = coupangJobCrawlerService.crawlJobs().get();

        assertEquals(0, jobs.size());
    }

    @Test
    void 응답_형식이_바뀌면_빈_목록을_돌려준다() throws Exception {
        stubBoard("{\"status\":404,\"error\":\"Job not found\"}");

        List<Job_mst> jobs = coupangJobCrawlerService.crawlJobs().get();

        assertEquals(0, jobs.size());
    }

    @Test
    void 상세_조회가_실패해도_공고는_살린다() throws Exception {
        when(crawlerCommonService.fetchApiResponse(anyString())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            if (url.contains("content=false")) {
                return boardResponse();
            }
            throw new RuntimeException("Cloudflare challenge");
        });

        List<Job_mst> jobs = coupangJobCrawlerService.crawlJobs().get();

        assertEquals(2, jobs.size());
        assertEquals(0, jobs.get(0).getPersonalHistory());
    }

    private String boardResponse() {
        return "{\"jobs\":["
            + "{"
            + "  \"id\": 8096053,"
            + "  \"title\": \"[쿠팡] Backend Engineer\","
            + "  \"location\": {\"name\": \"Seoul, South Korea\"},"
            + "  \"absolute_url\": \"https://www.coupang.jobs/en/jobs/?gh_jid=8096053\""
            + "},"
            + "{"
            + "  \"id\": 8127263,"
            + "  \"title\": \"[쿠팡플레이] QA Engineer\","
            + "  \"location\": {\"name\": \"Seoul, South Korea\"},"
            + "  \"absolute_url\": \"https://www.coupang.jobs/en/jobs/?gh_jid=8127263\""
            + "}"
            + "]}";
    }
}
