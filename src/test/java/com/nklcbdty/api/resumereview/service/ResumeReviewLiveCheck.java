package com.nklcbdty.api.resumereview.service;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.web.MockMultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.nklcbdty.api.resumereview.dto.ResumeReviewResponse;

/**
 * 실제 공고 페이지와 실제 Gemini 로 첨삭을 끝까지 돌려 본다. 단위 테스트가 아니다.
 *
 * <p>{@code ./gradlew test --tests "*ResumeReviewLiveCheck*" (환경변수 LIVE=true, JOB_URL, MODELS)}
 * — 키는 gitignore 된 application-dev.properties 에서 읽고, 결과는 build/resume-review-live.json 에 남긴다.</p>
 */
@EnabledIfEnvironmentVariable(named = "LIVE", matches = "true")
class ResumeReviewLiveCheck {

    private static final String RESUME = """
            홍길동 | 백엔드 개발자 | 5년차
            경력
            ○○커머스 (2021.03 ~ 현재) 백엔드 개발
            - 주문/결제 API 개발 및 운영
            - 사내 어드민 시스템 개발
            - 레거시 배치를 스프링 배치로 옮김
            △△소프트 (2019.01 ~ 2021.02) 웹 개발
            - JSP 기반 그룹웨어 유지보수
            - Oracle 쿼리 튜닝
            기술
            Java, Spring Boot, JPA, MySQL, Oracle, AWS EC2, Jenkins
            """;

    @Test
    void wantedPosting() throws Exception {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream("src/main/resources/application-dev.properties")) {
            props.load(in);
        }
        String key = props.getProperty("google.gemini.api-key");
        String url = envOr("JOB_URL", "http://wntd.co/57094502");

        JobPostingFetcher fetcher = new JobPostingFetcher();
        String posting = fetcher.fetch(url);

        ResumeReviewService service = new ResumeReviewService(fetcher, new ReviewDocumentExtractor(),
                new GeminiJsonClient(key, "https://generativelanguage.googleapis.com/v1beta/models/",
                                     envOr("MODELS", "gemini-flash-latest,gemini-flash-lite-latest")));
        long start = System.currentTimeMillis();
        ResumeReviewResponse res = service.review(url, null, List.of(new MockMultipartFile(
                "files", "이력서.txt", "text/plain", RESUME.getBytes(StandardCharsets.UTF_8))));
        long took = System.currentTimeMillis() - start;

        String json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(res);
        Path out = Path.of("build", "resume-review-live.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, "// took " + took + "ms, posting " + posting.length() + " chars\n// "
                               + posting.substring(0, Math.min(1500, posting.length())).replace("\n", "\n// ")
                               + "\n" + json, StandardCharsets.UTF_8);
    }

    private static String envOr(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v;
    }
}
