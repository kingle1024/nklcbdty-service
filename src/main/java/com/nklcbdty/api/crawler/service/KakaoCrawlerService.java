package com.nklcbdty.api.crawler.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nklcbdty.api.crawler.common.CrawlerCommonService;
import com.nklcbdty.api.crawler.common.JobEnums;
import com.nklcbdty.api.crawler.interfaces.JobCrawler;
import com.nklcbdty.api.crawler.vo.Job_mst;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class KakaoCrawlerService implements JobCrawler {

    private final CrawlerCommonService crawlerCommonService;

    @Autowired
    public KakaoCrawlerService(CrawlerCommonService crawlerCommonService) {
        this.crawlerCommonService = crawlerCommonService;
    }

    @Override
    public List<Job_mst> crawlJobs() {
        List<Job_mst> result = new ArrayList<>();
        try {
            addRecruitContent("P", result);
            addRecruitContent("S", result);

            for (Job_mst job : result) {
                if (job.getAnnoSubject().contains("DevOps") ||
                    job.getAnnoSubject().contains("Kubernetes Engine 개발자") ||
                    job.getAnnoSubject().contains("DKOS(Kubernetes as a Service) 개발자") ||
                    job.getAnnoSubject().contains("SRE(Site Reliability Engineer) 엔지니어")
                ) {
                    job.setSubJobCdNm(JobEnums.DevOps.getTitle());
                } else if (job.getAnnoSubject().contains("프론트") ||
                    job.getAnnoSubject().contains("FE 개발자")
                ) {
                    job.setSubJobCdNm(JobEnums.FrontEnd.getTitle());
                } else if (job.getAnnoSubject().contains("백엔드") ||
                    job.getAnnoSubject().contains("서버 개발자") ||
                    job.getAnnoSubject().contains("Back-End") ||
                    job.getAnnoSubject().contains("CI/CD 서비스 개발") ||
                    job.getAnnoSubject().contains("데이터 응용 플랫폼 개발") ||
                    job.getAnnoSubject().contains("시스템(ActionBase) 개발") ||
                    job.getAnnoSubject().contains("모델 플랫폼 개발")
                ) {
                    job.setSubJobCdNm(JobEnums.BackEnd.getTitle());
                } else if (
                    job.getAnnoSubject().contains("FE/BE") ||
                    job.getAnnoSubject().contains("플랫폼 개발자") ||
                    job.getAnnoSubject().contains("웹 풀스택")
                ) {
                    job.setSubJobCdNm(JobEnums.FullStack.getTitle());
                } else if (job.getAnnoSubject().contains("Data Analyst") ||
                    job.getAnnoSubject().contains("데이터 사이언티스트") ||
                    job.getAnnoSubject().contains("데이터 분석가") ||
                    job.getAnnoSubject().contains("데이터 분석 담당자")
                ) {
                    job.setSubJobCdNm(JobEnums.DataAnalyst.getTitle());
                } else if (job.getAnnoSubject().contains("macOS 플랫폼 개발자")) {
                    job.setSubJobCdNm(JobEnums.iOS.getTitle());
                } else if (job.getAnnoSubject().contains("Flutter")) {
                    job.setSubJobCdNm(JobEnums.Flutter.getTitle());
                } else if (job.getAnnoSubject().contains("데이터 엔지니어") ||
                    job.getAnnoSubject().contains("NoSQL 엔지니어")
                ) {
                    job.setSubJobCdNm(JobEnums.DataEngineering.getTitle());
                } else if (
                    job.getAnnoSubject().contains("머신러닝 엔지니어") ||
                    job.getAnnoSubject().contains("ML 엔지니어") ||
                    job.getAnnoSubject().contains("머신러닝 research scientist") ||
                    job.getAnnoSubject().contains("데이터플랫폼 엔지니어")
                ) {
                    job.setSubJobCdNm(JobEnums.ML.getTitle());
                } else if (job.getAnnoSubject().contains("DB운영")) {
                    job.setSubJobCdNm(JobEnums.DBA.getTitle());
                } else if (job.getAnnoSubject().contains("AI Safety 엔지니어")) {
                    job.setSubJobCdNm(JobEnums.AI.getTitle());
                } else if (job.getAnnoSubject().contains("안정성 관리") || job.getAnnoSubject().contains("Technical Writer")
                    || job.getAnnoSubject().contains("기술 문서") || job.getAnnoSubject().contains("기술지원 엔지니어")
                    || job.getAnnoSubject().contains("Developer Relations")
                    || job.getAnnoSubject().contains("모니터링 담당자")
                ) {
                    job.setSubJobCdNm(JobEnums.TechnicalSupport.getTitle());
                } else if (job.getAnnoSubject().contains("PM")) {
                    job.setSubJobCdNm(JobEnums.PM.getTitle());
                } else if (job.getAnnoSubject().contains("QA")) {
                    job.setSubJobCdNm(JobEnums.QA.getTitle());
                } else if (job.getAnnoSubject().contains("보안 담당자")) {
                    job.setSubJobCdNm(JobEnums.Security.getTitle());
                } else if (job.getAnnoSubject().contains("취약점 분석") || job.getAnnoSubject().contains("모의해킹")) {
                    job.setSubJobCdNm(JobEnums.SecurityEngineering.getTitle());
                } else if (job.getAnnoSubject().contains("기획 담당자") || job.getAnnoSubject().contains("기획자")) {
                    job.setSubJobCdNm(JobEnums.PO.getTitle());
                } else if (job.getAnnoSubject().contains("프로덕트 디자이너")) {
                    job.setSubJobCdNm(JobEnums.ProductDesigner.getTitle());
                } else if (job.getAnnoSubject().contains("시스템 엔지니어") ||
                    job.getAnnoSubject().contains("네트워크 드라이버 개발") ||
                    job.getAnnoSubject().contains("FPGA Engineer") ||
                    job.getAnnoSubject().contains("컴퓨팅 서비스 개발") ||
                    job.getAnnoSubject().contains("네트워킹 서비스 개발") ||
                    job.getAnnoSubject().contains("스토리지 플랫폼 개발") ||
                    job.getAnnoSubject().contains("클라우드 네트워크 엔지니어") ||
                    job.getAnnoSubject().contains("클라우드 Managed 서비스 엔지니어")
                ) {
                    job.setSubJobCdNm(JobEnums.Infra.getTitle());
                } else {
                    job.setSubJobCdNm(null);
                }

                if ("etc".equals(job.getSubJobCdNm())) {
                    if (job.getAnnoSubject().contains("네트워크 드라이버") ||
                        job.getAnnoSubject().contains("FPGA Engineer") ||
                        job.getAnnoSubject().contains("컴퓨팅 서비스") ||
                        job.getAnnoSubject().contains("네트워킹 서비스") ||
                        job.getAnnoSubject().contains("시스템 엔지니어") ||
                        job.getAnnoSubject().contains("클라우드 Managed 서비스 엔지니어") ||
                        job.getAnnoSubject().contains("클라우드 네트워크 엔지니어") ||
                        job.getAnnoSubject().contains("스토리지 플랫폼 개발자")
                    ) {
                        job.setSubJobCdNm(JobEnums.Infra.getTitle());
                    }
                }
            }

            for (Job_mst job : result) {
                String replaeTitle = job.getAnnoSubject().replaceAll("\\[.*?]\\s*", "");
                job.setAnnoSubject(replaeTitle);
                if ("Server".equals(job.getSubJobCdNm())) {
                    job.setSubJobCdNm(JobEnums.BackEnd.getTitle());
                }
                if (job.getAnnoSubject().contains("카카오페이")) {
                    String replaceTitle = job.getAnnoSubject().replace("카카오페이", "");
                    job.setAnnoSubject(replaceTitle);
                } else if (job.getAnnoSubject().contains("카카오모빌리티")) {
                    String replaceTitle = job.getAnnoSubject().replace("카카오모빌리티", "");
                    job.setAnnoSubject(replaceTitle);
                } else if (job.getAnnoSubject().contains("카카오엔터프라이즈")) {
                    String replaceTitle = job.getAnnoSubject().replace("카카오엔터프라이즈", "");
                    job.setAnnoSubject(replaceTitle);
                } else if (job.getAnnoSubject().contains("카카오게임즈")) {
                    String replaceTitle = job.getAnnoSubject().replace("카카오게임즈", "");
                    job.setAnnoSubject(replaceTitle);
                } else if (job.getAnnoSubject().contains("카카오헬스케어")) {
                    String replaceTitle = job.getAnnoSubject().replace("카카오헬스케어", "");
                    job.setAnnoSubject(replaceTitle);
                }

                switch (job.getSysCompanyCdNm()) {
                    case "kakao mobility": {
                        job.setSysCompanyCdNm("카카오 모빌리티");
                        break;
                    }
                    case "Kakao Pay Corp.": {
                        job.setSysCompanyCdNm("카카오 페이");
                        break;
                    }
                    case "Kakao Enterprise": {
                        job.setSysCompanyCdNm("카카오 엔터프라이즈");
                        break;
                    }
                    case "KakaoGames": {
                        job.setSysCompanyCdNm("카카오 게임즈");
                        break;
                    }
                    case "kakaohealthcare": {
                        job.setSysCompanyCdNm("카카오 헬스케어");
                        break;
                    }
                    default: {
                        job.setSysCompanyCdNm("카카오");
                    }
                }
            }

            List<Job_mst> kakaoBankResult = new ArrayList<>();
            addRecruitKakaoBank(kakaoBankResult);
            for (Job_mst item : kakaoBankResult) {
                if (item.getAnnoSubject().contains("QA")) {
                    item.setSubJobCdNm(JobEnums.QA.getTitle());
                } else if (item.getAnnoSubject().contains("iOS")) {
                    item.setSubJobCdNm(JobEnums.iOS.getTitle());
                } else if (item.getAnnoSubject().contains("머신러닝 엔지니어") ||
                    item.getAnnoSubject().contains("AI Research Engineer")
                ) {
                    item.setSubJobCdNm(JobEnums.ML.getTitle());
                } else if (item.getAnnoSubject().contains("데이터 분석 담당자")) {
                    item.setSubJobCdNm(JobEnums.DataAnalyst.getTitle());
                } else if (item.getAnnoSubject().contains("데이터 엔지니어") ||
                    item.getAnnoSubject().contains("데이터 플랫폼 엔지니어") ||
                    item.getAnnoSubject().contains("데이터 마트 개발 담당자")
                ) {
                    item.setSubJobCdNm(JobEnums.DataEngineering.getTitle());
                } else if (item.getAnnoSubject().contains("데이터베이스 관리자")) {
                    item.setSubJobCdNm(JobEnums.DBA.getTitle());
                } else if (item.getAnnoSubject().contains("시스템 엔지니어") ||
                    item.getAnnoSubject().contains("시스템 아키텍트/엔지니어")
                ) {
                    item.setSubJobCdNm(JobEnums.Infra.getTitle());
                } else if (item.getAnnoSubject().contains("기획자") ||
                    item.getAnnoSubject().contains("기획 담당자") ||
                    item.getAnnoSubject().contains("기획 및 운영 담당자") ||
                    item.getAnnoSubject().contains("신사업 전략 담당자") ||
                    item.getAnnoSubject().contains("업무 담당자") ||
                    item.getAnnoSubject().contains("전략 담당자") ||
                    item.getAnnoSubject().contains("운영 담당자") ||
                    item.getAnnoSubject().contains("심사 담당자") ||
                    item.getAnnoSubject().contains("서비스 담당자") ||
                    item.getAnnoSubject().contains("Fraud 담당자") ||
                    item.getAnnoSubject().contains("시스템 담당자") ||
                    item.getAnnoSubject().contains("세일즈 담당자") ||
                    item.getAnnoSubject().contains("외환 상품/서비스 담당자") ||
                    item.getAnnoSubject().contains("여신 Anti-Fraud 담당자") ||
                    item.getAnnoSubject().contains("FDS 시스템 담당자")
                ) {
                    item.setSubJobCdNm(JobEnums.PO.getTitle());
                } else if (item.getAnnoSubject().contains("PM")) {
                    item.setSubJobCdNm(JobEnums.PM.getTitle());
                } else if (item.getAnnoSubject().contains("프론트엔드 개발자") ||
                    item.getAnnoSubject().contains("React")
                ) {
                    item.setSubJobCdNm(JobEnums.FrontEnd.getTitle());
                } else if (item.getAnnoSubject().contains("서버 개발자") ||
                    item.getAnnoSubject().contains("백엔드 개발자") ||
                    item.getAnnoSubject().contains("DKOS(Kubernetes as a Service) 개발자")
                ) {
                    item.setSubJobCdNm(JobEnums.BackEnd.getTitle());
                } else if (item.getAnnoSubject().contains("상담업무 시스템 개발자") ||
                    item.getAnnoSubject().contains("업무 개발자") ||
                    item.getAnnoSubject().contains("서비스 개발자") ||
                    item.getAnnoSubject().contains("개발 담당자")
                ) {
                    item.setSubJobCdNm(JobEnums.FullStack.getTitle());
                } else if (item.getAnnoSubject().contains("DevOps") ||
                    item.getAnnoSubject().contains("Kubernetes Engineer") ||
                    item.getAnnoSubject().contains("서비스 아키텍트") ||
                    item.getAnnoSubject().contains("뱅킹 아키텍트")
                ) {
                    item.setSubJobCdNm(JobEnums.DevOps.getTitle());
                } else if (item.getAnnoSubject().contains("브랜드 디자") ||
                    item.getAnnoSubject().contains("프로덕트 디자이너") ||
                    item.getAnnoSubject().contains("UX/UI 디자이너")
                ) {
                    item.setSubJobCdNm(JobEnums.ProductDesigner.getTitle());
                } else if (item.getAnnoSubject().contains("시스템보안 담당자") ||
                    item.getAnnoSubject().contains("보안담당자") ||
                    item.getAnnoSubject().contains("보안 담당자")
                ) {
                    item.setSubJobCdNm(JobEnums.Security.getTitle());
                } else if (item.getAnnoSubject().contains("Developer Relations") ||
                    item.getAnnoSubject().contains("모니터링 담당자")
                ) {
                    item.setSubJobCdNm(JobEnums.TechnicalSupport.getTitle());
                } else {
                    item.setSubJobCdNm(null);
                }
            }

            result.addAll(kakaoBankResult);
            crawlerCommonService.saveAll("KAKAO", result);

        } catch (Exception e) {
            log.error("Error occurred while crawling jobs: {}", e.getMessage(), e);
        }
        return result;
    }

    private void addRecruitKakaoBank(List<Job_mst> result) {

        int idx = 1;
        int lastIdx = 999;
        while (true) {
            if(idx > lastIdx) {
                break;
            }

            String apiUrl = "https://recruit.kakaobank.com/api/user/recruit?pageNumber=" + idx + "&pageSize=20";
            String jsonResponse = crawlerCommonService.fetchApiResponse(apiUrl);
            if (jsonResponse.isEmpty()) {
                break;
            }

            JSONArray jobList = new JSONObject(jsonResponse).getJSONArray("list");
            JSONObject paging = new JSONObject(jsonResponse).getJSONObject("paging");
            lastIdx = paging.getInt("totalPages");
            for (int i = 0; i < jobList.length(); i++) {
                JSONObject edge = jobList.getJSONObject(i);
                Object endDate = edge.get("receiveEndDatetime");
                if(isCloseDate(endDate)) {
                    continue;
                }

                String title = edge.getString("recruitNoticeName");
                long recruitNoticeSn = edge.getLong("recruitNoticeSn");

                String employeeTypeName;
                if (title.contains("계약직") || title.contains("인턴")) {
                    employeeTypeName = "비정규";
                } else {
                    employeeTypeName = "정규";
                }
                String jobType = edge.getString("recruitClassName");

                Job_mst item = new Job_mst();
                item.setAnnoId(recruitNoticeSn);
                item.setAnnoSubject(title);
                item.setEmpTypeCdNm(employeeTypeName);
                item.setClassCdNm("Tech");
                item.setSubJobCdNm(jobType);
                item.setSysCompanyCdNm("카카오 뱅크");
                item.setJobDetailLink("https://recruit.kakaobank.com/jobs/" + recruitNoticeSn);
                item.setEndDate(String.valueOf(endDate));
                result.add(item);
            }
            idx++;
        }

    }

    private void addRecruitContent(String type, List<Job_mst> result) {
        final String companyType;
        if ("P".equals(type)) {
            companyType = "KAKAO";
        } else {
            companyType = "SUBSIDIARY";
        }

        int idx = 1;
        while (true) {
            boolean isContinue = addResult(idx, type, result, companyType);
            if (!isContinue) {
                break;
            }
            idx++;
        }
    }

    private boolean addResult(int idx, String type, List<Job_mst> result, String companyType) {
        final String apiUrl = "https://careers.kakao.com/public/api/job-list?"
            + "skillSet=&part=TECHNOLOGY"
            + "&company="+ companyType
            + "&keyword=&employeeType=&page=" + idx;
        final String jsonResponse = crawlerCommonService.fetchApiResponse(apiUrl);

        // JSON 파싱 및 변환
        JSONArray jobList = new JSONObject(jsonResponse).getJSONArray("jobList");
        if (jobList.isEmpty()) {
            return false;
        }

        // edges 배열을 반복
        for (int i = 0; i < jobList.length(); i++) {
            JSONObject edge = jobList.getJSONObject(i);
            String title = edge.getString("jobOfferTitle");
            Object endDate = edge.get("endDate");
            if(isCloseDate(endDate)) {
                continue;
            }

            long jobOfferId = edge.getLong("jobOfferId");

            String employeeTypeName = edge.getString("employeeTypeName");
            if("정규직".equals(employeeTypeName)) {
                employeeTypeName = "정규";
            } else {
                employeeTypeName = "비정규";
            }
            String jobType = edge.getString("jobType");
            if("TECHNOLOGY".equals(jobType)) {
                jobType = "Tech";
            }
            String skillSetType = "etc";
            if(edge.has("skillSetList") && !edge.isNull("skillSetList")) {
                skillSetType = edge.getJSONArray("skillSetList").getJSONObject(0).getString("skillSetType");
            }

            String companyNameEn = edge.getString("companyNameEn");
            Job_mst item = new Job_mst();
            item.setAnnoSubject(String.valueOf(title));
            item.setAnnoId(jobOfferId);
            item.setEmpTypeCdNm(employeeTypeName);
            item.setClassCdNm(jobType);
            item.setSubJobCdNm(skillSetType);
            item.setSysCompanyCdNm(companyNameEn);
            item.setJobDetailLink("https://careers.kakao.com/jobs/" + type + "-" + jobOfferId +"?skillSet=&part=TECHNOLOGY"
                + "&company="+ companyType +"&keyword=&employeeType=&page=" + idx);
            if (endDate.equals(null)) {
                item.setEndDate("영입종료시");
            } else {
                DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                LocalDateTime endDateTime = LocalDateTime.parse(endDate.toString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                item.setEndDate(endDateTime.format(outputFormatter));
            }
            result.add(item);
        }

        return true;
    }

    private boolean isCloseDate(Object endDate) {
        if (endDate.equals(null)) {
            return false;
        }
        LocalDateTime endDateTime;
        String endDateStr = String.valueOf(endDate);

        if (endDateStr.contains("T")) {
            endDateTime = LocalDateTime.parse(endDateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } else {
            endDateTime = LocalDateTime.parse(endDateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }

        // 현재 시간 가져오기
        LocalDateTime now = LocalDateTime.now();

        // 비교
        return now.isAfter(endDateTime);
    }
}
