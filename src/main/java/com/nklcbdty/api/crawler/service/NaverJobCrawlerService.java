package com.nklcbdty.api.crawler.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.nklcbdty.api.crawler.common.CrawlerCommonService;
import com.nklcbdty.api.crawler.common.JobEnums;
import com.nklcbdty.api.crawler.interfaces.JobCrawler;
import com.nklcbdty.api.crawler.vo.Job_mst;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NaverJobCrawlerService {

    private final CrawlerCommonService crawlerCommonService;
    private final String apiUrl;

    @Autowired
    public NaverJobCrawlerService(CrawlerCommonService crawlerCommonService) {
        this.crawlerCommonService = crawlerCommonService;
        this.apiUrl = createApiUrl();
    }

    private String createApiUrl() {
        return "https://recruit.navercorp.com/rcrt/loadJobList.do?annoId=&sw=&subJobCdArr=&sysCompanyCdArr=&empTypeCdArr=&entTypeCdArr=&workAreaCdArr=&";
    }

    @Async
    public CompletableFuture<List<Job_mst>> crawlJobs() {
        List<Job_mst> result = new ArrayList<>(Collections.emptyList());

        try {

            int idx = 0;
            while (true) {
                final String jsonResponse = crawlerCommonService.fetchApiResponse(apiUrl + "firstIndex=" + idx);
                JSONArray jobList = new JSONObject(jsonResponse).getJSONArray("list");
                if (jobList.isEmpty()) {
                    break;
                }

                for (int i = 0; i < jobList.length(); i++) {
                    JSONObject edge = jobList.getJSONObject(i);

                    Job_mst item = new Job_mst();
                    item.setAnnoId(edge.get("annoId").toString());
                    item.setAnnoSubject(edge.getString("annoSubject"));
                    item.setClassCdNm(edge.getString("classCdNm"));
                    item.setEmpTypeCdNm(edge.getString("empTypeCdNm"));
                    item.setSubJobCdNm(edge.getString("subJobCdNm"));
                    item.setSysCompanyCdNm(edge.getString("sysCompanyCdNm"));
                    item.setJobDetailLink(edge.getString("jobDetailLink"));
                    long jobDetailLink = extractYearsFromJobPage(edge.getString("jobDetailLink"));
                    item.setPersonalHistory(jobDetailLink);
                    if (edge.get("staYmdTime").equals(null)) {
                        item.setStartDate("영입종료시");
                    } else {
                        item.setStartDate(edge.getString("staYmdTime").replace(".", "-"));
                    }
                    if (edge.get("endYmdTime").equals(null)) {
                        item.setEndDate("영입종료시");
                    } else {
                        item.setEndDate(edge.getString("endYmdTime").replace(".", "-"));
                    }
                    result.add(item);
                }

                idx += 10;
            }

            for (Job_mst item : result) {
                String replaeTitle = item.getAnnoSubject().replaceAll("\\[.*?]\\s*", "");
                item.setAnnoSubject(replaeTitle);

                if ("AI/ML".equals(item.getSubJobCdNm())) {
                    item.setSubJobCdNm("ML");
                }

                switch (item.getSysCompanyCdNm()) {
                    case "NAVER": {
                        item.setSysCompanyCdNm("네이버");
                        break;
                    }
                    case "NAVER FINANCIAL": {
                        item.setSysCompanyCdNm("네이버페이");
                        break;
                    }
                    case "NAVER WEBTOON": {
                        item.setSysCompanyCdNm("네이버웹툰");
                        break;
                    }
               }
            }

            for (Job_mst item : result) {
                if (item.getAnnoSubject().contains("Kubernetes 서비스 개발")) {
                    item.setSubJobCdNm(JobEnums.DevOps.getTitle());
                }
            }

            crawlerCommonService.getNotSaveJobItem("NAVER", result);

        } catch (Exception e) {
            log.error("Error occurred while crawling jobs: {}", e.getMessage(), e);
        }

        return CompletableFuture.completedFuture(result);
    }

    public HttpURLConnection createConnection(URL url) throws Exception {
        return (HttpURLConnection) url.openConnection();
    }

    public long extractYearsFromJobPage(String url) {
        List<Long> years = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(url).timeout(3000).get();
            String pageText = doc.body().text();
            String regex = "(\\d+)년 이상"; // 숫자를 캡처 그룹으로 묶음
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(pageText);

            while (matcher.find()) {
                String numberStr = matcher.group(1); // 첫 번째 캡처 그룹(숫자) 가져오기
                try {
                    long year = Long.parseLong(numberStr); // String을 long으로 변환
                    years.add(year);
                } catch (NumberFormatException e) {
                    System.err.println("오류: '" + numberStr + "'를 long으로 변환할 수 없습니다. " + e.getMessage());
                }
            }
            Collections.sort(years);
        } catch (IOException e) {
            log.error("웹 페이지 연결 또는 파싱 중 오류 발생: {}", e.getMessage());
            return 0;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return years.isEmpty() ? 0 : years.get(0);
    }

}
