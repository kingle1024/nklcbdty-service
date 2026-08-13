package com.nklcbdty.api.crawler.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
 * 야놀자 크롤러가 Workday CxS 응답을 제대로 읽는지 확인한다.
 * (careers.yanolja.co 의 _next/data 에서 공고가 빠지면서 Workday 로 교체됐다)
 */
class YanoljaCralwerServiceTest {

    private static final String JOBS_URL =
        "https://yanolja.wd102.myworkdayjobs.com/wday/cxs/yanolja/External_Yanolja/jobs";

    @Mock
    private CrawlerCommonService commonService;

    private YanoljaCralwerService yanoljaCralwerService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        yanoljaCralwerService = new YanoljaCralwerService(commonService);

        PersonalHistoryDto history = new PersonalHistoryDto();
        history.setFrom(5);
        history.setTo(10);
        when(commonService.getPersonalHistory(anyString())).thenReturn(history);
        when(commonService.getNotSaveJobItem(eq("YANOLJA"), anyList()))
            .thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    void 목록과_상세를_합쳐_공고로_변환한다() throws Exception {
        when(commonService.fetchApiResponsePost(eq(JOBS_URL), anyString())).thenReturn(jobsPage());
        when(commonService.fetchApiResponse(contains("/job/Seoul/Backend-Engineer_JR100"))).thenReturn(detail());

        List<Job_mst> jobs = yanoljaCralwerService.crawlJobs().get();

        assertEquals(1, jobs.size());
        Job_mst job = jobs.get(0);
        assertEquals("JR100", job.getAnnoId());
        assertEquals("Software Engineer, Backend", job.getAnnoSubject());
        assertEquals(
            "https://yanolja.wd102.myworkdayjobs.com/ko-KR/External_Yanolja/job/Seoul/Backend-Engineer_JR100",
            job.getJobDetailLink());
        assertEquals("야놀자", job.getSysCompanyCdNm());
        assertEquals("정규", job.getEmpTypeCdNm());
        assertEquals("2026-07-31", job.getStartDate());
        assertEquals(5, job.getPersonalHistory());
        assertEquals(10, job.getPersonalHistoryEnd());
        // Workday 공고에는 마감일이 없다. 상시채용으로 두고 사라지면 종료 처리된다.
        assertNull(job.getEndDate());
        // 제목 키워드 보정
        assertEquals("Backend", job.getSubJobCdNm());
    }

    @Test
    void 상세_조회가_실패해도_공고는_살린다() throws Exception {
        when(commonService.fetchApiResponsePost(eq(JOBS_URL), anyString())).thenReturn(jobsPage());
        when(commonService.fetchApiResponse(anyString())).thenThrow(new RuntimeException("boom"));

        List<Job_mst> jobs = yanoljaCralwerService.crawlJobs().get();

        assertEquals(1, jobs.size());
        assertEquals("JR100", jobs.get(0).getAnnoId());
        assertNull(jobs.get(0).getEmpTypeCdNm());
    }

    @Test
    void 공고가_0건이면_상세를_조회하지_않는다() throws Exception {
        when(commonService.fetchApiResponsePost(eq(JOBS_URL), anyString()))
            .thenReturn("{\"total\":0,\"jobPostings\":[]}");

        List<Job_mst> jobs = yanoljaCralwerService.crawlJobs().get();

        assertEquals(0, jobs.size());
        verify(commonService, never()).fetchApiResponse(anyString());
        // 빈 페이지에서 바로 멈춰야 한다(페이지 상한까지 계속 때리지 않도록).
        verify(commonService, times(1)).fetchApiResponsePost(eq(JOBS_URL), anyString());
    }

    @Test
    void 응답_형식이_바뀌면_빈_목록을_돌려준다() throws Exception {
        when(commonService.fetchApiResponsePost(eq(JOBS_URL), anyString()))
            .thenReturn("{\"errorCode\":\"S21\",\"httpStatus\":404}");

        List<Job_mst> jobs = yanoljaCralwerService.crawlJobs().get();

        assertEquals(0, jobs.size());
        verify(commonService, times(1)).fetchApiResponsePost(eq(JOBS_URL), anyString());
    }

    private String jobsPage() {
        return "{\"total\":1,\"jobPostings\":["
            + "{"
            + "  \"title\": \"Software Engineer, Backend\","
            + "  \"externalPath\": \"/job/Seoul/Backend-Engineer_JR100\","
            + "  \"locationsText\": \"Seoul\","
            + "  \"postedOn\": \"Posted Yesterday\","
            + "  \"bulletFields\": [\"JR100\"]"
            + "},"
            // 공고번호가 없는 항목은 건너뛴다.
            + "{"
            + "  \"title\": \"공고번호 없는 공고\","
            + "  \"externalPath\": \"/job/Seoul/No-Id\","
            + "  \"bulletFields\": []"
            + "}"
            + "]}";
    }

    private String detail() {
        return "{\"jobPostingInfo\":{"
            + "  \"jobReqId\": \"JR100\","
            + "  \"title\": \"Software Engineer, Backend\","
            + "  \"timeType\": \"Full time\","
            + "  \"startDate\": \"2026-07-31\","
            + "  \"jobDescription\": \"&lt;p&gt;백엔드 개발 경력 5년 이상 10년 이하&lt;/p&gt;\""
            + "}}";
    }
}
