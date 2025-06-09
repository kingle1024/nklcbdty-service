package com.nklcbdty.api.crawler.common;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nklcbdty.api.ai.service.GeminiService;
import com.nklcbdty.api.crawler.repository.CrawlerRepository;
import com.nklcbdty.api.crawler.vo.Job_mst;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class CrawlerCommonService {

    private final CrawlerRepository crawlerRepository;
    private final GeminiService geminiService;

    @Autowired
    public CrawlerCommonService(CrawlerRepository crawlerRepository, GeminiService geminiService) {
        this.crawlerRepository = crawlerRepository;
        this.geminiService = geminiService;
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

    public List<Job_mst> getNotSaveJobItem(String company, List<Job_mst> result) {
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
        }

        return jobsToSave;
    }

    public void refineJobData(List<Job_mst> result) {
        refineJobItemBygemini(result);

        for (Job_mst job : result) {
            if (job.getEmpTypeCdNm() == null) {
                job.setEmpTypeCdNm(JobEnums.REGULAR.getTitle());
            } else if (job.getEmpTypeCdNm().contains("계약")) {
                job.setEmpTypeCdNm(JobEnums.CONTRACT.getTitle());
            }

            if (job.getWorkplace() == null) {
                job.setWorkplace(JobEnums.SEOUL.getTitle());
            }

            // 채용 공고로 보정하기
            final String jobTitle = job.getAnnoSubject().toUpperCase().replace(" ", "");
            if ("BACK-END".equalsIgnoreCase(jobTitle)) {
                job.setSubJobCdNm(JobEnums.BackEnd.getTitle());
            } else if ("SERVER".equalsIgnoreCase(jobTitle)) {
                job.setSubJobCdNm(JobEnums.BackEnd.getTitle());
            } else if (jobTitle.contains("BACKEND")) {
                job.setSubJobCdNm(JobEnums.BackEnd.getTitle());
            } else if (jobTitle.contains("SERVERENGINEER")) {
                job.setSubJobCdNm(JobEnums.BackEnd.getTitle());
            } else if (jobTitle.contains("FULL-STACK")) {
                job.setSubJobCdNm(JobEnums.FullStack.getTitle());
            } else if (jobTitle.contains("SOFTWAREENGINEER-") && "R&D".equals(job.getClassCdNm())) {
                job.setSubJobCdNm(JobEnums.FullStack.getTitle());
            } else if (jobTitle.contains("IOS")) {
                job.setSubJobCdNm(JobEnums.iOS.getTitle());
            } else if (jobTitle.contains("ANDROID")) {
                job.setSubJobCdNm(JobEnums.Android.getTitle());
            } else if (jobTitle.contains("DEVOPS")) {
                job.setSubJobCdNm(JobEnums.DevOps.getTitle());
            } else if (jobTitle.contains("DATAANALYST")) {
                job.setSubJobCdNm(JobEnums.DataAnalyst.getTitle());
            } else if (jobTitle.contains("DATASCIENTIST")) {
                job.setSubJobCdNm(JobEnums.DataAnalyst.getTitle());
            } else if (jobTitle.contains("DBA")) {
                job.setSubJobCdNm(JobEnums.DBA.getTitle());
            } else if (jobTitle.contains("인재풀 등록")) {
                job.setSubJobCdNm(null);
            }
        }
    }

    private void refineJobItemBygemini(List<Job_mst> result) {
        Mono<Map<String, String>> classificationResultsMono = geminiService.classifyJobTitles(result);

        try {
            Map<String, String> classificationMap = classificationResultsMono.block();

            if (classificationMap != null) {
                log.info("----- Gemini 분류 결과 수신 및 DB 업데이트 시작 -----");

                for (Job_mst job : result) {
                    String predictedJobType = classificationMap.get(job.getAnnoSubject().strip());

                    if (predictedJobType != null) {
                         job.setSubJobCdNm(predictedJobType); // Job_mst 클래스에 setPredictedJobType() 메서드가 필요합니다.
                    } else {
                        log.error("Warning: No classification found for title: \"{}\"", job.getAnnoSubject());
                    }
                }
                log.info("Gemini 븐류 결과 적용 완료. 총 {}개의 직무가 업데이트되었습니다.", result.size());
            } else {
                 log.error("Warning: Gemini classification returned no result (Mono was empty).");
            }

        } catch (Exception e) {
            // block() 호출 중 오류 발생 시 예외 처리
            log.error("Error during Gemini classification (blocked call): {}", e.getMessage(), e);
            e.printStackTrace();
        }
        log.info("----- refineJobData 메서드 동기적 처리 완료 -----");
    }

    /**
     * <p> 크롤러 사이트 서버 통신 후, JSON or HTML데이터를 파싱한 List<\Job_mst\> 데이터를 
     * 	   annoId 중복여부를 판별하고, Repository에 데이터를 삽입한다.
     * </p>
     * @return void
     * */
    public List<Job_mst> insertJobMst(List<Job_mst> resList) {
        if (resList.isEmpty()) {
            log.info("resList.isEmpty()");
            return resList;
        }
    	List<Long> annoIds;
		List<Job_mst> existingJobs;
		List<Job_mst> jobsToSave = new ArrayList<>();
		
		annoIds = resList.stream().map(Job_mst::getAnnoId).collect(Collectors.toList());
		existingJobs = crawlerRepository.findAllByAnnoIdIn(annoIds);
		
		for (Job_mst jobItem : resList) {
			boolean exists = existingJobs.stream().anyMatch(item -> item.getAnnoId().equals(jobItem.getAnnoId()));
			if (exists) {
				Job_mst existingJob = existingJobs.stream()
					.filter(item -> item.getAnnoId().equals(jobItem.getAnnoId()))
					.findFirst()
					.orElse(null);
				if (existingJob != null && !existingJob.getAnnoSubject().equals(jobItem.getAnnoSubject())) {
                    // annoSubject가 다를 경우에만 저장
                    jobsToSave.add(jobItem);
                }
			} else {
				jobsToSave.add(jobItem);
			}
		}
		
		if (!jobsToSave.isEmpty()) {
			for (Job_mst jobItem : jobsToSave) {
				jobItem.setCompanyCd("COUPANG");
			}
		}

        return jobsToSave;
    }
    
    /**
	 * <p>밀리세컨드로 된 데이터를 yyyy-MM-dd HH:mm:ss format String으로 변환한다. </p>
	 * @author David Lee
	 * */
	public String formatCurrentTime() {
		long currentTimeMillis = System.currentTimeMillis();
		
        // 밀리초를 LocalDateTime으로 변환
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(currentTimeMillis), ZoneId.systemDefault());

        // 원하는 형식으로 포맷팅
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedDate = dateTime.format(formatter);

        return formattedDate;
	}

    public boolean isCloseDate(Object endDate) {
        if (endDate.equals(null)) {
            return false;
        }
        LocalDateTime endDateTime;
        String endDateStr = String.valueOf(endDate);

        if (endDateStr.contains("T")) {
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(endDateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            endDateTime = offsetDateTime.toLocalDateTime();
        } else {
            endDateTime = LocalDateTime.parse(endDateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }

        // 현재 시간 가져오기
        LocalDateTime now = LocalDateTime.now();

        // 비교
        return now.isAfter(endDateTime);
    }

    public List<Job_mst> getNotSaveJobItem(List<Job_mst> jobsToSave) {
        return crawlerRepository.saveAll(jobsToSave);
    }
}
