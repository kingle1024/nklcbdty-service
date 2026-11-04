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
import com.nklcbdty.common.vo.Job_mst;

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

    /**
     * 카카오모빌리티 채용 사이트(greetinghr) 목록 페이지의 {@code __NEXT_DATA__} 축약본.
     * queries 안에서 openings 가 마지막에 오는 것도 실제 응답과 같다 (인덱스로 찍으면 안 되는 이유).
     */
    private static final String MOBILITY_NEXT_DATA = """
        {
          "props": {
            "pageProps": {
              "dehydratedState": {
                "queries": [
                  { "queryKey": ["publicCareer", "getCareerBaseInfo", "kakaomobility"], "state": { "data": {} } },
                  {
                    "queryKey": ["openings"],
                    "state": {
                      "data": [
                        {
                          "openingId": 235808,
                          "title": "[집중채용] 자율주행 Research Engineer (Ph.D) ",
                          "dueDate": "2099-09-28T02:59:59Z",
                          "openingJobPosition": {
                            "openingJobPositions": [
                              {
                                "workspaceJob": { "job": "개발" },
                                "jobPositionCareer": { "careerFrom": null, "careerTo": null, "careerType": "NOT_MATTER" },
                                "jobPositionEmployment": { "employmentType": "FULL_TIME_WORKER" }
                              }
                            ]
                          }
                        },
                        {
                          "openingId": 227958,
                          "title": "[Contract] 자율주행 HW 테크니션",
                          "dueDate": null,
                          "openingJobPosition": {
                            "openingJobPositions": [
                              {
                                "workspaceJob": { "job": "개발" },
                                "jobPositionCareer": { "careerFrom": 3, "careerTo": 10, "careerType": "EXPERIENCED" },
                                "jobPositionEmployment": { "employmentType": "CONTRACT_WORKER" }
                              }
                            ]
                          }
                        }
                      ]
                    }
                  }
                ]
              }
            }
          }
        }
        """;

    @Test
    @DisplayName("카카오모빌리티: __NEXT_DATA__ 의 openings 를 Job_mst 로 매핑한다")
    void parseMobilityOpenings_mapsOpenings() {
        List<Job_mst> result = new ArrayList<>();
        kakaoCrawlerService.parseMobilityOpenings(MOBILITY_NEXT_DATA, result);

        assertThat(result).hasSize(2);

        Job_mst first = result.get(0);
        assertThat(first.getAnnoId()).isEqualTo("235808");
        assertThat(first.getAnnoSubject()).isEqualTo("[집중채용] 자율주행 Research Engineer (Ph.D)");
        assertThat(first.getJobDetailLink())
            .isEqualTo("https://kakaomobility.career.greetinghr.com/ko/o/235808");
        assertThat(first.getSysCompanyCdNm()).isEqualTo("카카오 모빌리티");
        assertThat(first.getClassCdNm()).isEqualTo("개발");
        assertThat(first.getEmpTypeCdNm()).isEqualTo("정규");
        // UTC 02:59:59 → 한국시각 11:59:59
        assertThat(first.getEndDate()).isEqualTo("2099-09-28 11:59:59");
        assertThat(first.getPersonalHistory()).isZero();

        Job_mst second = result.get(1);
        assertThat(second.getAnnoId()).isEqualTo("227958");
        assertThat(second.getEmpTypeCdNm()).isEqualTo("비정규");
        assertThat(second.getPersonalHistory()).isEqualTo(3L);
        assertThat(second.getPersonalHistoryEnd()).isEqualTo(10L);
        // 마감일 없는 상시채용은 endDate 를 비워 둔다 (JobEndDates 가 상시채용으로 읽는다)
        assertThat(second.getEndDate()).isNull();
    }

    @Test
    @DisplayName("카카오모빌리티: 마감일이 지난 공고는 결과에서 제외된다")
    void parseMobilityOpenings_skipsExpiredItems() {
        when(crawlerCommonService.isCloseDate(eq("2099-09-28T02:59:59Z"))).thenReturn(true);

        List<Job_mst> result = new ArrayList<>();
        kakaoCrawlerService.parseMobilityOpenings(MOBILITY_NEXT_DATA, result);

        assertThat(result).extracting(Job_mst::getAnnoId).containsExactly("227958");
    }

    @Test
    @DisplayName("카카오모빌리티: openings 쿼리가 없으면 예외 없이 빈 결과를 준다")
    void parseMobilityOpenings_withoutOpeningsQuery() {
        String nextData = """
            { "props": { "pageProps": { "dehydratedState": { "queries": [
                { "queryKey": ["publicCareer", "getCareerBaseInfo"], "state": { "data": {} } }
            ] } } } }
            """;

        List<Job_mst> result = new ArrayList<>();
        kakaoCrawlerService.parseMobilityOpenings(nextData, result);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("카카오게임즈: 같은 greetinghr 구조를 회사명·상세 주소만 바꿔 읽는다")
    void parseGreetinghrOpenings_kakaoGames() {
        List<Job_mst> result = new ArrayList<>();
        kakaoCrawlerService.parseGreetinghrOpenings(
            MOBILITY_NEXT_DATA, "https://recruit.kakaogames.com", "카카오 게임즈", result);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSysCompanyCdNm()).isEqualTo("카카오 게임즈");
        assertThat(result.get(0).getJobDetailLink()).isEqualTo("https://recruit.kakaogames.com/ko/o/235808");
    }

    /** ninehire(헬스케어·페이증권) 공고 목록 응답 축약본. 마감일은 endAt 으로 온다(예전 deadlineValue). */
    private static final String NINEHIRE_RESPONSE = """
        {
          "count": 43,
          "results": [
            {
              "externalTitle": " 검색발견 백엔드 개발자(시니어) ",
              "addressKey": "HZZ8Yz5b",
              "endAt": "2099-12-01T14:59:59.000Z",
              "deadlineType": "date",
              "employmentType": ["full_time"],
              "career": { "type": "experienced", "range": { "over": 7, "below": 0 } },
              "jobGroup": { "title": "개발" }
            },
            {
              "externalTitle": "주소 키가 없는 공고",
              "endAt": null,
              "employmentType": ["full_time"]
            },
            {
              "externalTitle": "고객센터 상담원(계약직)",
              "addressKey": "590nPJF2",
              "endAt": null,
              "deadlineType": "until_filled",
              "employmentType": ["contract"],
              "career": { "type": "any", "range": null },
              "jobGroup": null
            }
          ]
        }
        """;

    @Test
    @DisplayName("ninehire: endAt 을 한국시각으로 옮기고, 깨진 공고 하나 때문에 나머지를 버리지 않는다")
    void parseNinehireRecruitments_mapsEndAtAndSkipsBrokenItem() {
        List<Job_mst> result = new ArrayList<>();
        int total = kakaoCrawlerService.parseNinehireRecruitments(
            NINEHIRE_RESPONSE, "https://career.kakaopaysec.com", "카카오 페이증권", result);

        assertThat(total).isEqualTo(43);
        assertThat(result).extracting(Job_mst::getAnnoId).containsExactly("HZZ8Yz5b", "590nPJF2");

        Job_mst first = result.get(0);
        assertThat(first.getAnnoSubject()).isEqualTo("검색발견 백엔드 개발자(시니어)");
        assertThat(first.getJobDetailLink()).isEqualTo("https://career.kakaopaysec.com/job_posting/HZZ8Yz5b");
        assertThat(first.getSysCompanyCdNm()).isEqualTo("카카오 페이증권");
        // UTC 14:59:59 → 한국시각 23:59:59
        assertThat(first.getEndDate()).isEqualTo("2099-12-01 23:59:59");
        assertThat(first.getEmpTypeCdNm()).isEqualTo("정규");
        assertThat(first.getClassCdNm()).isEqualTo("개발");
        assertThat(first.getPersonalHistory()).isEqualTo(7L);

        Job_mst second = result.get(1);
        assertThat(second.getEndDate()).isNull();
        assertThat(second.getEmpTypeCdNm()).isEqualTo("비정규");
    }

    @Test
    @DisplayName("ninehire: endAt 이 없으면 예전 필드 deadlineValue 를 읽는다")
    void parseNinehireRecruitments_fallsBackToDeadlineValue() {
        String json = """
            { "count": 1, "results": [
              { "externalTitle": "보안 엔지니어", "addressKey": "sbumX7WW",
                "deadlineValue": "2099-01-01T00:00:00Z", "employmentType": ["full_time"] }
            ] }
            """;

        List<Job_mst> result = new ArrayList<>();
        kakaoCrawlerService.parseNinehireRecruitments(json, "https://recruit.kakaohealthcare.com", "카카오 헬스케어", result);

        assertThat(result).singleElement().extracting(Job_mst::getEndDate).isEqualTo("2099-01-01 09:00:00");
    }

    private static Job_mst crawled(String companyNameEn, String title) {
        Job_mst job = new Job_mst();
        job.setSysCompanyCdNm(companyNameEn);
        job.setAnnoSubject(title);
        return job;
    }

    @Test
    @DisplayName("회사명 정리: 페이손해보험을 따로 잡고, 자체 사이트 계열사 이름이 '카카오'로 덮이지 않는다")
    void refineResult_mapsSubsidiaryNames() {
        List<Job_mst> jobs = new ArrayList<>(List.of(
            crawled("kpaylossinsurance", "[공동체] 카카오페이손해보험 DBA"),
            crawled("Kakao Pay Corp.", "[공동체] 카카오페이 서버 개발자 - 데이터 플랫폼"),
            crawled("카카오 게임즈", "[신입/경력] 수집형/방치형 RPG 마케팅PM 영입"),
            crawled("Kakao Corp", "Data Scientist (경력)")
        ));

        kakaoCrawlerService.refineResult(jobs);

        assertThat(jobs).extracting(Job_mst::getSysCompanyCdNm)
            .containsExactly("카카오 페이손해보험", "카카오 페이", "카카오 게임즈", "카카오");
        assertThat(jobs).extracting(Job_mst::getAnnoSubject)
            .containsExactly("DBA", "서버 개발자 - 데이터 플랫폼", "수집형/방치형 RPG 마케팅PM 영입", "Data Scientist (경력)");
        assertThat(jobs).extracting(Job_mst::getSubJobCdNm)
            .containsExactly("DBA", "Backend", "PM", "DataAnalyst");
    }
}
