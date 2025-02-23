package com.nklcbdty.api.crawler.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nklcbdty.api.crawler.common.CrawlerCommonService;
import com.nklcbdty.api.crawler.interfaces.JobCrawler;
import com.nklcbdty.api.crawler.repository.CrawlerRepository;
import com.nklcbdty.api.crawler.vo.Job_mst;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NaverJobCrawlerService implements JobCrawler {

    private final CrawlerRepository crawlerRepository;
    private final CrawlerCommonService crawlerCommonService;
    private final String apiUrl;

    @Autowired
    public NaverJobCrawlerService(CrawlerRepository crawlerRepository, CrawlerCommonService crawlerCommonService) {
        this.crawlerRepository = crawlerRepository;
        this.crawlerCommonService = crawlerCommonService;
        this.apiUrl = createApiUrl();
    }

    private String createApiUrl() {
        return "https://recruit.navercorp.com/rcrt/loadJobList.do?subJobCdArr=1010004&sysCompanyCdArr=&empTypeCdArr=&entTypeCdArr=&workAreaCdArr=&sw=&subJobCdData=1010004&firstIndex=0";
    }

    @Override
    public List<Job_mst> crawlJobs() {
        List<Job_mst> result = Collections.emptyList();

        try {
            final String jsonResponse = crawlerCommonService.fetchApiResponse(apiUrl);

            // JSON 파싱 및 변환
            JSONArray jobList = new JSONObject(jsonResponse).getJSONArray("list");
            ObjectMapper objectMapper = new ObjectMapper();
            Job_mst[] jobArray = objectMapper.readValue(jobList.toString(), Job_mst[].class);
            result = new ArrayList<>(List.of(jobArray));

            // 모든 기존 데이터 조회
            List<Long> annoIds = result.stream().map(Job_mst::getAnnoId).collect(Collectors.toList());
            List<Job_mst> existingJobs = crawlerRepository.findAllByAnnoIdIn(annoIds); // 기존 Job_mst 조회

            List<Job_mst> jobsToSave = new ArrayList<>();

            for (Job_mst job : result) {
                boolean exists = existingJobs.stream().anyMatch(e -> e.getAnnoId().equals(job.getAnnoId()));

                if (exists) {
                    // 기존 데이터가 존재하는 경우
                    Job_mst existingJob = existingJobs.stream()
                            .filter(e -> e.getAnnoId().equals(job.getAnnoId()))
                            .findFirst()
                            .orElse(null);
                    if (existingJob != null && !existingJob.getAnnoSubject().equals(job.getAnnoSubject())) {
                        // annoSubject가 다를 경우에만 저장
                        jobsToSave.add(job);
                    }
                } else {
                    // 존재하지 않으면 새로 저장
                    jobsToSave.add(job);
                }
            }

            // 한 번에 저장
            if (!jobsToSave.isEmpty()) {
                crawlerRepository.saveAll(jobsToSave);
            }

        } catch (Exception e) {
            log.error("Error occurred while crawling jobs: {}", e.getMessage(), e);
        }

        return result;
    }

    public HttpURLConnection createConnection(URL url) throws Exception {
        return (HttpURLConnection) url.openConnection();
    }
}
