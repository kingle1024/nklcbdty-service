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
public class TossJobCrawlerService implements JobCrawler {

    @Autowired
    private final CrawlerCommonService commonService;

    public TossJobCrawlerService(CrawlerCommonService commonService) {
        this.commonService = commonService;
    }

    @Override
    public List<Job_mst> crawlJobs() {
        List<Job_mst> result = new ArrayList<>();

        try {
            final String apiUrl = "https://api-public.toss.im/api/v3/ipd-eggnog/career/job-groups";
            final String jsonResponse = commonService.fetchApiResponse(apiUrl);

            // JSON 객체로 변환
            JSONObject jsonResult = new JSONObject(jsonResponse);

            // edges 배열 가져오기
            JSONArray edges = jsonResult.getJSONArray("success");
            for (int i = 0; i < edges.length(); i++) {
                JSONObject edge = edges.getJSONObject(i);
                JSONArray contents = edge.getJSONArray("jobs");
                for (int j = 0; j < contents.length(); j++) {
                    JSONObject contentItem = contents.getJSONObject(j);
                    JSONArray metadata = contentItem.getJSONArray("metadata");
                    Job_mst item = new Job_mst();
                    for (int k = 0; k < metadata.length(); k++) {
                        JSONObject jsonObject = metadata.getJSONObject(k);
                        String name = jsonObject.getString("name");
                        String value = jsonObject.getString("value");
                        switch (name) {
                            case "포지션의 소속 자회사를 선택해 주세요.": {
                                item.setSysCompanyCdNm(value);
                                break;
                            }
                            case "Job Category를 선택해 주세요. (미사용 필드입니다)": {
                                item.setClassCdNm(value);
                            }
                            case "커리어 페이지 노출 Job Category 값을 선택해주세요": {
                                item.setSubJobCdNm(value);
                                break;
                            }
                            case "Employment_Type": {
                                if ("정규직".equals(value)) {
                                    item.setEmpTypeCdNm("정규");
                                } else {
                                    item.setEmpTypeCdNm(value);
                                }
                                break;
                            }
                        }
                    }
                    item.setAnnoId(contentItem.getLong("id"));
                    item.setAnnoSubject(contentItem.getString("title"));
                    item.setJobDetailLink(contentItem.getString("absolute_url"));
                    result.add(item);
                }
            }

            commonService.saveAll("TOSS", result);

        } catch (Exception e) {
            log.error("Error occurred while crawling jobs: {}", e.getMessage(), e);
        }

        return result;
    }
}
