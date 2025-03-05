package com.nklcbdty.api.crawler.service;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nklcbdty.api.crawler.common.CrawlerCommonService;
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
    public List<Job_mst> crawlJobs() {
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
                        String title = node.getString("title");
                        String companies = node.getJSONArray("companies").getJSONObject(0).getString("name");
                        Job_mst item = new Job_mst();
                        item.setAnnoId(node.getLong("strapiId"));
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
                        item.setJobDetailLink("https://careers.linecorp.com/ko/jobs/" + node.getLong("strapiId"));
                        result.add(item);
                    }
                }
            }

            crawlerCommonService.saveAll("LINE", result);

        } catch (Exception e) {
            log.error("Error occurred while crawling jobs: {}", e.getMessage(), e);
        }

        return result;
    }
}
