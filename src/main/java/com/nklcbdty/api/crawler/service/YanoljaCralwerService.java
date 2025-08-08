package com.nklcbdty.api.crawler.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.nklcbdty.api.crawler.common.CrawlerCommonService;
import com.nklcbdty.api.crawler.common.JobEnums;
import com.nklcbdty.api.crawler.interfaces.JobCrawler;
import com.nklcbdty.api.crawler.vo.Job_mst;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class YanoljaCralwerService {

    private final CrawlerCommonService commonService;

    @Autowired
    public YanoljaCralwerService(CrawlerCommonService commonService) {
        this.commonService = commonService;
    }

    @Async
    public CompletableFuture<List<Job_mst>> crawlJobs() {
        List<Job_mst> result = new ArrayList<>();

        try {
            Document doc = Jsoup.connect("https://careers.yanolja.co/home").get();
            Elements scripts = doc.getElementsByTag("script");
            String regex = "/_next/static/(.*?)/_ssgManifest.js";
            Pattern pattern = Pattern.compile(regex);
            String buildName = "";
            for (Element script : scripts) {
                String src = script.attr("src"); // src 속성 값 가져오기

                // 정규식으로 매칭
                Matcher matcher = pattern.matcher(src);
                if (matcher.find()) {
                    buildName = matcher.group(1); // 첫 번째 그룹 추출
                }
            }

            final String apiUrl = "https://careers.yanolja.co/_next/data/"+buildName+"/ko/home.json?occupations=R%26D&employments=FULL_TIME_WORKER&page=home";
            final String jsonResponse = commonService.fetchApiResponse(apiUrl);

            // JSON 객체로 변환
            JSONObject jsonResult = new JSONObject(jsonResponse);

            // edges 배열 가져오기
            JSONArray jsonArray = jsonResult.getJSONObject("pageProps")
                .getJSONObject("dehydratedState")
                .getJSONArray("queries");
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);

                if (jsonObject.getJSONObject("state") == null ||
                    jsonObject.getJSONObject("state").get("data") instanceof JSONObject) {
                    continue;
                }

                JSONArray jsonArray1 = jsonObject.getJSONObject("state").getJSONArray("data");
                for (int j = 0; j < jsonArray1.length(); j++) {
                    Job_mst item = new Job_mst();
                    JSONObject data = jsonArray1.getJSONObject(j);
                    item.setAnnoId(data.get("openingId").toString());
                    item.setAnnoSubject(data.getString("title"));
                    if(data.has("job") && !data.isNull("job")) {
                        item.setClassCdNm(data.getString("job"));
                    } else {
                        item.setClassCdNm("기타");
                    }
                    String employmentType = data.getJSONObject("openingJobPosition").getJSONArray("openingJobPositions").getJSONObject(0).getJSONObject("jobPositionEmployment").getString("employmentType");
                    switch (employmentType) {
                        case "FULL_TIME_WORKER": {
                            item.setEmpTypeCdNm("정규");
                            break;
                        }
                        case "CONTRACT_WORKER": {
                            item.setEmpTypeCdNm("비정규");
                            break;
                        }
                        default: {
                            item.setEmpTypeCdNm(employmentType);
                        }
                    }
                    item.setJobDetailLink("https://careers.yanolja.co/o/" + item.getAnnoId());
                    item.setSubJobCdNm(item.getClassCdNm());
                    if(data.has("subsidiary") && !data.isNull("subsidiary")) {
                        item.setSysCompanyCdNm(data.getString("subsidiary"));
                    } else {
                        item.setSysCompanyCdNm("야놀자");
                    }
                    if (commonService.isCloseDate(data.get("dueDate"))) {
                        item.setEndDate(data.get("dueDate").toString());
                    }
                    Object from = data.getJSONObject("careerInfo").get("from");
                    if (from instanceof Integer) {
                        item.setPersonalHistory(((Integer) from).longValue());
                    }
                    Object to = data.getJSONObject("careerInfo").get("to");
                    if (to instanceof Integer) {
                        item.setPersonalHistoryEnd(((Integer) to).longValue());
                    }
                    result.add(item);
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

        } catch (Exception e) {
            log.error("Error occurred while crawling jobs: {}", e.getMessage(), e);
        }

        return CompletableFuture.completedFuture(commonService.getNotSaveJobItem("YANOLJA", result));
    }
}
