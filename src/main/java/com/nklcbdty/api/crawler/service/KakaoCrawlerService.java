package com.nklcbdty.api.crawler.service;

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
                if (job.getAnnoSubject().contains("DevOps")) {
                    job.setSubJobCdNm(JobEnums.DevOps.getTitle());
                } else if (job.getAnnoSubject().contains("프론트")) {
                    job.setSubJobCdNm(JobEnums.FrontEnd.getTitle());
                } else if (job.getAnnoSubject().contains("백엔드") ||
                    job.getAnnoSubject().contains("서버 개발자 모집") ||
                    job.getAnnoSubject().contains("Back-End")
                ) {
                    job.setSubJobCdNm(JobEnums.BackEnd.getTitle());
                } else if (job.getAnnoSubject().contains("Data Analyst")) {
                    job.setSubJobCdNm(JobEnums.DataAnalyst.getTitle());
                } else if (
                    job.getAnnoSubject().contains("머신러닝 엔지니어") ||
                    job.getAnnoSubject().contains("머신러닝 research scientist")
                ) {
                    job.setSubJobCdNm(JobEnums.ML.getTitle());
                } else if (job.getAnnoSubject().contains("데이터 사이언티스트")) {

                } else if (job.getAnnoSubject().contains("DB운영")) {
                    job.setSubJobCdNm(JobEnums.DBA.getTitle());
                } else if (job.getAnnoSubject().contains("안정성 관리") ||
                    job.getAnnoSubject().contains("Technical Writer") ||
                    job.getAnnoSubject().contains("기술 문서")
                ) {
                    job.setSubJobCdNm(JobEnums.TechnicalSupport.getTitle());
                } else if (job.getAnnoSubject().contains("PM")) {
                    job.setSubJobCdNm(JobEnums.PM.getTitle());
                }

                if ("etc".equals(job.getSubJobCdNm())) {
                    if (job.getAnnoSubject().contains("네트워크 드라이버") ||
                        job.getAnnoSubject().contains("FPGA Engineer") ||
                        job.getAnnoSubject().contains("컴퓨팅 서비스") ||
                        job.getAnnoSubject().contains("네트워킹 서비스") ||
                        job.getAnnoSubject().contains("시스템 엔지니어")
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
            crawlerCommonService.saveAll("KAKAO", result);

        } catch (Exception e) {
            log.error("Error occurred while crawling jobs: {}", e.getMessage(), e);
        }
        return result;
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
            result.add(item);
        }

        return true;
    }
}
