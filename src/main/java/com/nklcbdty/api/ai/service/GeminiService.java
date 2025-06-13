package com.nklcbdty.api.ai.service;

import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.nklcbdty.api.ai.dto.GeminiCandidate;
import com.nklcbdty.api.ai.dto.GeminiContent;
import com.nklcbdty.api.ai.dto.GeminiPart;
import com.nklcbdty.api.ai.dto.GeminiRequest;
import com.nklcbdty.api.ai.dto.GeminiResponse;
import com.nklcbdty.api.crawler.vo.Job_mst;

// 여러 제목 처리를 위한 새로운 임포트
import java.util.HashMap;
import java.util.Map;

import reactor.core.publisher.Mono; // 비동기 처리를 위한 Mono 사용

@Service
public class GeminiService {

    // WebClient 주입 (WebClient Bean 설정은 별도로 필요합니다)
    private final WebClient geminiWebClient;

    // application.properties 또는 환경 변수에서 Google Gemini API 키 주입
    @Value("${google.gemini.api-key}")
    private String apiKey;

    // JSON 직렬화/역직렬화를 위한 ObjectMapper (Spring Boot에서는 자동으로 빈으로 등록되기도 합니다)
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 생성자를 통한 WebClient 주입
    public GeminiService(WebClient geminiWebClient) {
        this.geminiWebClient = geminiWebClient;
    }

