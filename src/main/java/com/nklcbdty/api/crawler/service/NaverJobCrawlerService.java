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
import com.nklcbdty.api.crawler.dto.PersonalHistoryDto;
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
                    PersonalHistoryDto personalHistoryDto = crawlerCommonService.extractPersonalHistoryFromJobPage(edge.getString("jobDetailLink"));
                    item.setPersonalHistory(personalHistoryDto.getFrom());
                    item.setPersonalHistoryEnd(personalHistoryDto.getTo());
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

}
