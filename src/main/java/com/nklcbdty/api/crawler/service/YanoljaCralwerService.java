package com.nklcbdty.api.crawler.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.nklcbdty.api.crawler.common.CrawlerCommonService;
import com.nklcbdty.api.crawler.common.JobEnums;
import com.nklcbdty.api.crawler.dto.PersonalHistoryDto;
import com.nklcbdty.common.vo.Job_mst;

import lombok.extern.slf4j.Slf4j;

/**
 * 야놀자 채용 크롤러.
 *
 * <p>careers.yanolja.co(그리팅) 의 {@code _next/data} 에서 공고를 긁었지만, 채용 시스템이 Workday 로
 * 이관되면서 그 페이지는 회사 소개/복지 페이지만 남고 공고 목록이 통째로 빠졌다(openings 항상 0건).
 * 지금은 Workday 채용 사이트가 유일한 공고 출처라 Workday 의 공개 CxS API 를 직접 호출한다.</p>
 */
@Slf4j
@Service
public class YanoljaCralwerService {

    private static final String WORKDAY_CXS_BASE =
        "https://yanolja.wd102.myworkdayjobs.com/wday/cxs/yanolja/External_Yanolja";
    private static final String WORKDAY_SITE_BASE =
        "https://yanolja.wd102.myworkdayjobs.com/ko-KR/External_Yanolja";

    private static final int PAGE_SIZE = 20;
    // 무한 루프 방지용 상한. 야놀자 공고 규모(수십 건)에 비해 충분히 크다.
    private static final int MAX_PAGES = 20;

    private final CrawlerCommonService commonService;

    @Autowired
    public YanoljaCralwerService(CrawlerCommonService commonService) {
        this.commonService = commonService;
    }

    @Async
    public CompletableFuture<List<Job_mst>> crawlJobs() {
        List<Job_mst> result = new ArrayList<>();

        try {
            log.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> {}의 크롤러가 {}로 시작됩니다.",
                this.getClass(), commonService.formatCurrentTime());

            int offset = 0;
            for (int page = 0; page < MAX_PAGES; page++) {
                String body = new JSONObject()
                    .put("appliedFacets", new JSONObject())
                    .put("limit", PAGE_SIZE)
                    .put("offset", offset)
                    .put("searchText", "")
                    .toString();

                String jsonResponse = commonService.fetchApiResponsePost(WORKDAY_CXS_BASE + "/jobs", body);
                JSONObject pageObj = new JSONObject(jsonResponse);
                JSONArray postings = pageObj.optJSONArray("jobPostings");

                if (postings == null) {
                    log.error("야놀자 크롤 응답에서 jobPostings 를 찾지 못함. 응답 앞부분: {}",
                        jsonResponse == null ? "null" : jsonResponse.substring(0, Math.min(300, jsonResponse.length())));
                    break;
                }
                if (postings.isEmpty()) {
                    // 공고가 실제로 0건일 수 있다. Workday 사이트가 살아 있어도(HTTP 200 + 정상 스키마)
                    // 야놀자가 공개 공고를 전부 내리면 total=0 이 온다(2026-08-14 확인). 차단/스키마 변경과
                    // 구분되도록 첫 페이지에서 0건이면 total 과 함께 경고로 남긴다.
                    if (page == 0) {
                        log.warn("야놀자 채용 사이트에 공개 공고가 0건 (total={}). 응답은 정상이라 차단/스키마 변경이 아닌 "
                            + "'공고 없음'으로 판단한다. 사이트: {}", pageObj.optInt("total", 0), WORKDAY_SITE_BASE);
                    }
                    break;
                }

                for (int i = 0; i < postings.length(); i++) {
                    try {
                        Job_mst item = toJobMst(postings.getJSONObject(i));
                        if (item != null) {
                            result.add(item);
                        }
                    } catch (Exception itemEx) {
                        log.error("야놀자 공고 파싱 실패 (offset={}, index={}): {}", offset, i, itemEx.getMessage(), itemEx);
                    }
                }

                offset += PAGE_SIZE;
                if (offset >= pageObj.optInt("total", 0)) {
                    break;
                }
            }

            for (Job_mst item : result) {
                if (item.getAnnoSubject().contains("Software Engineer")) {
                    item.setSubJobCdNm(JobEnums.BackEnd.getTitle());
                } else if (item.getAnnoSubject().contains("Full-Stack")) {
                    item.setSubJobCdNm(JobEnums.FullStack.getTitle());
                } else if (item.getAnnoSubject().contains("Data Scientist")) {
                    item.setSubJobCdNm(JobEnums.DataAnalyst.getTitle());
                } else if (item.getAnnoSubject().contains("Researcher")) {
                    item.setSubJobCdNm(JobEnums.TechnicalSupport.getTitle());
                }
            }

            log.info("야놀자 크롤 완료 — {}건", result.size());
        } catch (Exception e) {
            log.error("Error occurred while crawling jobs: {}", e.getMessage(), e);
        }