    /**
     * 여러 채용 공고 제목 리스트를 받아 Google Gemini API를 호출하여 각 제목의 직무를 분류합니다.
     * 이 메서드가 여러 제목 분류의 주 진입점입니다.
     *
     * @param jobTitles 분류할 채용 공고 제목 리스트
     * @return 각 제목과 예측된 직무 유형을 담는 Map을 포함하는 Mono (비동기 결과)
     */
    public Mono<Map<String, String>> classifyJobTitles(List<Job_mst> jobTitles) {
        if (jobTitles == null || jobTitles.isEmpty()) {
            return Mono.just(Collections.emptyMap()); // 빈 리스트인 경우 빈 맵 반환
        }

        // 1. 여러 제목을 포함하는 하나의 프롬프트 구성
        String combinedPrompt = buildCombinedPrompt(jobTitles);

        // 2. buildGeminiRequestBody를 사용하여 요청 본문 DTO 생성
        Object requestBody = buildGeminiRequestBody(combinedPrompt);

        // 3. WebClient를 사용하여 Google Gemini API에 POST 요청 전송 및 응답 처리
        return geminiWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        // WebClientConfig에서 baseUrl이 설정되었다고 가정
                        // 실제 Gemini API 엔드포인트 경로를 여기에 지정해야 합니다.
                        // 예: uriBuilder.path("/v1/models/gemini-pro:generateContent")
                        .queryParam("key", apiKey)
                        .build())
                .bodyValue(requestBody)
                .retrieve()
                // API 응답 본문을 String (JSON 문자열)으로 변환
                .bodyToMono(String.class)
                // 수신한 JSON 문자열을 파싱하여 모델이 생성한 텍스트(여러 분류 결과 포함)를 추출
                .map(this::parseGeminiResponse)
                // 추출된 텍스트에서 각 제목별 분류 결과 파싱
                .map(this::parseClassificationsFromText);
    }

    /**
     * 여러 채용 공고 제목을 포함하는 단일 프롬프트 메시지를 구성합니다.
     * 모델에게 각 제목별 분류 결과를 특정 형식으로 반환하도록 지시합니다.
     *
     * @param jobTitles 분류할 채용 공고 제목 리스트
     * @return 모델에게 보낼 전체 프롬프트 문자열
     */
    private String buildCombinedPrompt(List<Job_mst> jobTitles) {
        // 모델이 각 제목별 분류 결과를 반환하도록 명확히 지시하는 프롬프트 구성
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("""
                주어진 채용 공고 제목 리스트를 보고 각 제목에 대해 가장 적합한 하나의 직무 유형을 분류해주세요.
                각 제목과 분류 결과를 '제목 -> 직무유형' 형식으로 한 줄에 하나씩 응답해주세요.
                다른 설명이나 추가 문구 없이 오직 분류 결과 리스트만 응답해야 합니다.

                분류 기준은 다음과 같습니다:
                - Backend
                - FrontEnd
                - iOS
                - Android
                - DevOps
                - AI
                - Infra
                - DataAnalyst
                - DataEngineering
                - FullStack
                - DBA
                - SecurityEngineering
                - Security
                - SAP
                - ML
                - QA
                - TechnicalSupport
                - PM
                - Flutter
                - ProductDesigner
                - PO
                - 기타 (위 분류에 해당하지 않는 경우)

                예시)
                제목 리스트:
                - 시니어 프론트엔드 개발자 채용
                - ML 엔지니어 모십니다
                - 글로벌 백엔드 개발자

                응답 예시:
                AI -> AI
                LLM -> AI
                Growth Engineering -> AI
                프론트엔드 개발 -> FrontEnd
                ML 엔지니어 -> ML
                MLOps 엔지니어 -> ML
                머신러닝 엔지니어 -> ML
                ML Manager -> ML
                Industrial Engineer -> DataAnalyst
                데이터 분석 -> DataAnalyst
                Data Manager -> DataAnalyst
                데이터 엔지니어 -> DataEngineering
                백엔드 개발자 -> Backend
                서버 개발 -> Backend
                빌링플랫폼 개발 -> Backend
                검색 엔지니어 -> Backend
                Software Engineer -> Backend
                Graphics 엔진 개발 -> Backend
                SDK 개발 -> Backend
                Platform Engineer -> Backend
                개발자 공개 채용 -> FullStack
                코어뱅킹(공통) 개발자 -> FullStack
                종합재무리스크 업무 개발자 -> FullStack
                사내정보시스템 개발 및 운영 -> FullStack
                사내 시스템 개발 -> FullStack
                웹 크롤링(스크래핑) 엔지니어 -> FullStack
                웹 풀스택 -> FullStack
                Financial System Develop -> FullStack
                관리회계 IT 담당자 -> FullStack
                HR System Develop -> FullStack
                보안 담당자 -> Security
                정보보호 담당자 -> Security
                정보보안 -> Security
                정보보호 관리체계 통합 운영 담당자 -> Security
                IT 서비스 안정성 및 위험관리 전문가 -> Security
                IT Audit Manager -> Security
                Privacy Manager -> Security
                보안 플랫폼 개발 -> SecurityEngineering
                보안 엔지니어 -> SecurityEngineering
                DevOps Engineer -> DevOps
                Kubernetes Engine -> DevOps
                SRE -> DevOps
                서비스 아키텍트 -> DevOps
                QA -> QA
                PM -> PM
                기획 담당자 -> PO
                기술/기획 담당자 -> PO
                리서치/서베이 운영 담당자 -> PO
                iOS -> iOS
                시스템 엔지니어 -> Infra
                네트워크 엔지니어 -> Infra
                Cloud Management 개발 -> Infra
                데이터베이스 서비스 개발 -> Infra
                네트워킹 서비스 개발 -> Infra
                컴퓨팅 서비스 개발 -> Infra
                Database 엔지니어 -> DBA
                기술지원 담당 -> TechnicalSupport
                Developer Relations Manage -> TechnicalSupport
                변호사 -> Legal&Compliance
                AML -> Legal&Compliance
                Compliance Manage -> Legal&Compliance
                Credit Strategy Manage -> Legal&Compliance
                광고 전략 오퍼레이션 -> Marketing
                Brand Marketer -> Marketing
                Strategy Manage -> Strategy
                Strategy Development Manager -> Strategy
                IR Manager -> Strategy
                Research Assistant -> Strategy
                Public Affairs Manage -> Communication
                Communications Manage -> Communication
                Global Communications Manage -> Communication
                성장 및 협력 담당자 -> People
                Payroll Manager -> People
                Barista -> People
                Culture Event Manager -> People
                HR Coordinator -> People
                Recruiting Business Partner -> People
                Recruiting Strategy Manage -> People
                Talent Sourcer -> People
                Employer Branding Manage -> People
                HRBP -> People
                Data Recruiting -> People
                노사 전략 및 ER 담당자 -> People
                교육 콘텐츠 기획 및 운영 -> People
                구매 계약 담당자 -> Finance
                Cloud Business Pathfinder -> Business
                사업개발 및 세일즈 -> Business
                ---
                제목 리스트:
                """);

        // 각 제목을 '- 제목' 형태로 프롬프트에 추가
        for (Job_mst title : jobTitles) {
            promptBuilder.append("- ").append(title.getAnnoSubject()).append("\n");
        }

        promptBuilder.append("\n응답:"); // 모델이 응답을 시작할 부분 명시

        return promptBuilder.toString();
    }

    /**
     * 주어진 단일 프롬프트로 Google Gemini API를 호출하여 텍스트 응답을 생성합니다.
     * (주로 내부적으로 여러 제목을 포함하는 프롬프트 처리에 사용됩니다)
     *
     * @param prompt AI 모델에게 전달할 단일 프롬프트 메시지 (여러 제목 포함 가능)
     * @return Gemini 모델이 생성한 텍스트 응답을 담는 Mono (비동기 결과)
     */
    // 이 메서드는 classifyJobTitles 내부에서만 사용되므로 private으로 변경 가능
    // public Mono<String> generateText(String prompt) { ... }
    private Mono<String> generateText(String prompt) { // private으로 변경 예시
        Object requestBody = buildGeminiRequestBody(prompt);

        return geminiWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        // Base URL은 WebClientConfig에 설정되어 있다고 가정
                        // 실제 Gemini API 엔드포인트 경로 (WebClientConfig의 baseUrl 뒤에 붙을 경로)
                        // 예: uriBuilder.path("/v1/models/gemini-pro:generateContent")
                        .queryParam("key", apiKey)
                        .build())
                .bodyValue(requestBody)
                .retrieve()
                // API 응답 본문을 String (JSON 문자열)으로 변환
                .bodyToMono(String.class)
                // 수신한 JSON 문자열을 파싱하여 모델이 생성한 텍스트를 추출
                .map(this::parseGeminiResponse);
    }

    /**
     * Google Gemini API 요청 본문 구조에 맞는 DTO 객체를 생성합니다.
     * 예: {"contents": [{"parts": [{"text": "프롬프트 내용"}]}]}
     * (단일 프롬프트 문자열을 입력으로 받습니다)
     *
     * @param prompt 요청 본문에 포함될 프롬프트 텍스트 (여러 제목을 포함하는 프롬프트)
     * @return 요청 본문에 사용될 DTO 객체
     */
    private Object buildGeminiRequestBody(String prompt) {
        GeminiPart promptPart = new GeminiPart(prompt);
        List<GeminiPart> parts = Collections.singletonList(promptPart);

        GeminiContent content = new GeminiContent(parts);
        List<GeminiContent> contents = Collections.singletonList(content);

        return new GeminiRequest(contents);
    }

    /**
     * Google Gemini API 응답 JSON 문자열을 파싱하여 모델이 생성한 텍스트를 추출합니다.
     * (JSON 응답 구조에서 모델 텍스트 부분만 추출합니다)
     *
     * @param responseJson Gemini API로부터 받은 JSON 응답 문자열
     * @return 추출된 모델 생성 텍스트 또는 파싱/추출 실패 시 오류 메시지
     */
    private String parseGeminiResponse(String responseJson) {
        try {
            // DTO는 com.nklcbdty.api.ai.dto 패키지에 정의되어 있다고 가정
            GeminiResponse response = objectMapper.readValue(responseJson, GeminiResponse.class);

            if (response != null && response.getCandidates() != null && !response.getCandidates().isEmpty()) {
                GeminiCandidate firstCandidate = response.getCandidates().get(0);
                if (firstCandidate != null && firstCandidate.getContent() != null && firstCandidate.getContent().getParts() != null && !firstCandidate.getContent().getParts().isEmpty()) {
                    GeminiPart firstPart = firstCandidate.getContent().getParts().get(0);
                    if (firstPart != null && firstPart.getText() != null) {
                        return firstPart.getText().strip(); // 양 끝 공백/줄바꿈 제거
                    }
                }
             }
             // 텍스트를 찾지 못했거나 응답 구조가 예상과 다른 경우
             System.err.println("Warning: Could not find text in Gemini Response. Response JSON: " + responseJson);
             return "Error: Could not parse text from Gemini Response (Text not found)";

         } catch (Exception e) {
             // JSON 파싱 중 오류 발생 시 예외 처리
             System.err.println("Failed to parse Gemini Response JSON: " + e.getMessage());
             e.printStackTrace();
             return "Error: JSON parsing failed: " + e.getMessage(); // 오류 메시지 포함
         }
    }

    /**
     * 모델이 생성한 텍스트 응답에서 각 채용 공고 제목과 해당하는 직무 분류를 파싱합니다.
     * 응답 형식은 '제목 -> 직무유형' 형태의 각 줄로 가정합니다.
     *
     * @param modelTextResponse 모델이 생성한 텍스트 응답 (여러 분류 결과 포함)
     * @return 각 제목과 예측된 직무 유형을 매핑한 Map
     */
    private Map<String, String> parseClassificationsFromText(String modelTextResponse) {
        Map<String, String> classifications = new HashMap<>();
        if (modelTextResponse == null || modelTextResponse.trim().isEmpty()) {
            return classifications; // 응답 텍스트가 없으면 빈 맵 반환
        }

        // 응답 텍스트를 줄 단위로 분리
        // \\r?는 캐리지 리턴(CR, \r)이 있거나 없을 수 있다는 의미이고, \\n는 라인 피드(LF, \n)입니다.
        // Windows/Linux/macOS의 다양한 줄바꿈 문자를 처리하기 위해 \\r?\\n를 사용합니다.
        String[] lines = modelTextResponse.split("\\r?\\n");

        for (String line : lines) {
            // 각 줄에서 ' -> ' 구분자를 기준으로 제목과 직무유형 분리
            String trimmedLine = line.strip(); // 줄의 앞뒤 공백 제거
            // 유효하지 않은 줄은 건너뜁니다 (빈 줄, 구분자 없는 줄 등)
            if (trimmedLine.isEmpty() || !trimmedLine.contains("->")) {
                continue;
            }

            // '->'를 기준으로 문자열을 분리하되, 제목 안에 '->'가 있을 수 있으므로 첫 번째 '->'만 기준으로 분리하도록 limit=2를 사용
            String[] parts = trimmedLine.split("->", 2);
            if (parts.length == 2) {
                String title = parts[0].strip(); // 제목 부분의 앞뒤 공백 제거
                String jobType = parts[1].strip(); // 직무유형 부분의 앞뒤 공백 제거
                // 제목과 직무유형이 모두 비어있지 않은 경우에만 맵에 추가
                if (!title.isEmpty() && !jobType.isEmpty()) {
                    classifications.put(title, jobType); // 맵에 저장
                }
            } else {
                 // '->' 구분자가 예상대로 나오지 않은 줄에 대한 처리 (로깅 등)
                 System.err.println("Warning: Could not parse classification from line: " + trimmedLine);
            }
        }

        return classifications;
    }
}
