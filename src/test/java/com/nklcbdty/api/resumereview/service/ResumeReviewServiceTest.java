package com.nklcbdty.api.resumereview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.nklcbdty.api.resumereview.dto.ResumeReviewResponse;
import com.nklcbdty.api.resumereview.dto.ResumeReviewResponse.FitStatus;

class ResumeReviewServiceTest {

    private JobPostingFetcher fetcher;
    private GeminiJsonClient gemini;
    private ResumeReviewService service;

    private final MockMultipartFile resume = new MockMultipartFile(
            "files", "이력서.txt", "text/plain",
            "경력\n주문 API 개발\n기술: Java".getBytes(StandardCharsets.UTF_8));

    @BeforeEach
    void setUp() {
        fetcher = mock(JobPostingFetcher.class);
        gemini = mock(GeminiJsonClient.class);
        service = new ResumeReviewService(fetcher, new ReviewDocumentExtractor(), gemini);
    }

    @Test
    @DisplayName("AI 가 준 변경을 원문에 적용하고, 못 찾은 변경은 applied=false 로 남긴다")
    void appliesChanges() {
        when(gemini.generate(anyString())).thenReturn(new GeminiJsonClient.Answer("m", """
                ```json
                {"jobTitle":"백엔드","company":"SK","requirements":["Spring Boot"],
                 "fit":[{"requirement":"Spring Boot","status":"partial","evidence":"Java 만 있음"},
                        {"requirement":"Kafka","status":"???","evidence":""}],
                 "documents":[{"index":0,"summary":"Spring 강조","changes":[
                   {"type":"REPLACE","section":"경력","before":"주문 API 개발","after":"Spring Boot 주문 API 개발","reason":"요구사항"},
                   {"type":"insert","section":"기술","before":"기술: Java","after":"- [직접 입력: 사용 기간]","reason":"구체화"},
                   {"type":"REPLACE","section":"","before":"없는 문장","after":"x","reason":""}]}],
                 "suggestions":["Kafka 경험을 보강하세요"]}
                ```"""));

        ResumeReviewResponse res = service.review(null, "Spring Boot 백엔드 개발자 모집", List.of(resume));

        assertThat(res.company()).isEqualTo("SK");
        assertThat(res.fit()).extracting(ResumeReviewResponse.Fit::status)
                             .containsExactly(FitStatus.PARTIAL, FitStatus.PARTIAL);
        ResumeReviewResponse.Document doc = res.documents().get(0);
        assertThat(doc.fileName()).isEqualTo("이력서.txt");
        assertThat(doc.originalText()).isEqualTo("경력\n주문 API 개발\n기술: Java");
        assertThat(doc.revisedText()).isEqualTo("경력\nSpring Boot 주문 API 개발\n기술: Java\n- [직접 입력: 사용 기간]");
        assertThat(doc.changes()).extracting(ResumeReviewResponse.Change::applied).containsExactly(true, true, false);
        assertThat(res.suggestions()).containsExactly("Kafka 경험을 보강하세요");
        assertThat(res.model()).isEqualTo("m");
    }

    @Test
    @DisplayName("붙여넣은 공고가 있으면 주소를 열지 않는다")
    void prefersPastedText() {
        when(gemini.generate(anyString())).thenReturn(new GeminiJsonClient.Answer("m", "{}"));

        service.review("https://www.wanted.co.kr/wd/1", "공고 본문", List.of(resume));

        verify(fetcher, never()).fetch(anyString());
    }

    @Test
    @DisplayName("공고가 없거나 파일이 없으면 AI 를 부르지 않고 거절한다")
    void rejectsMissingInput() {
        assertThatThrownBy(() -> service.review(null, " ", List.of(resume)))
                .hasMessageContaining("공고 주소나 공고 내용");
        assertThatThrownBy(() -> service.review(null, "공고", List.of()))
                .hasMessageContaining("파일을 하나 이상");
        verify(gemini, never()).generate(anyString());
    }

    @Test
    @DisplayName("지원하지 않는 파일 형식은 거절한다")
    void rejectsDocx() {
        MockMultipartFile docx = new MockMultipartFile("files", "이력서.docx", "application/octet-stream", new byte[] {1});
        assertThatThrownBy(() -> service.review(null, "공고", List.of(docx)))
                .hasMessageContaining("PDF 로 저장");
    }

    @Test
    @DisplayName("AI 답이 JSON 이 아니면 다시 시도하라고 알린다")
    void brokenJson() {
        when(gemini.generate(anyString())).thenReturn(new GeminiJsonClient.Answer("m", "{\"jobTitle\": "));
        assertThatThrownBy(() -> service.review(null, "공고", List.of(resume)))
                .isInstanceOf(ResumeReviewException.class)
                .extracting(e -> ((ResumeReviewException) e).getKind())
                .isEqualTo(ResumeReviewException.Kind.UNAVAILABLE);
    }

    @Test
    @DisplayName("프롬프트에 공고와 문서가 파일 이름과 함께 들어간다")
    void prompt() {
        String prompt = ResumeReviewService.buildPrompt("공고 본문 %d",
                List.of(new ReviewDocumentExtractor.ReviewDocument("a.pdf", "본문", false)));
        assertThat(prompt).contains("공고 본문 %d", "<<<문서 0: a.pdf>>>", "개선율 %]", "최대 15개");
    }
}
