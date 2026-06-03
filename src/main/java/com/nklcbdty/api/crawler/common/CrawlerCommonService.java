package com.nklcbdty.api.crawler.common;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nklcbdty.api.ai.nlp.PersonalHistoryEnsemble;
import com.nklcbdty.api.ai.service.GeminiService;
import com.nklcbdty.api.crawler.dto.PersonalHistoryDto;
import com.nklcbdty.common.crawler.repository.CrawlerRepository;
import com.nklcbdty.common.exception.ApiException;
import com.nklcbdty.common.vo.Job_mst;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class CrawlerCommonService {

    private final CrawlerRepository crawlerRepository;
    private final GeminiService geminiService;
    private final PersonalHistoryEnsemble personalHistoryEnsemble;

    @Autowired
    public CrawlerCommonService(CrawlerRepository crawlerRepository,
                                GeminiService geminiService,
                                PersonalHistoryEnsemble personalHistoryEnsemble) {
        this.crawlerRepository = crawlerRepository;
        this.geminiService = geminiService;
        this.personalHistoryEnsemble = personalHistoryEnsemble;
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

    public String fetchApiResponsePost(String apiUrl, String body) {
        try {
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = body.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

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
            log.error("Error occurred while fetching API response (POST): {}", e.getMessage(), e);
            throw new ApiException("Failed to fetch API response");
        }
    }

    public List<Job_mst> getNotSaveJobItem(String company, List<Job_mst> result) {
        reconcileEndedJobs(company, result);

        List<String> annoIds = result.stream().map(Job_mst::getAnnoId).collect(Collectors.toList());
        List<Job_mst> existingJobs = crawlerRepository.findAllByAnnoIdIn(annoIds);
        List<Job_mst> jobsToSave = new ArrayList<>();

        for (Job_mst job : result) {
            boolean exists = existingJobs.stream().anyMatch(e -> e.getAnnoId().equals(job.getAnnoId()));

            if (!exists) {
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

    public void refineJobItemBygemini(List<Job_mst> result) {
        if (result.isEmpty()) {
            log.info("result가 비어 있어서 Gemini 호출하지 않음");
            return;
        }
        log.info("----- Gemini 호출중... -----");
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
    	List<String> annoIds;
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
        // JSONObject.NULL.equals(null) 는 true 반환 → 과거에는 종료 여부를 정반대로 판단해
        // null deadline 공고가 살아남고 endDate=NULL 로 저장되어 EmailService 의 isLive 가 true → 메일에 종료 공고가 계속 노출됐다.
        if (endDate == null || JSONObject.NULL.equals(endDate)) {
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

    public List<Job_mst> saveAll(List<Job_mst> result) {
        return crawlerRepository.saveAll(result);
    }

    private static final List<DateTimeFormatter> END_DATE_FORMATTERS = Arrays.asList(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    );

    // 크롤 결과에 없는 기존 row 는 사이트에서 내려간 것으로 보고 endDate=어제 로 마킹.
    // 빈 결과는 네트워크 실패일 수 있어 건너뜀.
    void reconcileEndedJobs(String companyCd, List<Job_mst> currentlyCrawled) {
        if (companyCd == null || currentlyCrawled == null || currentlyCrawled.isEmpty()) {
            log.warn("reconcileEndedJobs 스킵: companyCd={}, crawledSize={}",
                companyCd, currentlyCrawled == null ? 0 : currentlyCrawled.size());
            return;
        }
        Set<String> crawledAnnoIds = currentlyCrawled.stream()
            .map(Job_mst::getAnnoId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(HashSet::new));

        List<Job_mst> dbRows = crawlerRepository.findAllByCompanyCd(companyCd);
        LocalDateTime now = LocalDateTime.now();
        String endedDateStr = now.minusDays(1).format(END_DATE_FORMATTERS.get(0));

        List<Job_mst> toMark = new ArrayList<>();
        for (Job_mst row : dbRows) {
            if (row.getAnnoId() == null) continue;
            if (crawledAnnoIds.contains(row.getAnnoId())) continue;
            if (!isCurrentlyLive(row.getEndDate(), now)) continue;
            row.setEndDate(endedDateStr);
            toMark.add(row);
        }

        if (!toMark.isEmpty()) {
            crawlerRepository.saveAll(toMark);
            log.info("reconcileEndedJobs: companyCd={} 종료 처리 {}건", companyCd, toMark.size());
        }
    }

    private boolean isCurrentlyLive(String endDateStr, LocalDateTime now) {
        if (endDateStr == null || "영입종료시".equals(endDateStr)) {
            return true;
        }
        for (DateTimeFormatter formatter : END_DATE_FORMATTERS) {
            try {
                return LocalDateTime.parse(endDateStr, formatter).isAfter(now);
            } catch (DateTimeParseException ignored) { }
        }
        // 파싱 불가는 보수적으로 살아있는 것으로 간주 (reconciliation 대상 제외)
        return true;
    }

    public PersonalHistoryDto extractPersonalHistoryFromJobPage(String url) {
        try {
            Document doc;
            if (url.contains("coupang.jobs")) {
                doc = Jsoup.connect(url)
                    .header("Cookie", "ph_cookiePref=NFAT; _fbp=fb.1.1740312267141.693781373128074153; __Host-vId=1a4db879-6e82-447f-97f5-25d0e8268655; _gcl_au=1.1.1800247100.1748611754; UMB_SESSION=CfDJ8NqBX9onXMZHpSTh77lB%2FQkhxkL5DSas%2FYeafOa0WLLboYk4tHDwNQUtr19MsQu%2B1WtnCLGEevabF%2Bndnc5%2FEByVX6NDoZSEFam5z5YwKtTu3SqldIKc4h1jAWlEkvjLx%2FCBjTV0EPrjLDPJe9FhRQn7OhkCHcdYxj8XSkBrIo0d; _clck=tp51lh%7C2%7Cfxy%7C0%7C1880; _gid=GA1.2.80406493.1753579741; _ga=GA1.1.1363205650.1740312267; _ga_WN9DBP9Q8X=GS2.1.s1753579741$o30$g1$t1753580115$j49$l0$h0; _clsk=1ifku0s%7C1753580747196%7C5%7C1%7Ca.clarity.ms%2Fcollect")
                    .timeout(3000)
                    .get();
            } else {
                doc = Jsoup.connect(url)
                    .timeout(3000)
                    .get();
            }

            String pageText = doc.body().text(); // 웹 페이지 전체 텍스트 가져오기
            return getPersonalHistory(pageText); // getPersonalHistory 메서드 호출
        } catch (IOException e) {
            log.error("웹 페이지 연결 또는 파싱 중 오류 발생: {}", e.getMessage());
            return new PersonalHistoryDto(); // 오류 발생 시 기본 DTO 반환
        } catch (Exception e) {
            log.error("예상치 못한 오류 발생: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public PersonalHistoryDto getPersonalHistory(String qualification) {
        return personalHistoryEnsemble.extract(qualification);
    }
}