        return CompletableFuture.completedFuture(commonService.getNotSaveJobItem("YANOLJA", result));
    }

    /** Workday 목록 항목 하나를 Job_mst 로 변환한다. 필수값이 없으면 null. */
    private Job_mst toJobMst(JSONObject posting) {
        String annoSubject = posting.optString("title", "");
        String externalPath = posting.optString("externalPath", "");
        // bulletFields[0] 이 공고번호(jobReqId). 공고가 수정돼도 유지되는 값이라 annoId 로 쓴다.
        JSONArray bulletFields = posting.optJSONArray("bulletFields");
        String annoId = (bulletFields == null || bulletFields.isEmpty()) ? "" : bulletFields.optString(0, "");

        if (annoId.isBlank() || annoSubject.isBlank() || externalPath.isBlank()) {
            log.warn("야놀자 공고 필수값 누락으로 건너뜀 (title='{}', path='{}', reqId='{}')",
                annoSubject, externalPath, annoId);
            return null;
        }

        Job_mst item = new Job_mst();
        item.setAnnoId(annoId);
        item.setAnnoSubject(annoSubject);
        item.setJobDetailLink(WORKDAY_SITE_BASE + externalPath);
        item.setSysCompanyCdNm("야놀자");
        // Workday 공고에는 마감일이 없다(내려갈 때까지 모집). endDate 는 비워 두고
        // 사이트에서 사라지면 reconcileEndedJobs 가 종료 처리한다.

        applyDetail(item, externalPath);
        return item;
    }

    /**
     * 상세 API 로 고용형태/게시일/경력을 채운다.
     * 상세 조회가 실패해도 목록에서 얻은 정보만으로 공고는 살린다.
     */
    private void applyDetail(Job_mst item, String externalPath) {
        try {
            String detailJson = commonService.fetchApiResponse(WORKDAY_CXS_BASE + externalPath);
            JSONObject info = new JSONObject(detailJson).optJSONObject("jobPostingInfo");
            if (info == null) {
                log.warn("야놀자 공고 상세에 jobPostingInfo 없음 (path={})", externalPath);
                return;
            }

            item.setEmpTypeCdNm(convertTimeTypeToEmpType(info.optString("timeType", "")));
            item.setStartDate(info.optString("startDate", null));

            String jobDescription = info.optString("jobDescription", "");
            if (!jobDescription.isBlank()) {
                PersonalHistoryDto history = commonService.getPersonalHistory(Jsoup.parse(jobDescription).text());
                item.setPersonalHistory(history.getFrom());
                item.setPersonalHistoryEnd(history.getTo());
            }
        } catch (Exception e) {
            log.error("야놀자 공고 상세 조회 실패 (path={}): {}", externalPath, e.getMessage());
        }
    }

    /** Workday 의 timeType(Full time/Part time)을 기존 고용형태 표기로 바꾼다. */
    private String convertTimeTypeToEmpType(String timeType) {
        if (timeType == null || timeType.isBlank()) return "";

        switch (timeType.replace(" ", "").toUpperCase()) {
            case "FULLTIME":
                return "정규";
            case "PARTTIME":
                return "비정규";
            default:
                return timeType;
        }
    }
}
