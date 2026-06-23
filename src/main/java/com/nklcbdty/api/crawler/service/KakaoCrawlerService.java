package com.nklcbdty.api.crawler.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nklcbdty.api.crawler.common.CrawlerCommonService;
import com.nklcbdty.api.crawler.common.JobEnums;
import com.nklcbdty.api.crawler.dto.PersonalHistoryDto;
import com.nklcbdty.common.vo.Job_mst;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class KakaoCrawlerService {

    private final CrawlerCommonService crawlerCommonService;

    // 카카오페이손해보험 마감일(UTC) -> KST 변환용
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter KST_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    public KakaoCrawlerService(CrawlerCommonService crawlerCommonService) {
        this.crawlerCommonService = crawlerCommonService;
    }

    @Async
    public CompletableFuture<List<Job_mst>> crawlJobs() {
        List<Job_mst> result = new ArrayList<>();
        try {
            List<Job_mst> kakaoHealth = new ArrayList<>();
            addRecruitHealth(kakaoHealth);
            result.addAll(kakaoHealth);

            List<Job_mst> kakaoGames = new ArrayList<>();
            addRecruitGames(kakaoGames);
            result.addAll(kakaoGames);

            List<Job_mst> kakaopaySec = new ArrayList<>();
            addRecruitPaySec(kakaopaySec);
            result.addAll(kakaopaySec);

            addRecruitContent("P", result);
            addRecruitContent("S", result);

            refineResult(result);

            List<Job_mst> kakaoBankResult = new ArrayList<>();
            addRecruitKakaoBank(kakaoBankResult);
            refineBankResult(kakaoBankResult);

            result.addAll(kakaoBankResult);

            // 카카오페이손해보험(그리팅 기반). sysCompanyCdNm 이 refineResult 의 default("카카오")로
            // 덮이지 않도록 뱅크와 동일하게 refineResult 이후에 합친다.
            List<Job_mst> kakaoPayInsResult = new ArrayList<>();
            addRecruitPayIns(kakaoPayInsResult);
            result.addAll(kakaoPayInsResult);

        } catch (Exception e) {
            log.error("Error occurred while crawling jobs: {}", e.getMessage(), e);
        }
        return CompletableFuture.completedFuture(crawlerCommonService.getNotSaveJobItem("KAKAO", result));
    }

    private void refineBankResult(List<Job_mst> kakaoBankResult) {
        for (Job_mst item : kakaoBankResult) {
            if (item.getAnnoSubject().contains("QA")) {
                item.setSubJobCdNm(JobEnums.QA.getTitle());
            } else if (item.getAnnoSubject().contains("iOS")) {
                item.setSubJobCdNm(JobEnums.iOS.getTitle());
            } else if (item.getAnnoSubject().contains("머신러닝 엔지니어") ||
                item.getAnnoSubject().contains("딥러닝 엔지니어") ||
                item.getAnnoSubject().contains("AI Research Engineer")
            ) {
                item.setSubJobCdNm(JobEnums.ML.getTitle());
            } else if (
                item.getAnnoSubject().contains("데이터 분석 담당자") ||
                item.getAnnoSubject().contains("데이터 분석가")
            ) {
                item.setSubJobCdNm(JobEnums.DataAnalyst.getTitle());
            } else if (item.getAnnoSubject().contains("데이터 엔지니어") ||
                item.getAnnoSubject().contains("데이터 플랫폼 엔지니어") ||
                item.getAnnoSubject().contains("데이터 마트 개발 담당자")
            ) {
                item.setSubJobCdNm(JobEnums.DataEngineering.getTitle());
            } else if (item.getAnnoSubject().contains("데이터베이스 관리자")) {
                item.setSubJobCdNm(JobEnums.DBA.getTitle());
            } else if (item.getAnnoSubject().contains("AI Search Engineer")) {
                item.setSubJobCdNm(JobEnums.AI.getTitle());
            } else if (
                item.getAnnoSubject().contains("시스템 엔지니어") ||
                item.getAnnoSubject().contains("시스템 아키텍트/엔지니어") ||
                item.getAnnoSubject().contains("네트워크 아키텍트")
            ) {
                item.setSubJobCdNm(JobEnums.Infra.getTitle());
            } else if (item.getAnnoSubject().contains("기획자") || item.getAnnoSubject().contains("기획 담당자")
                || item.getAnnoSubject().contains("기획 및 운영 담당자") || item.getAnnoSubject().contains("신사업 전략 담당자")
                || item.getAnnoSubject().contains("업무 담당자") || item.getAnnoSubject().contains("전략 담당자")
                || item.getAnnoSubject().contains("운영 담당자") || item.getAnnoSubject().contains("심사 담당자")
                || item.getAnnoSubject().contains("서비스 담당자") || item.getAnnoSubject().contains("Fraud 담당자")
                || item.getAnnoSubject().contains("시스템 담당자") || item.getAnnoSubject().contains("세일즈 담당자")
                || item.getAnnoSubject().contains("외환 상품/서비스 담당자") || item.getAnnoSubject()
                .contains("여신 Anti-Fraud 담당자") || item.getAnnoSubject().contains("FDS 시스템 담당자")) {
                item.setSubJobCdNm(JobEnums.PO.getTitle());
            } else if (
                item.getAnnoSubject().contains("네트워크 아키텍트") ||
                item.getAnnoSubject().contains("네트워크 엔지니어")
            ) {
                item.setSubJobCdNm(JobEnums.Infra.getTitle());
            } else if (item.getAnnoSubject().contains("PM")) {
                item.setSubJobCdNm(JobEnums.PM.getTitle());
            } else if (item.getAnnoSubject().contains("프론트엔드 개발자") || item.getAnnoSubject().contains("React")) {
                item.setSubJobCdNm(JobEnums.FrontEnd.getTitle());
            } else if (item.getAnnoSubject().contains("서버 개발자") || item.getAnnoSubject().contains("백엔드 개발자")
                || item.getAnnoSubject().contains("DKOS(Kubernetes as a Service) 개발자")) {
                item.setSubJobCdNm(JobEnums.BackEnd.getTitle());
            } else if (item.getAnnoSubject().contains("상담업무 시스템 개발자") || item.getAnnoSubject().contains("업무 개발자")
                || item.getAnnoSubject().contains("서비스 개발자") || item.getAnnoSubject().contains("개발 담당자")) {
                item.setSubJobCdNm(JobEnums.FullStack.getTitle());
            } else if (item.getAnnoSubject().contains("DevOps") || item.getAnnoSubject()
                .contains("Kubernetes Engineer") || item.getAnnoSubject().contains("서비스 아키텍트")
                || item.getAnnoSubject().contains("뱅킹 아키텍트")) {
                item.setSubJobCdNm(JobEnums.DevOps.getTitle());
            } else if (item.getAnnoSubject().contains("브랜드 디자") || item.getAnnoSubject().contains("프로덕트 디자이너")
                || item.getAnnoSubject().contains("UX/UI 디자이너")) {
                item.setSubJobCdNm(JobEnums.ProductDesigner.getTitle());
            } else if (
                item.getAnnoSubject().contains("시스템보안 담당자") ||
                item.getAnnoSubject().contains("보안담당자") ||
                item.getAnnoSubject().contains("보안 담당자") ||
                item.getAnnoSubject().contains("인증라이선스 담당자")
            ) {
                item.setSubJobCdNm(JobEnums.Security.getTitle());
            } else if (item.getAnnoSubject().contains("Developer Relations") || item.getAnnoSubject()
                .contains("모니터링 담당자")) {
                item.setSubJobCdNm(JobEnums.TechnicalSupport.getTitle());
            } else {
                item.setSubJobCdNm(null);
            }
        }
    }

    private void refineResult(List<Job_mst> result) {
        for (Job_mst job : result) {
            if (job.getAnnoSubject().contains("DevOps") ||
                job.getAnnoSubject().contains("Kubernetes Engine 개발자") ||
                job.getAnnoSubject().contains("DKOS(Kubernetes as a Service) 개발자") ||
                job.getAnnoSubject().contains("SRE(Site Reliability Engineer) 엔지니어") ||
                job.getAnnoSubject().contains("MLOps Engineer")
            ) {
                job.setSubJobCdNm(JobEnums.DevOps.getTitle());
            } else if (job.getAnnoSubject().contains("프론트") ||
                job.getAnnoSubject().contains("FE 개발자") ||
                job.getAnnoSubject().contains("Frontend Develop")
            ) {
                job.setSubJobCdNm(JobEnums.FrontEnd.getTitle());
            } else if (job.getAnnoSubject().contains("백엔드") ||
                job.getAnnoSubject().contains("서버 개발자") ||
                job.getAnnoSubject().contains("Back-End") ||
                job.getAnnoSubject().contains("CI/CD 서비스 개발") ||
                job.getAnnoSubject().contains("데이터 응용 플랫폼 개발") ||
                job.getAnnoSubject().contains("시스템(ActionBase) 개발") ||
                job.getAnnoSubject().contains("모델 플랫폼 개발") ||
                job.getAnnoSubject().contains("벡엔드 개발자") ||
                job.getAnnoSubject().contains("검색 엔지니어")
            ) {
                job.setSubJobCdNm(JobEnums.BackEnd.getTitle());
            } else if (
                job.getAnnoSubject().contains("FE/BE") ||
                job.getAnnoSubject().contains("플랫폼 개발자") ||
                job.getAnnoSubject().contains("웹 풀스택") ||
                job.getAnnoSubject().contains("웹 크롤링") ||
                job.getAnnoSubject().contains("어드민 개발")
            ) {
                job.setSubJobCdNm(JobEnums.FullStack.getTitle());
            } else if (job.getAnnoSubject().contains("Data Analyst") ||
                job.getAnnoSubject().contains("데이터 사이언티스트") ||
                job.getAnnoSubject().contains("데이터 분석가") ||
                job.getAnnoSubject().contains("데이터 분석 담당자")
            ) {
                job.setSubJobCdNm(JobEnums.DataAnalyst.getTitle());
            } else if (
                job.getAnnoSubject().contains("클라이언트 SDK 개발자") ||
                job.getAnnoSubject().contains("Mobile App Develop")
            ) {
                job.setSubJobCdNm(JobEnums.Android.getTitle());
            } else if (job.getAnnoSubject().contains("macOS 플랫폼 개발자")) {
                job.setSubJobCdNm(JobEnums.iOS.getTitle());
            } else if (job.getAnnoSubject().contains("Flutter")) {
                job.setSubJobCdNm(JobEnums.Flutter.getTitle());
            } else if (job.getAnnoSubject().contains("데이터 엔지니어") ||
                job.getAnnoSubject().contains("NoSQL 엔지니어") ||
                job.getAnnoSubject().contains("Data Engineer")
            ) {
                job.setSubJobCdNm(JobEnums.DataEngineering.getTitle());
            } else if (
                job.getAnnoSubject().contains("머신러닝 엔지니어") ||
                job.getAnnoSubject().contains("ML 엔지니어") ||
                job.getAnnoSubject().contains("머신러닝 research scientist") ||
                job.getAnnoSubject().contains("데이터플랫폼 엔지니어")
            ) {
                job.setSubJobCdNm(JobEnums.ML.getTitle());
            } else if (job.getAnnoSubject().contains("DB운영")) {
                job.setSubJobCdNm(JobEnums.DBA.getTitle());
            } else if (
                job.getAnnoSubject().contains("AI Safety 엔지니어") ||
                job.getAnnoSubject().contains("LLM Research")
            ) {
                job.setSubJobCdNm(JobEnums.AI.getTitle());
            } else if (job.getAnnoSubject().contains("안정성 관리") || job.getAnnoSubject().contains("Technical Writer")
                || job.getAnnoSubject().contains("기술 문서") || job.getAnnoSubject().contains("기술지원 엔지니어")
                || job.getAnnoSubject().contains("Developer Relations")
                || job.getAnnoSubject().contains("모니터링 담당자")
            ) {
                job.setSubJobCdNm(JobEnums.TechnicalSupport.getTitle());
            } else if (job.getAnnoSubject().contains("PM")) {
                job.setSubJobCdNm(JobEnums.PM.getTitle());
            } else if (job.getAnnoSubject().contains("QA")) {
                job.setSubJobCdNm(JobEnums.QA.getTitle());
            } else if (
                job.getAnnoSubject().contains("보안 담당자") ||
                job.getAnnoSubject().contains("개인정보보호 시니어") ||
                job.getAnnoSubject().contains("개인정보보호 주니어") ||
                job.getAnnoSubject().contains("개인정보보호 리더") ||
                job.getAnnoSubject().contains("정보보안") ||
                job.getAnnoSubject().contains("IT 서비스 안정성")
            ) {
                job.setSubJobCdNm(JobEnums.Security.getTitle());
            } else if (
                job.getAnnoSubject().contains("취약점 분석") ||
                job.getAnnoSubject().contains("모의해킹") ||
                job.getAnnoSubject().contains("컴플라이언스 엔지니어") ||
                job.getAnnoSubject().contains("정보보안 컴플라이언스 엔지니어") ||
                job.getAnnoSubject().contains("보안 엔지니어")
            ) {
                job.setSubJobCdNm(JobEnums.SecurityEngineering.getTitle());
            } else if (job.getAnnoSubject().contains("기획 담당자") || job.getAnnoSubject().contains("기획자")) {
                job.setSubJobCdNm(JobEnums.PO.getTitle());
            } else if (job.getAnnoSubject().contains("프로덕트 디자이너")) {
                job.setSubJobCdNm(JobEnums.ProductDesigner.getTitle());
            } else if (job.getAnnoSubject().contains("시스템 엔지니어") ||
                job.getAnnoSubject().contains("네트워크 드라이버 개발") ||
                job.getAnnoSubject().contains("FPGA Engineer") ||
                job.getAnnoSubject().contains("컴퓨팅 서비스 개발") ||
                job.getAnnoSubject().contains("네트워킹 서비스 개발") ||
                job.getAnnoSubject().contains("스토리지 플랫폼 개발") ||
                job.getAnnoSubject().contains("네트워크 엔지니어") ||
                job.getAnnoSubject().contains("클라우드 Managed 서비스 엔지니어")
            ) {
                job.setSubJobCdNm(JobEnums.Infra.getTitle());
            } else {
                job.setSubJobCdNm(null);
            }

            if ("etc".equals(job.getSubJobCdNm())) {
                if (job.getAnnoSubject().contains("네트워크 드라이버") ||
                    job.getAnnoSubject().contains("FPGA Engineer") ||
                    job.getAnnoSubject().contains("컴퓨팅 서비스") ||
                    job.getAnnoSubject().contains("네트워킹 서비스") ||
                    job.getAnnoSubject().contains("네트워크 엔지니어") ||
                    job.getAnnoSubject().contains("시스템 엔지니어") ||
                    job.getAnnoSubject().contains("클라우드 Managed 서비스 엔지니어") ||
                    job.getAnnoSubject().contains("클라우드 네트워크 엔지니어") ||
                    job.getAnnoSubject().contains("스토리지 플랫폼 개발자")
                ) {
                    job.setSubJobCdNm(JobEnums.Infra.getTitle());
                }
            }
        }

        for (Job_mst job : result) {
            String replaeTitle = job.getAnnoSubject().replaceAll("\\[.*?]\\s*", "");
            job.setAnnoSubject(replaeTitle);
            if ("Server".equals(job.getSubJobCdNm())) {
                job.setSubJobCdNm(JobEnums.BackEnd.getTitle());
            }
            if (job.getAnnoSubject().contains("카카오페이")) {
                String replaceTitle = job.getAnnoSubject().replace("카카오페이", "");
                job.setAnnoSubject(replaceTitle);
            } else if (job.getAnnoSubject().contains("카카오모빌리티")) {
                String replaceTitle = job.getAnnoSubject().replace("카카오모빌리티", "");
                job.setAnnoSubject(replaceTitle);
            } else if (job.getAnnoSubject().contains("카카오엔터프라이즈")) {
                String replaceTitle = job.getAnnoSubject().replace("카카오엔터프라이즈", "");
                job.setAnnoSubject(replaceTitle);
            } else if (job.getAnnoSubject().contains("카카오게임즈")) {
                String replaceTitle = job.getAnnoSubject().replace("카카오게임즈", "");
                job.setAnnoSubject(replaceTitle);
            } else if (job.getAnnoSubject().contains("카카오헬스케어")) {
                String replaceTitle = job.getAnnoSubject().replace("카카오헬스케어", "");
                job.setAnnoSubject(replaceTitle);
            }

            switch (job.getSysCompanyCdNm()) {
                case "kakao mobility": {
                    job.setSysCompanyCdNm("카카오 모빌리티");
                    break;
                }
                case "Kakao Pay Corp.": {
                    job.setSysCompanyCdNm("카카오 페이");
                    break;
                }
                case "Kakao Enterprise": {
                    job.setSysCompanyCdNm("카카오 엔터프라이즈");
                    break;
                }
                case "KakaoGames": {
                    job.setSysCompanyCdNm("카카오 게임즈");
                    break;
                }
                case "kakaohealthcare", "카카오 헬스케어": {
                    job.setSysCompanyCdNm("카카오 헬스케어");
                    break;
                }
                case "카카오 페이증권": {
                    job.setSysCompanyCdNm("카카오 페이증권");
                    break;
                }
                default: {
                    job.setSysCompanyCdNm("카카오");
                }
            }
        }
    }

    private void addRecruitGames(List<Job_mst> kakaoGames) {
        String buildName = "";
        try {
            Document doc = Jsoup.connect("https://recruit.kakaogames.com/ko/joinjuskr").get();
            Element scriptElement = doc.getElementById("__NEXT_DATA__");

            if (scriptElement != null) {
                // 첫 번째 og:image 메타 태그의 content 속성 값 가져오기
                String jsonData = scriptElement.html();

                                // JSON 파싱
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(jsonData);

                // buildId 값 추출
                buildName = jsonNode.get("buildId").asText();


                // 정규식에 매칭되는 부분이 있는지 확인
                if (buildName == null) {
                    log.error("No match found buildName");
                    return;
                }
            }

            final String apiUrl = "https://recruit.kakaogames.com/_next/data/" + buildName + "/ko/joinjuskr.json?locale=ko&page=joinjuskr";
            final String jsonResponse = crawlerCommonService.fetchApiResponse(apiUrl);

            // JSON 객체로 변환
            JSONObject jsonResult = new JSONObject(jsonResponse);

            JSONArray data = jsonResult.getJSONObject("pageProps")
                .getJSONObject("dehydratedState")
                .getJSONArray("queries")
                .getJSONObject(2)
                .getJSONObject("state")
                .optJSONArray("data");
            if (data == null) {
                log.error("카카오게임즈 채용공고 data 미존재");
                return;
            }

            for (int i = 0; i < data.length(); i++) {
                JSONObject jsonObject = data.getJSONObject(i);
                Object endDateObj = jsonObject.get("dueDate");
                if (crawlerCommonService.isCloseDate(endDateObj)) {
                    continue;
                }

                Job_mst item = new Job_mst();
                String endDate;
                if (endDateObj != null && !JSONObject.NULL.equals(endDateObj)) {
                    endDate = endDateObj.toString();
                    OffsetDateTime offsetDateTime = OffsetDateTime.parse(endDate, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    String formattedDate = offsetDateTime.format(formatter);
                    item.setEndDate(formattedDate);
                }
                String title = jsonObject.get("title").toString();
                item.setAnnoSubject(title);
                item.setSysCompanyCdNm("카카오 게임즈");
                // jsonObject.getJSONObject("openingJobPosition").getJSONArray("openingJobPositions").getJSONObject(1)
                JSONArray openingJobPositions = jsonObject.getJSONObject("openingJobPosition")
                    .getJSONArray("openingJobPositions");
                item.setEmpTypeCdNm("정규");
                long minCareerFrom = Long.MAX_VALUE;
                for (int j = 0; j < openingJobPositions.length(); j++) {
                    JSONObject openingJobPosition = openingJobPositions.getJSONObject(j);
                    String employ = null;
                    if (!openingJobPosition.isNull("jobPositionEmployment") ) {
                        employ = openingJobPosition.getJSONObject("jobPositionEmployment").getString("employmentType");
                    }
                    if (!openingJobPosition.isNull("jobPositionCareer")) {
                        if (!openingJobPosition.getJSONObject("jobPositionCareer").isNull("careerFrom")) {
                            final long careerFrom = openingJobPosition.getJSONObject("jobPositionCareer").getLong("careerFrom");
                            minCareerFrom = Math.min(minCareerFrom, careerFrom);
                        }
                    }

                    if ("FULL_TIME_WORKER".equals(employ)) {
                        item.setEmpTypeCdNm("정규");
                        break;
                    } else if ("CONTRACT_WORKER".equals(employ)) {
                        item.setEmpTypeCdNm("비정규");
                    }
                }
                if (minCareerFrom != Long.MAX_VALUE) {
                    item.setPersonalHistory(minCareerFrom);
                }
                Object from = jsonObject.getJSONObject("careerInfo").get("from");
                if (from instanceof Integer) {
                    item.setPersonalHistory(((Integer) from).longValue());
                }
                Object to = jsonObject.getJSONObject("careerInfo").get("to");
                if (to instanceof Integer) {
                    item.setPersonalHistoryEnd(((Integer) to).longValue());
                }
                String openingId = String.valueOf(jsonObject.getInt("openingId"));
                item.setJobDetailLink("https://recruit.kakaogames.com/ko/o/" + openingId);
                item.setAnnoId(openingId);
                kakaoGames.add(item);
            }
        } catch (Exception e) {
            log.error("Error occurred while fetching Kakao Games jobs: {}", e.getMessage(), e);
        }
    }

    /**
     * 카카오페이손해보험 채용 크롤. 그리팅(Greeting) 기반 Next.js 사이트로,
     * {@code /ko/apply} HTML 의 __NEXT_DATA__ 에서 buildId 를 추출해
     * {@code /_next/data/<buildId>/ko/apply.json} 의 openings 배열을 파싱한다.
     */
    private void addRecruitPayIns(List<Job_mst> kakaoPayInsResult) {
        final String host = "https://career.kakaopayinscorp.co.kr";
        String buildName = "";
        try {
            Document doc = Jsoup.connect(host + "/ko/apply").get();
            Element scriptElement = doc.getElementById("__NEXT_DATA__");
            if (scriptElement != null) {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(scriptElement.html());
                buildName = jsonNode.get("buildId").asText();
            }
            if (buildName == null || buildName.isBlank()) {
                log.error("카카오페이손해보험 buildId 추출 실패");
                return;
            }

            final String apiUrl = host + "/_next/data/" + buildName + "/ko/apply.json?locale=ko&page=apply";
            final String jsonResponse = crawlerCommonService.fetchApiResponse(apiUrl);

            // pageProps.dehydratedState.queries 에서 queryKey[0] == "openings" 인 항목의 state.data
            JSONArray queries = new JSONObject(jsonResponse)
                .getJSONObject("pageProps")
                .getJSONObject("dehydratedState")
                .getJSONArray("queries");
            JSONArray data = null;
            for (int i = 0; i < queries.length(); i++) {
                JSONObject query = queries.getJSONObject(i);
                JSONArray queryKey = query.optJSONArray("queryKey");
                if (queryKey != null && queryKey.length() > 0 && "openings".equals(queryKey.optString(0))) {
                    JSONObject state = query.optJSONObject("state");
                    if (state != null) {
                        data = state.optJSONArray("data");
                    }
                    break;
                }
            }
            if (data == null) {
                log.error("카카오페이손해보험 openings data 미존재 (buildId={})", buildName);
                return;
            }

            for (int i = 0; i < data.length(); i++) {
                JSONObject jsonObject = data.getJSONObject(i);

                // 미배포 공고 스킵
                if (jsonObject.has("deploy") && !jsonObject.optBoolean("deploy", true)) {
                    continue;
                }

                // dueDate(UTC, 예 "2026-06-21T14:59:59Z") -> KST ZonedDateTime.
                // 공용 isCloseDate 는 타임존을 무시(UTC 벽시계 비교)해 마감일 당일 KST 공고를
                // 9시간 일찍 종료 처리하므로, 손보 분기는 KST 기준으로 마감 판정/저장한다.
                Object endDateObj = jsonObject.opt("dueDate");
                ZonedDateTime kstEnd = null;
                if (endDateObj != null && !JSONObject.NULL.equals(endDateObj)) {
                    kstEnd = OffsetDateTime.parse(endDateObj.toString(), DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        .atZoneSameInstant(KST);
                    if (kstEnd.toLocalDateTime().isBefore(LocalDateTime.now())) {
                        continue;
                    }
                }

                Job_mst item = new Job_mst();
                if (kstEnd != null) {
                    item.setEndDate(kstEnd.format(KST_DATE_FORMAT));
                }

                item.setAnnoSubject(jsonObject.getString("title").trim());
                item.setSysCompanyCdNm("카카오페이손해보험");
                String openingId = String.valueOf(jsonObject.get("openingId"));
                item.setAnnoId(openingId);
                item.setJobDetailLink(host + "/o/" + openingId);
                item.setEmpTypeCdNm("정규");

                // 직군/직무/근무지/경력/고용형태는 openingJobPositions[0] 기준
                JSONObject jobPositionWrap = jsonObject.optJSONObject("openingJobPosition");
                if (jobPositionWrap != null) {
                    JSONArray positions = jobPositionWrap.optJSONArray("openingJobPositions");
                    if (positions != null && positions.length() > 0) {
                        JSONObject position = positions.getJSONObject(0);

                        JSONObject occupation = position.optJSONObject("workspaceOccupation");
                        if (occupation != null) {
                            item.setClassCdNm(occupation.optString("occupation", null));
                        }
                        JSONObject job = position.optJSONObject("workspaceJob");
                        if (job != null) {
                            item.setSubJobCdNm(job.optString("job", null));
                        }
                        JSONObject place = position.optJSONObject("workspacePlace");
                        if (place != null) {
                            item.setWorkplace(convertPayInsWorkplace(place));
                        }
                        JSONObject career = position.optJSONObject("jobPositionCareer");
                        if (career != null) {
                            item.setPersonalHistory(career.optLong("careerFrom", 0L));
                            if (!career.isNull("careerTo")) {
                                item.setPersonalHistoryEnd(career.optLong("careerTo", 0L));
                            }
                        }
                        JSONObject employment = position.optJSONObject("jobPositionEmployment");
                        if (employment != null && "CONTRACT_WORKER".equals(employment.optString("employmentType"))) {
                            item.setEmpTypeCdNm("비정규");
                        }
                    }
                }

                refinePayInsSubJob(item);
                kakaoPayInsResult.add(item);
            }
        } catch (Exception e) {
            log.error("Error occurred while fetching Kakao Pay Insurance jobs: {}", e.getMessage(), e);
        }
    }

    /**
     * 근무지 객체를 표시용 문자열로 변환. 판교(경기 성남) 본사는 "판교"로 정규화.
     */
    private String convertPayInsWorkplace(JSONObject place) {
        final String region = place.optString("place", "");
        if (region.contains("판교") || region.contains("성남") || region.contains("경기")) {
            return "판교";
        }
        if (region.contains("서울")) {
            return JobEnums.SEOUL.getTitle();
        }
        final String location = place.optString("location", null);
        return (location == null || location.isBlank()) ? region : location;
    }

    /**
     * 제목 기반 개발 직무 보정. 명확한 케이스만 선반영하고 나머지는 Gemini 분류에 맡긴다.
     */
    private void refinePayInsSubJob(Job_mst item) {
        if (item.getAnnoSubject() == null) {
            return;
        }
        final String title = item.getAnnoSubject().toUpperCase().replace(" ", "");
        if (title.contains("BACKEND") || title.contains("BACK-END") || title.contains("서버개발")) {
            item.setSubJobCdNm(JobEnums.BackEnd.getTitle());
        } else if (title.contains("FRONTEND") || title.contains("FRONT-END")) {
            item.setSubJobCdNm(JobEnums.FrontEnd.getTitle());
        } else if (title.contains("DEVOPS") || title.contains("SRE")) {
            item.setSubJobCdNm(JobEnums.DevOps.getTitle());
        } else if (title.contains("(PM)") || title.contains("기획자") || title.contains("PRODUCTMANAGER")) {
            item.setSubJobCdNm(JobEnums.PM.getTitle());
        }
    }

    private void addRecruitHealth(List<Job_mst> kakaopayHealth) {
        String buildName = "";
        try {
            Document doc = Jsoup.connect("https://recruit.kakaohealthcare.com/recruit").get();
            Elements iconLinkTags = doc.select("link[rel=icon], link[rel=shortcut icon]");

            if (!iconLinkTags.isEmpty()) {
                // 첫 번째 og:image 메타 태그의 content 속성 값 가져오기
                String imageUrl = Objects.requireNonNull(iconLinkTags.first()).attr("href");

                // URL에서 "brand/"와 그 다음 "/" 사이의 문자열을 추출하는 정규식
                String regex = "/homepage/([^/]+)/";
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(imageUrl);

                // 정규식에 매칭되는 부분이 있는지 확인
                if (matcher.find()) {
                    buildName = matcher.group(1); // 첫 번째 그룹 (UUID 부분) 추출
                } else {
                    log.error("No match found in the URL: {}", imageUrl);
                    return;
                }
            }

            final String apiUrl = "https://api.ninehire.com/identity-access/homepage/recruitments?companyId="+buildName+"&page=1&countPerPage=20&externalTitle=&order=created_at_desc";
            final String jsonResponse = crawlerCommonService.fetchApiResponse(apiUrl);

            // JSON 객체로 변환
            JSONObject jsonResult = new JSONObject(jsonResponse);

            JSONArray jsonArray = jsonResult.getJSONArray("results");
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                Object endDateObj = jsonObject.get("deadlineValue");
                if (crawlerCommonService.isCloseDate(endDateObj)) {
                    continue;
                }

                Job_mst item = new Job_mst();
                String endDate;
                if (endDateObj != null && !JSONObject.NULL.equals(endDateObj)) {
                    endDate = jsonObject.getString("deadlineValue");
                    OffsetDateTime offsetDateTime = OffsetDateTime.parse(endDate, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    String formattedDate = offsetDateTime.format(formatter);
                    item.setEndDate(formattedDate);
                }
                String title = jsonObject.get("externalTitle").toString();
                item.setAnnoSubject(title);
                String empTypeCdNm = jsonObject.getJSONArray("employmentType").getString(0);
                if("full_time".equals(empTypeCdNm)) {
                    item.setEmpTypeCdNm("정규");
                } else {
                    item.setEmpTypeCdNm("비정규");
                }
                if (!jsonObject.isNull("jobGroup")) {
                    item.setClassCdNm(jsonObject.getJSONObject("jobGroup").get("title").toString());
                }
                item.setPersonalHistory(jsonObject.getJSONObject("career").getJSONObject("range").getLong("over"));
                item.setPersonalHistoryEnd(jsonObject.getJSONObject("career").getJSONObject("range").getLong("below"));
                item.setSysCompanyCdNm("카카오 헬스케어");
                item.setJobDetailLink("https://recruit.kakaohealthcare.com/job_posting/" + jsonObject.get("addressKey"));
                item.setAnnoId(jsonObject.getString("addressKey"));
                kakaopayHealth.add(item);
            }
        } catch (Exception e) {
            log.error("Error occurred while fetching Kakao Health jobs: {}", e.getMessage(), e);
        }
    }

    private void addRecruitPaySec(List<Job_mst> kakaopaySec) {
        String buildName = "";

        try {
            // 웹 페이지에 연결하여 Document 객체 가져오기
            Document doc = Jsoup.connect("https://career.kakaopaysec.com/job_posting").get();

            // property 속성이 "og:image"인 meta 태그 선택
            Elements metaTags = doc.select("meta[property=og:image]");

            if (!metaTags.isEmpty()) {
                // 첫 번째 og:image 메타 태그의 content 속성 값 가져오기
                String imageUrl = Objects.requireNonNull(metaTags.first()).attr("content");

                // URL에서 "brand/"와 그 다음 "/" 사이의 문자열을 추출하는 정규식
                String regex = "/brand/([^/]+)/";
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(imageUrl);

                // 정규식에 매칭되는 부분이 있는지 확인
                if (matcher.find()) {
                    buildName = matcher.group(1); // 첫 번째 그룹 (UUID 부분) 추출
                } else {
                    log.error("No match found in the URL: {}", imageUrl);
                    return;
                }
            }

            final String apiUrl = "https://api.ninehire.com/identity-access/homepage/recruitments?companyId="+buildName+"&page=1&countPerPage=20&externalTitle=&order=created_at_desc";
            log.info("카카오페이증권 buildName {}", buildName);
            final String jsonResponse = crawlerCommonService.fetchApiResponse(apiUrl);

            // JSON 객체로 변환
            JSONObject jsonResult = new JSONObject(jsonResponse);

            JSONArray jsonArray = jsonResult.getJSONArray("results");
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                Object endDateObj = jsonObject.get("deadlineValue");
                if (crawlerCommonService.isCloseDate(endDateObj)) {
                    continue;
                }

                Job_mst item = new Job_mst();
                String endDate;
                if (endDateObj != null && !JSONObject.NULL.equals(endDateObj)) {
                    endDate = jsonObject.getString("deadlineValue");
                    OffsetDateTime offsetDateTime = OffsetDateTime.parse(endDate, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    String formattedDate = offsetDateTime.format(formatter);
                    item.setEndDate(formattedDate);
                }
                String title = jsonObject.get("externalTitle").toString();
                item.setAnnoSubject(title);
                String empTypeCdNm = jsonObject.getJSONArray("employmentType").isEmpty() ? "" : jsonObject.getJSONArray("employmentType").getString(0);
                if("full_time".equals(empTypeCdNm)) {
                    item.setEmpTypeCdNm("정규");
                } else {
                    item.setEmpTypeCdNm("비정규");
                }
                if (!jsonObject.isNull("jobGroup")) {
                    item.setClassCdNm(jsonObject.getJSONObject("jobGroup").get("title").toString());
                }
                if (jsonObject.get("career") instanceof JSONObject) {
                    if (jsonObject.getJSONObject("career").get("range") instanceof JSONObject) {
                        item.setPersonalHistory(jsonObject.getJSONObject("career").getJSONObject("range").getLong("over"));
                        item.setPersonalHistoryEnd(jsonObject.getJSONObject("career").getJSONObject("range").getLong("below"));
                    }
                }
                item.setSysCompanyCdNm("카카오 페이증권");
                item.setJobDetailLink("https://career.kakaopaysec.com/job_posting/" + jsonObject.get("addressKey"));
                item.setAnnoId(jsonObject.getString("addressKey"));
                kakaopaySec.add(item);
            }

        } catch (Exception e) {
            log.error("Error occurred while fetching Kakao Pay jobs: {}", e.getMessage(), e);
        }
    }

    public void addRecruitKakaoBank(List<Job_mst> result) {

        int idx = 1;
        int lastIdx = 999;
        while (true) {
            if (idx > lastIdx) {
                break;
            }

            String apiUrl = "https://recruit.kakaobank.com/api/recruits";
            String body = "{\"pageNumber\":" + idx + ",\"pageSize\":20}";
            String jsonResponse = crawlerCommonService.fetchApiResponsePost(apiUrl, body);
            if (jsonResponse.isEmpty()) {
                break;
            }

            JSONObject root = new JSONObject(jsonResponse);
            JSONArray jobList = root.getJSONArray("list");
            JSONObject paging = root.getJSONObject("paging");
            lastIdx = paging.getInt("totalPages");
            for (int i = 0; i < jobList.length(); i++) {
                JSONObject edge = jobList.getJSONObject(i);
                Object endDate = edge.get("receiveEndDatetime");
                if (isCloseDate(endDate)) {
                    continue;
                }

                String title = edge.getString("recruitNoticeName");
                long recruitNoticeSn = edge.getLong("recruitNoticeSn");

                String employeeTypeName;
                if (title.contains("계약직") || title.contains("인턴")) {
                    employeeTypeName = "비정규";
                } else {
                    employeeTypeName = "정규";
                }
                String jobType = edge.optString("recruitClassName", "");
                String jobDescription = getJobDescription(String.valueOf(recruitNoticeSn));

                Job_mst item = new Job_mst();
                item.setAnnoId(String.valueOf(recruitNoticeSn));
                item.setAnnoSubject(title);
                item.setEmpTypeCdNm(employeeTypeName);
                item.setClassCdNm("Tech");
                item.setSubJobCdNm(jobType);
                item.setSysCompanyCdNm("카카오 뱅크");
                String recruitNoticeUrl = edge.optString("recruitNoticeUrl", "");
                if (!recruitNoticeUrl.isEmpty()) {
                    if (!recruitNoticeUrl.startsWith("http")) {
                        recruitNoticeUrl = "https://" + recruitNoticeUrl;
                    }
                    item.setJobDetailLink(recruitNoticeUrl);
                } else {
                    item.setJobDetailLink("https://recruit.kakaobank.com/jobs/" + recruitNoticeSn);
                }
                item.setEndDate(String.valueOf(endDate));
                PersonalHistoryDto personalHistory = crawlerCommonService.getPersonalHistory(jobDescription);
                item.setPersonalHistory(personalHistory.getFrom());
                item.setPersonalHistoryEnd(personalHistory.getTo());
                result.add(item);
            }
            idx++;
        }

    }

    private String getJobDescription(String recruitNoticeSn) {
        try {
            String apiUrl = "https://recruit.kakaobank.com/api/recruits/" + recruitNoticeSn;
            String jsonResponse = crawlerCommonService.fetchApiResponse(apiUrl);
            if (jsonResponse.isEmpty()) {
                return "";
            }

            JSONObject jobDetail = new JSONObject(jsonResponse);
            String contents = jobDetail.optString("contents", "");
            if (contents.isEmpty()) {
                return "";
            }

            return contents;
        } catch (Exception e) {
            log.warn("카카오뱅크 채용 상세 조회 실패 (sn={}): {}", recruitNoticeSn, e.getMessage());
            return "";
        }
    }

    private void addRecruitContent(String type, List<Job_mst> result) {
        final String companyType;
        if ("P".equals(type)) {
            companyType = "KAKAO";
        } else {
            companyType = "SUBSIDIARY";
        }

        int idx = 1;
        while (true) {
            boolean isContinue = addResult(idx, type, result, companyType);
            if (!isContinue) {
                break;
            }
            idx++;
            if (idx > 500) {
                break;
            }
        }
    }

    private boolean addResult(int idx, String type, List<Job_mst> result, String companyType) {
        final String apiUrl = "https://careers.kakao.com/public/api/job-list?"
            + "skillSet=&part=TECHNOLOGY"
            + "&company="+ companyType
            + "&keyword=&employeeType=&page=" + idx;
        final String jsonResponse = crawlerCommonService.fetchApiResponse(apiUrl);

        // JSON 파싱 및 변환
        JSONArray jobList = new JSONObject(jsonResponse).getJSONArray("jobList");
        if (jobList.isEmpty()) {
            return false;
        }

        // edges 배열을 반복
        for (int i = 0; i < jobList.length(); i++) {
            JSONObject edge = jobList.getJSONObject(i);
            String title = edge.getString("jobOfferTitle");
            Object endDate = edge.get("endDate");
            if(isCloseDate(endDate)) {
                continue;
            }

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
            String skillSetType = "etc";
            if(edge.has("skillSetList") && !edge.isNull("skillSetList")) {
                skillSetType = edge.getJSONArray("skillSetList").getJSONObject(0).getString("skillSetType");
            }
            String qualification = edge.getString("qualification");

            String companyNameEn = edge.getString("companyNameEn");
            Job_mst item = new Job_mst();
            item.setAnnoSubject(String.valueOf(title).trim());
            item.setAnnoId(String.valueOf(jobOfferId));
            item.setEmpTypeCdNm(employeeTypeName);
            item.setClassCdNm(jobType);
            item.setSubJobCdNm(skillSetType);
            item.setSysCompanyCdNm(companyNameEn);
            item.setJobDetailLink("https://careers.kakao.com/jobs/" + type + "-" + jobOfferId +"?skillSet=&part=TECHNOLOGY"
                + "&company="+ companyType +"&keyword=&employeeType=&page=" + idx);
            if (endDate == null || JSONObject.NULL.equals(endDate)) {
                item.setEndDate("영입종료시");
            } else {
                DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                LocalDateTime endDateTime = LocalDateTime.parse(endDate.toString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                item.setEndDate(endDateTime.format(outputFormatter));
            }
            PersonalHistoryDto personalHistory;
            if ("-".equals(qualification)) {
                String introduction = edge.getString("introduction");
                personalHistory = crawlerCommonService.getPersonalHistory(introduction);
            } else {
                personalHistory = crawlerCommonService.getPersonalHistory(qualification);
            }
            item.setPersonalHistory(personalHistory.getFrom());
            item.setPersonalHistoryEnd(personalHistory.getTo());

            result.add(item);
        }

        try {
            JSONObject response = new JSONObject(jsonResponse);
            final int totalPage = response.getInt("totalPage");
            return idx != totalPage;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isCloseDate(Object endDate) {
        if (endDate == null || JSONObject.NULL.equals(endDate)) {
            return false;
        }
        LocalDateTime endDateTime;
        String endDateStr = String.valueOf(endDate);

        if (endDateStr.contains("T")) {
            endDateTime = LocalDateTime.parse(endDateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } else {
            endDateTime = LocalDateTime.parse(endDateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }

        // 현재 시간 가져오기
        LocalDateTime now = LocalDateTime.now();

        // 비교
        return now.isAfter(endDateTime);
    }
}
