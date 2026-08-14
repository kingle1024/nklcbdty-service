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
 * 쿠팡 채용 크롤러.
 *
 * <p>예전엔 www.coupang.jobs 목록 HTML 을 Jsoup 으로 긁었지만, 이 사이트가 Cloudflare 봇 차단
 * 뒤로 들어가면서 서버(운영/배치)에서는 "Just a moment..." 챌린지 페이지(403)만 받게 됐다.
 * 그래서 총 건수(data-results) 파싱이 항상 실패해 0건이 되고, 링크 점검 배치도 같은 403 을 받아
 * 이미 저장된 쿠팡 공고를 매번 오류 종료(endDate=2000-01-01) 처리했다.</p>
 *
 * <p>coupang.jobs 자체가 Greenhouse 를 채용 시스템으로 쓰고 있어(목록 링크가 모두 {@code ?gh_jid=})
 * Greenhouse 의 공개 job board API 를 직접 호출한다. 이쪽은 봇 차단이 없는 공개 JSON API 이고,
 * 공고 번호({@code id})가 기존에 쓰던 {@code data-id} 와 같은 값이라 저장된 annoId 와도 이어진다.</p>
 */
@Service
@Slf4j
public class CoupangJobCrawlerService {

    private static final String GREENHOUSE_BOARD = "https://boards-api.greenhouse.io/v1/boards/coupang/jobs";
    /** 기존 크롤러가 목록을 서울(반경 100km)로 좁혀 받아왔던 것과 동일한 범위. */
    private static final String SEOUL_LOCATION_KEYWORD = "Seoul";

    private final CrawlerCommonService crawlerCommonService;

    @Autowired
    public CoupangJobCrawlerService(CrawlerCommonService crawlerCommonService) {
        this.crawlerCommonService = crawlerCommonService;
    }

    @Async
    public CompletableFuture<List<Job_mst>> crawlJobs() {
        List<Job_mst> resList = new ArrayList<>();
        log.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> {}의 크롤러가 {}로 시작됩니다.",
            this.getClass(), crawlerCommonService.formatCurrentTime());

        // 기존엔 crawlJobs 에 try/catch 가 없어 쿠팡 실패 시 예외가 controller 의 all 모드까지 전파돼
        // 전체 회사 크롤 결과가 0건이 됐다. 다른 크롤러처럼 내부에서 격리해 부분 결과를 반환한다.
        try {
            // content=false 로 목록만 받는다(약 0.5MB). 상세 본문까지 한 번에 받으면 13MB 라
            // all 모드에서 8개 크롤러가 동시에 도는 힙에 부담이 된다.
            String listJson = crawlerCommonService.fetchApiResponse(GREENHOUSE_BOARD + "?content=false");
            JSONArray jobs = new JSONObject(listJson).optJSONArray("jobs");

            if (jobs == null) {
                log.error("쿠팡 크롤 응답에서 jobs 를 찾지 못함. 응답 앞부분: {}",
                    listJson == null ? "null" : listJson.substring(0, Math.min(300, listJson.length())));
                return CompletableFuture.completedFuture(
                    crawlerCommonService.getNotSaveJobItem("COUPANG", resList));
            }

            for (int i = 0; i < jobs.length(); i++) {
                try {
                    Job_mst item = toJobMst(jobs.getJSONObject(i));
                    if (item != null) {
                        resList.add(item);
                    }
                } catch (Exception itemEx) {
                    log.error("쿠팡 공고 파싱 실패 (index={}): {}", i, itemEx.getMessage(), itemEx);
                }
            }

            for (Job_mst item : resList) {
                setSubJobCdNm(item);
                setSysCompanyCdNm(item);
            }

            log.info("쿠팡 크롤 완료 — 전체 {}건 중 서울 {}건", jobs.length(), resList.size());
            if (resList.isEmpty()) {
                log.warn("쿠팡 크롤 결과 0건 — Greenhouse board 응답 스키마 변경/보드 이관 의심");
            }
        } catch (Exception e) {
            log.error("쿠팡 크롤링 실패: {}", e.getMessage(), e);
        }

