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
public class YanoljaCralwerService implements JobCrawler {

    private final CrawlerCommonService commonService;

    @Autowired
    public YanoljaCralwerService(CrawlerCommonService commonService) {
        this.commonService = commonService;
    }

    @Override
    public List<Job_mst> crawlJobs() {
        List<Job_mst> result = new ArrayList<>();

        try {
            final String apiUrl = "https://careers.yanolja.co/_next/data/qQ_dgc_yJImTqGdio_XOn/ko.json";
            final String jsonResponse = commonService.fetchApiResponse(apiUrl);

            // JSON 객체로 변환
            JSONObject jsonResult = new JSONObject(jsonResponse);

            // edges 배열 가져오기
            JSONArray jsonArray = jsonResult.getJSONObject("pageProps")
                .getJSONObject("dehydratedState")
                .getJSONArray("queries");
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String queryHash = jsonObject.getString("queryHash");
                if(!"[\"openings\",\"\"]".equals(queryHash)) {
                    continue;
                }

                JSONArray jsonArray1 = jsonObject.getJSONObject("state").getJSONArray("data");
                for (int j = 0; j < jsonArray1.length(); j++) {
                    Job_mst item = new Job_mst();
                    JSONObject data = jsonArray1.getJSONObject(j);
                    item.setAnnoId(data.getLong("openingId"));
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
                    result.add(item);
                }
            }

            commonService.saveAll("YANOLJA", result);
        } catch (Exception e) {
            log.error("Error occurred while crawling jobs: {}", e.getMessage(), e);
        }

        return result;
    }
}
