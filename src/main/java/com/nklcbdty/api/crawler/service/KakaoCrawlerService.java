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
            String apiUrl = "https://careers.kakao.com/public/api/job-list?skillSet=&part=TECHNOLOGY&company=KAKAO&keyword=&employeeType=&page=1";
            final String jsonResponse = crawlerCommonService.fetchApiResponse(apiUrl);

            // JSON 파싱 및 변환
            JSONArray jobList = new JSONObject(jsonResponse).getJSONArray("jobList");

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
                String skillSetType = edge.getJSONArray("skillSetList").getJSONObject(0).getString("skillSetType");
                String companyNameEn = edge.getString("companyNameEn");

                Job_mst item = new Job_mst();
                item.setAnnoSubject(String.valueOf(title));
                item.setAnnoId(jobOfferId);
                item.setEmpTypeCdNm(employeeTypeName);
                item.setClassCdNm(jobType);
                item.setSubJobCdNm(skillSetType);
                item.setSysCompanyCdNm(companyNameEn);
                item.setJobDetailLink("https://careers.kakao.com/jobs/S-" + jobOfferId);
                result.add(item);
            }

            crawlerCommonService.saveAll("KAKAO", result);

        } catch (Exception e) {
            log.error("Error occurred while crawling jobs: {}", e.getMessage(), e);
        }
        return List.of();
    }


}
