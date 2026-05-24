package com.nklcbdty.api.crawler.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.nklcbdty.api.crawler.common.CrawlerCommonService;
import com.nklcbdty.api.crawler.dto.PersonalHistoryDto;
import com.nklcbdty.api.crawler.vo.Job_mst;

class KakaoCrawlerServiceTest {

    private static final String KAKAOBANK_RECRUITS_URL = "https://recruit.kakaobank.com/api/recruits";

    private CrawlerCommonService crawlerCommonService;
    private KakaoCrawlerService kakaoCrawlerService;

    @BeforeEach
    void setUp() {
        crawlerCommonService = mock(CrawlerCommonService.class);
        kakaoCrawlerService = new KakaoCrawlerService(crawlerCommonService);

        PersonalHistoryDto history = new PersonalHistoryDto();
        history.setFrom(3);
        history.setTo(7);
        when(crawlerCommonService.getPersonalHistory(anyString())).thenReturn(history);
        when(crawlerCommonService.fetchApiResponse(anyString()))
            .thenReturn("{\"contents\":\"경력 3년 이상\"}");
    }

    @Test
    @DisplayName("새 POST API 응답을 Job_mst 리스트로 매핑한다")
    void addRecruitKakaoBank_parsesNewApiResponse() {
        String mockResponse = """
            {
                "paging": {
                    "pageNumber": 1,
                    "pageSize": 20,
                    "totalPages": 1,
                    "totalElements": 2
                },
                "list": [
                    {
                        "recruitNoticeSn": 253256,
                        "recruitNoticeName": "AI 품질 및 안전성 평가 담당자 (AI Quality & Safety Evaluation)",
                        "recruitNoticeUrl": "kakaobank.recruiter.co.kr/app/jobnotice/view?systemKindCode=MRS2&jobnoticeSn=253256",
                        "recruitTypeName": "일반채용",
                        "recruitClassName": "AI",
                        "receiveStartDatetime": "2099-01-01 00:00:00",
                        "receiveEndDatetime": "2099-12-31 23:59:59",
                        "sortOrder": 8
                    },
                    {
                        "recruitNoticeSn": 249294,
                        "recruitNoticeName": "LLMOps 엔지니어 (계약직)",
                        "recruitNoticeUrl": "kakaobank.recruiter.co.kr/app/jobnotice/view?systemKindCode=MRS2&jobnoticeSn=249294",
                        "recruitTypeName": "일반채용",
                        "recruitClassName": "AI",
                        "receiveStartDatetime": "2099-01-01 00:00:00",
                        "receiveEndDatetime": "2099-12-31 23:59:59",
                        "sortOrder": 46
                    }
                ]
            }
            """;
        when(crawlerCommonService.fetchApiResponsePost(eq(KAKAOBANK_RECRUITS_URL), anyString()))
            .thenReturn(mockResponse);

        List<Job_mst> result = new ArrayList<>();
        kakaoCrawlerService.addRecruitKakaoBank(result);

        assertThat(result).hasSize(2);

        Job_mst first = result.get(0);
        assertThat(first.getAnnoId()).isEqualTo("253256");
        assertThat(first.getAnnoSubject()).contains("AI 품질");
        assertThat(first.getEmpTypeCdNm()).isEqualTo("정규");
        assertThat(first.getClassCdNm()).isEqualTo("Tech");
        assertThat(first.getSubJobCdNm()).isEqualTo("AI");
        assertThat(first.getSysCompanyCdNm()).isEqualTo("카카오 뱅크");
        assertThat(first.getJobDetailLink()).isEqualTo(
            "https://kakaobank.recruiter.co.kr/app/jobnotice/view?systemKindCode=MRS2&jobnoticeSn=253256");
        assertThat(first.getEndDate()).isEqualTo("2099-12-31 23:59:59");
        assertThat(first.getPersonalHistory()).isEqualTo(3L);
        assertThat(first.getPersonalHistoryEnd()).isEqualTo(7L);

        Job_mst second = result.get(1);
        assertThat(second.getAnnoId()).isEqualTo("249294");
        assertThat(second.getEmpTypeCdNm()).isEqualTo("비정규");
    }