        return CompletableFuture.completedFuture(crawlerCommonService.getNotSaveJobItem("COUPANG", resList));
    }

    /** Greenhouse 목록 항목 하나를 Job_mst 로 변환한다. 서울 공고가 아니거나 필수값이 없으면 null. */
    private Job_mst toJobMst(JSONObject posting) {
        JSONObject location = posting.optJSONObject("location");
        String workplace = location == null ? "" : location.optString("name", "");
        if (!workplace.contains(SEOUL_LOCATION_KEYWORD)) {
            return null;
        }

        long id = posting.optLong("id", 0L);
        String annoSubject = posting.optString("title", "");
        if (id == 0L || annoSubject.isBlank()) {
            log.warn("쿠팡 공고 필수값 누락으로 건너뜀 (id={}, title='{}')", id, annoSubject);
            return null;
        }

        Job_mst job_mst = new Job_mst();
        job_mst.setAnnoId(String.valueOf(id));
        job_mst.setAnnoSubject(annoSubject);
        // absolute_url 은 영문(/en/) 페이지라 한국어 공고 페이지로 맞춰 준다. gh_jid 로 같은 공고가 열린다.
        job_mst.setJobDetailLink("https://www.coupang.jobs/kr/jobs/?gh_jid=" + id);
        job_mst.setWorkplace(JobEnums.SEOUL.getTitle());

        applyPersonalHistory(job_mst, id);
        return job_mst;
    }

    /**
     * 상세 API 본문에서 경력 요건을 뽑는다.
     * 상세 조회가 실패해도 목록에서 얻은 정보만으로 공고는 살린다.
     */
    private void applyPersonalHistory(Job_mst item, long id) {
        try {
            String detailJson = crawlerCommonService.fetchApiResponse(GREENHOUSE_BOARD + "/" + id);
            String content = new JSONObject(detailJson).optString("content", "");
            if (content.isBlank()) {
                return;
            }

            // content 는 HTML 이 이스케이프된 문자열이라 Jsoup 으로 두 번 풀어야 본문 텍스트가 된다.
            PersonalHistoryDto personalHistoryDto =
                crawlerCommonService.getPersonalHistory(Jsoup.parse(Jsoup.parse(content).text()).text());
            item.setPersonalHistory(personalHistoryDto.getFrom());
            item.setPersonalHistoryEnd(personalHistoryDto.getTo());
        } catch (Exception e) {
            log.error("쿠팡 공고 상세 조회 실패 (id={}): {}", id, e.getMessage());
        }
    }

    private void setSysCompanyCdNm(Job_mst item) {
        if (item.getSysCompanyCdNm() == null) {
            if (item.getAnnoSubject().contains("[쿠팡플레이]")) {
                item.setSysCompanyCdNm("쿠팡플레이");
            } else if (item.getAnnoSubject().contains("[쿠팡이츠]")) {
                item.setSysCompanyCdNm("쿠팡이츠");
            } else if (item.getAnnoSubject().contains("[쿠팡풀필먼트서비스]") ||
                item.getAnnoSubject().contains("Coupang Fulfillment Services")
            ) {
                item.setSysCompanyCdNm("쿠팡풀필먼트서비스");
            } else if (item.getAnnoSubject().contains("[쿠팡로지스틱스서비스]")) {
                item.setSysCompanyCdNm("쿠팡로지스틱스서비스");
            } else if (item.getAnnoSubject().contains("[CPLB]")) {
                item.setSysCompanyCdNm("CPLB");
            } else {
                item.setSysCompanyCdNm("쿠팡");
            }
        }
    }

    private void setSubJobCdNm(Job_mst item) {
        if (item.getAnnoSubject().contains("QA Engineer")) {
            item.setSubJobCdNm(JobEnums.QA.getTitle());
        } else if (item.getAnnoSubject().contains("Front-end")) {
            item.setSubJobCdNm(JobEnums.FrontEnd.getTitle());
        } else if (item.getAnnoSubject().contains("Backend") ||
            item.getAnnoSubject().contains("Back-end") ||
            item.getAnnoSubject().contains("Back-End") ||
            item.getAnnoSubject().contains("back-end") ||
            item.getAnnoSubject().contains("Software Engineer") ||
            item.getAnnoSubject().contains("Growth Engineering")
        ) {
            item.setSubJobCdNm(JobEnums.BackEnd.getTitle());
        } else if (item.getAnnoSubject().contains("SRE")) {
            item.setSubJobCdNm(JobEnums.DevOps.getTitle());
        } else if (item.getAnnoSubject().contains("Full-stack") ||
            item.getAnnoSubject().contains("Video Stream Platform Engineer")
        ) {
            item.setSubJobCdNm(JobEnums.FullStack.getTitle());
        } else if (item.getAnnoSubject().contains("Machine Learning Engineer")) {
            item.setSubJobCdNm(JobEnums.ML.getTitle());
        } else if (item.getAnnoSubject().contains("Mobile Engineer")) {
            item.setSubJobCdNm(JobEnums.Android.getTitle());
        } else if (item.getAnnoSubject().contains("Flutter")) {
            item.setSubJobCdNm(JobEnums.Flutter.getTitle());
        } else if (item.getAnnoSubject().contains("Data Analyst") ||
            item.getAnnoSubject().contains("Data Science") ||
            item.getAnnoSubject().contains("Data Analysis") ||
            item.getAnnoSubject().contains("Business Analyst") ||
            item.getAnnoSubject().contains("데이터 분석 담당")
        ) {
            item.setSubJobCdNm(JobEnums.DataAnalyst.getTitle());
        } else if (
            item.getAnnoSubject().contains("Data Engineer")
        ) {
            item.setSubJobCdNm(JobEnums.DataEngineering.getTitle());
        } else if (item.getAnnoSubject().contains("Network Engineer") ||
            item.getAnnoSubject().contains("Infra Engineer") ||
            item.getAnnoSubject().contains("System Engineer") ||
            item.getAnnoSubject().contains("Data Center Engineer") ||
            item.getAnnoSubject().contains("Facility Engineer") ||
            item.getAnnoSubject().contains("Facilities Engineer")
        ) {
            item.setSubJobCdNm(JobEnums.Infra.getTitle());
        } else if (
            item.getAnnoSubject().contains("Compliance Monitoring") ||
            item.getAnnoSubject().contains("Security Architecture") ||
            item.getAnnoSubject().contains("개인정보보호 컴플라이언스 디렉터")
        ) {
            item.setSubJobCdNm(JobEnums.Security.getTitle());
        } else if (item.getAnnoSubject().contains("Security Engineer")) {
            item.setSubJobCdNm(JobEnums.SecurityEngineering.getTitle());
        } else if (item.getAnnoSubject().contains("Product Manager") ||
            item.getAnnoSubject().contains("PM")
        ) {
            item.setSubJobCdNm(JobEnums.PM.getTitle());
        } else if (item.getAnnoSubject().contains("Product Owner")) {
            item.setSubJobCdNm(JobEnums.PO.getTitle());
        } else if (
            item.getAnnoSubject().contains("Product Design") ||
            item.getAnnoSubject().contains("Product design") ||
            item.getAnnoSubject().contains("Product designer") ||
            item.getAnnoSubject().contains("Brand Design") ||
            item.getAnnoSubject().contains("Brand Designer")
        ) {
            item.setSubJobCdNm(JobEnums.ProductDesigner.getTitle());
        } else if (item.getAnnoSubject().contains("SAP")) {
            item.setSubJobCdNm(JobEnums.SAP.getTitle());
        } else if (item.getAnnoSubject().contains("Al Counsel")) {
            item.setSubJobCdNm(JobEnums.AI.getTitle());
        } else if (item.getAnnoSubject().contains("Technical Program Manage")) {
            item.setSubJobCdNm(JobEnums.TechnicalSupport.getTitle());
        }
    }
}
