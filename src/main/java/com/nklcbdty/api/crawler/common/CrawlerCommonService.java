package com.nklcbdty.api.crawler.common;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.nklcbdty.api.crawler.repository.CrawlerRepository;
import com.nklcbdty.api.crawler.vo.Job_mst;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CrawlerCommonService {

    private final CrawlerRepository crawlerRepository;

    @Autowired
    public CrawlerCommonService(CrawlerRepository crawlerRepository) {
        this.crawlerRepository = crawlerRepository;
    }

    public String fetchApiResponse(String apiUrl) {

        try {
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setRequestMethod("GET");

            StringBuilder response = new StringBuilder();
            try (BufferedReader in = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
            }

            return response.toString();
        } catch (Exception e) {
            log.error("Error occurred while fetching API response: {}", e.getMessage(), e);
            throw new ApiException("Failed to fetch API response");  // 커스텀 예외 던지기
        }
    }

    public void saveAll(String company, List<Job_mst> result) {
        List<Long> annoIds = result.stream().map(Job_mst::getAnnoId).collect(Collectors.toList());
        List<Job_mst> existingJobs = crawlerRepository.findAllByAnnoIdIn(annoIds);
        List<Job_mst> jobsToSave = new ArrayList<>();

        for (Job_mst job : result) {
            boolean exists = existingJobs.stream().anyMatch(e -> e.getAnnoId().equals(job.getAnnoId()));

            if (exists) {
                Job_mst existingJob = existingJobs.stream()
                    .filter(e -> e.getAnnoId().equals(job.getAnnoId()))
                    .findFirst()
                    .orElse(null);
                if (existingJob != null && !existingJob.getAnnoSubject().equals(job.getAnnoSubject())) {
                    // annoSubject가 다를 경우에만 저장
                    jobsToSave.add(job);
                }
            } else {
                jobsToSave.add(job);
            }
        }

        // 한 번에 저장
        if (!jobsToSave.isEmpty()) {
            for (Job_mst item : jobsToSave) {
                item.setCompanyCd(company);
            }
            crawlerRepository.saveAll(jobsToSave);
        }
    }
}