    @Test
    @DisplayName("POST body에 pageNumber/pageSize가 포함된다")
    void addRecruitKakaoBank_sendsPaginationInPostBody() {
        String emptyPage = "{\"paging\":{\"totalPages\":1},\"list\":[]}";
        when(crawlerCommonService.fetchApiResponsePost(anyString(), anyString())).thenReturn(emptyPage);

        kakaoCrawlerService.addRecruitKakaoBank(new ArrayList<>());

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(crawlerCommonService).fetchApiResponsePost(urlCaptor.capture(), bodyCaptor.capture());

        assertThat(urlCaptor.getValue()).isEqualTo(KAKAOBANK_RECRUITS_URL);
        assertThat(bodyCaptor.getValue())
            .contains("\"pageNumber\":1")
            .contains("\"pageSize\":20");
    }

    @Test
    @DisplayName("마감일이 지난 공고는 결과에서 제외된다")
    void addRecruitKakaoBank_skipsExpiredItems() {
        String mockResponse = """
            {
                "paging": {"totalPages": 1},
                "list": [
                    {
                        "recruitNoticeSn": 1,
                        "recruitNoticeName": "만료된 공고",
                        "recruitNoticeUrl": "kakaobank.recruiter.co.kr/app/jobnotice/view?jobnoticeSn=1",
                        "recruitClassName": "AI",
                        "receiveEndDatetime": "2000-01-01 00:00:00"
                    },
                    {
                        "recruitNoticeSn": 2,
                        "recruitNoticeName": "유효한 공고",
                        "recruitNoticeUrl": "kakaobank.recruiter.co.kr/app/jobnotice/view?jobnoticeSn=2",
                        "recruitClassName": "AI",
                        "receiveEndDatetime": "2099-12-31 23:59:59"
                    }
                ]
            }
            """;
        when(crawlerCommonService.fetchApiResponsePost(anyString(), anyString())).thenReturn(mockResponse);

        List<Job_mst> result = new ArrayList<>();
        kakaoCrawlerService.addRecruitKakaoBank(result);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAnnoId()).isEqualTo("2");
    }

    @Test
    @DisplayName("totalPages 값을 따라 여러 페이지를 순회한다")
    void addRecruitKakaoBank_iteratesMultiplePages() {
        String page1 = """
            {
                "paging": {"totalPages": 2},
                "list": [
                    {
                        "recruitNoticeSn": 100,
                        "recruitNoticeName": "공고 A",
                        "recruitNoticeUrl": "kakaobank.recruiter.co.kr/app/jobnotice/view?jobnoticeSn=100",
                        "recruitClassName": "Backend",
                        "receiveEndDatetime": "2099-12-31 23:59:59"
                    }
                ]
            }
            """;
        String page2 = """
            {
                "paging": {"totalPages": 2},
                "list": [
                    {
                        "recruitNoticeSn": 200,
                        "recruitNoticeName": "공고 B",
                        "recruitNoticeUrl": "kakaobank.recruiter.co.kr/app/jobnotice/view?jobnoticeSn=200",
                        "recruitClassName": "Backend",
                        "receiveEndDatetime": "2099-12-31 23:59:59"
                    }
                ]
            }
            """;
        when(crawlerCommonService.fetchApiResponsePost(anyString(), anyString()))
            .thenReturn(page1)
            .thenReturn(page2);

        List<Job_mst> result = new ArrayList<>();
        kakaoCrawlerService.addRecruitKakaoBank(result);

        assertThat(result).extracting(Job_mst::getAnnoId).containsExactly("100", "200");

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(crawlerCommonService, times(2))
            .fetchApiResponsePost(eq(KAKAOBANK_RECRUITS_URL), bodyCaptor.capture());
        assertThat(bodyCaptor.getAllValues()).containsExactly(
            "{\"pageNumber\":1,\"pageSize\":20}",
            "{\"pageNumber\":2,\"pageSize\":20}"
        );
    }

    @Test
    @DisplayName("recruitNoticeUrl이 비어 있으면 기본 URL로 폴백한다")
    void addRecruitKakaoBank_fallbacksWhenRecruitNoticeUrlMissing() {
        String mockResponse = """
            {
                "paging": {"totalPages": 1},
                "list": [
                    {
                        "recruitNoticeSn": 999,
                        "recruitNoticeName": "URL 없는 공고",
                        "recruitClassName": "AI",
                        "receiveEndDatetime": "2099-12-31 23:59:59"
                    }
                ]
            }
            """;
        when(crawlerCommonService.fetchApiResponsePost(anyString(), anyString())).thenReturn(mockResponse);

        List<Job_mst> result = new ArrayList<>();
        kakaoCrawlerService.addRecruitKakaoBank(result);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getJobDetailLink())
            .isEqualTo("https://recruit.kakaobank.com/jobs/999");
    }
}
