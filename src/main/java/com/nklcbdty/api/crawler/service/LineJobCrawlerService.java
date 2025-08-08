package com.nklcbdty.api.crawler.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.nklcbdty.api.crawler.common.CrawlerCommonService;
import com.nklcbdty.api.crawler.common.JobEnums;
import com.nklcbdty.api.crawler.dto.PersonalHistoryDto;
import com.nklcbdty.api.crawler.interfaces.JobCrawler;
import com.nklcbdty.api.crawler.vo.Job_mst;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class LineJobCrawlerService implements JobCrawler {

    private final CrawlerCommonService crawlerCommonService;

    @Autowired
    public LineJobCrawlerService(CrawlerCommonService crawlerCommonService) {
        this.crawlerCommonService = crawlerCommonService;
    }

    @Override
    @Async
    public CompletableFuture<List<Job_mst>> crawlJobs() {
        List<Job_mst> result = new ArrayList<>();
        try {
            final String apiUrl = "https://careers.linecorp.com/page-data/ko/jobs/page-data.json";
            final String jsonResponse = crawlerCommonService.fetchApiResponse(apiUrl);

            // JSON 객체로 변환
            JSONObject jsonResult = new JSONObject(jsonResponse);

            // edges 배열 가져오기
            JSONArray edges = jsonResult.getJSONObject("result").getJSONObject("data").getJSONObject("allStrapiJobs").getJSONArray("edges");

            // edges 배열을 반복
            for (int i = 0; i < edges.length(); i++) {
                JSONObject edge = edges.getJSONObject(i);


                JSONObject node = edge.getJSONObject("node");
                Object endDate = node.get("end_date");
                if (isCloseDate(endDate)) {
                    continue;
                }

                // regions 배열 가져오기
                JSONArray cities = node.getJSONArray("cities");
                JSONArray jobUnit = node.getJSONArray("job_unit");
                JSONArray jobFields = node.getJSONArray("job_fields");
                if (!cities.isEmpty() && !jobUnit.isEmpty()) {
                    JSONObject firstRegion = cities.getJSONObject(0);
                    JSONObject firstJobUnit = jobUnit.getJSONObject(0);

                    String citiesName = firstRegion.getString("name");
                    String jobUnitName = firstJobUnit.getString("name");

                    // 조건 확인
                    if (("Bundang".equals(citiesName) || "Seoul".equals(citiesName)) && "Engineering".equals(jobUnitName)) {
                        // title 값 가져오기
                        String companies = node.getJSONArray("companies").getJSONObject(0).getString("name");
                        Job_mst item = new Job_mst();
                        item.setAnnoId(node.get("strapiId").toString());
                        item.setClassCdNm(jobUnitName);
                        item.setSysCompanyCdNm(companies);
                        item.setAnnoSubject(node.getString("title"));
                        if(jobFields.isEmpty()) {
                            item.setSubJobCdNm("기타");
                        } else {
                            item.setSubJobCdNm(jobFields.getJSONObject(0).getString("name"));
                        }
                        String employment_type = node.getJSONArray("employment_type").getJSONObject(0).getString("name");
                        if("Full-time".equals(employment_type)) {
                            item.setEmpTypeCdNm("정규");
                        }
                        final String detailLink = "https://careers.linecorp.com/ko/jobs/" + node.getLong("strapiId");
                        item.setJobDetailLink(detailLink);
                        PersonalHistoryDto personalHistoryDto = crawlerCommonService.extractPersonalHistoryFromJobPage(detailLink);
                        item.setPersonalHistory(personalHistoryDto.getFrom());
                        item.setPersonalHistoryEnd(personalHistoryDto.getTo());
                        item.setStartDate(formattedDate(node.getString("start_date")));
                        if (endDate.equals(null)) {
                            item.setEndDate("영입종료시");
                        } else {
                            item.setEndDate(formattedDate(endDate.toString()));
                        }
                        result.add(item);
                    }
                }
            }

            for (Job_mst item : result) {
                if (item.getAnnoSubject().contains("Server Engineer") ||
                    item.getAnnoSubject().contains("Backend Software") ||
                    item.getAnnoSubject().contains("Backend Server") ||
                    item.getAnnoSubject().contains("Financial System Developer") ||
                    item.getAnnoSubject().contains("Server-side")
                ) {
                    item.setSubJobCdNm(JobEnums.BackEnd.getTitle());
                } else if (
                    item.getAnnoSubject().contains("Frontend Engineer") ||
                    item.getAnnoSubject().contains("FrontEnd Engineer") ||
                    item.getAnnoSubject().contains("Front-End Engineer") ||
                    item.getAnnoSubject().contains("Front-end Engineer")
                ) {
                    item.setSubJobCdNm(JobEnums.FrontEnd.getTitle());
                } else if (
                    item.getAnnoSubject().contains("HR System Developer") ||
                    item.getAnnoSubject().contains("사내정보시스템")
                ) {
                    item.setSubJobCdNm(JobEnums.FullStack.getTitle());
                } else if (item.getAnnoSubject().contains("Android Engineer")) {
                    item.setSubJobCdNm(JobEnums.Android.getTitle());
                } else if (item.getAnnoSubject().contains("iOS")) {
                    item.setSubJobCdNm(JobEnums.iOS.getTitle());
                } else if (
                    item.getAnnoSubject().contains("Data Engineer") ||
                    item.getAnnoSubject().contains("MongoDB Engineer")
                ) {
                    item.setSubJobCdNm(JobEnums.DataEngineering.getTitle());
                } else if (
                    item.getAnnoSubject().contains("ML Engineer") ||
                    item.getAnnoSubject().contains("MLOps Engineer")
                ) {
                    item.setSubJobCdNm(JobEnums.ML.getTitle());
                } else if (item.getAnnoSubject().contains("Network Security Engineer")) {
                    item.setSubJobCdNm(JobEnums.SecurityEngineering.getTitle());
                } else if (item.getAnnoSubject().contains("Kubernetes Engineer")) {
                    item.setSubJobCdNm(JobEnums.DevOps.getCode());
                } else if (
                    item.getAnnoSubject().contains("Technical Account Manager") ||
                    item.getAnnoSubject().contains("Technical Program Manager") ||
                    item.getAnnoSubject().contains("Technical Writer") ||
                    item.getAnnoSubject().contains("테크니컬 라이터")
                ) {
                    item.setSubJobCdNm(JobEnums.TechnicalSupport.getTitle());
                } else if (
                    item.getAnnoSubject().contains("QA") ||
                    item.getAnnoSubject().contains("SET")
                ) {
                    item.setSubJobCdNm(JobEnums.QA.getTitle());
                } else if (
                    item.getAnnoSubject().contains("Operations Engineer") ||
                    item.getAnnoSubject().contains("Reliability Engineer") ||
                    item.getAnnoSubject().contains("Framework Engineer")
                ) {
                    item.setSubJobCdNm(JobEnums.Infra.getTitle());
                } else if (item.getAnnoSubject().contains("Site Reliability Engineering")) {
                    item.setSubJobCdNm(JobEnums.DevOps.getTitle());
                }
            }

            for (Job_mst item : result) {
                if (
                    item.getSubJobCdNm().contains("Notinuse") ||
                    item.getSubJobCdNm().contains("notinuse")
                ) {
                    item.setSubJobCdNm(null);
                }
                final String subJobCdNmReplace = item.getSubJobCdNm().replace(" ", "");
                item.setSubJobCdNm(subJobCdNmReplace);
            }

        } catch (Exception e) {
            log.error("Error occurred while crawling jobs: {}", e.getMessage(), e);
        }

        return CompletableFuture.completedFuture(crawlerCommonService.getNotSaveJobItem("LINE", result));
    }

    private String formattedDate(String dateStr) {
        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        LocalDateTime endDateTime = LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return endDateTime.format(outputFormatter);
    }

    private boolean isCloseDate(Object endDate) {
        if (endDate.equals(null)) {
            return false;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

        // endDate를 LocalDateTime 객체로 변환
        LocalDateTime endDateTime = LocalDateTime.parse(endDate.toString(), formatter);

        // 현재 시간 가져오기
        LocalDateTime now = LocalDateTime.now();

        // 비교
        return now.isAfter(endDateTime);
    }
}
