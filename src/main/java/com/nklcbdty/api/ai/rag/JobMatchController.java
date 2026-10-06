package com.nklcbdty.api.ai.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 이력서 PDF → 적합 공고 매칭 엔드포인트.
 *
 * <p>예: {@code POST /api/jobs/match} (multipart/form-data, file=이력서.pdf)</p>
 * <p>PDF 텍스트를 추출해 {@link SemanticSearchService} 의미 검색으로 가장 유사한 공고 top-K 를 반환한다.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/jobs")
public class JobMatchController {

    private static final int DEFAULT_K = 5;
    private static final int MAX_K = 20;

    private final SemanticSearchService search;
    private final ResumePdfExtractor pdfExtractor;
    private final EmbeddingService embedder;
    private final JobEmbeddingCache cache;

    public JobMatchController(SemanticSearchService search, ResumePdfExtractor pdfExtractor,
                              EmbeddingService embedder, JobEmbeddingCache cache) {
        this.search = search;
        this.pdfExtractor = pdfExtractor;
        this.embedder = embedder;
        this.cache = cache;
    }

    /**
     * 매칭이 왜 안 되는지 밖에서 가리기 위한 상태. 운영 로그도 CloudType 환경변수도 볼 수 없어서
     * 둔다. 비밀값은 담지 않는다 — 키는 "설정됐는지" 만, 실패는 상태 코드만.
     *
     * <ul>
     *   <li>{@code provider=false} → {@code RAG_EMBEDDING_PROVIDER=none}</li>
     *   <li>{@code available=false} → API 키(OPENAI_API_KEY) 비어 있음</li>
     *   <li>{@code lastFailure=HTTP 401/429...} → 키가 틀렸거나 쿼터·결제 문제</li>
     *   <li>{@code indexedJobs=0} 인데 실패도 없음 → 기동 직후 인덱싱 중</li>
     * </ul>
     */
    @GetMapping("/match/status")
    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("provider", embedder.hasProvider());
        out.put("available", embedder.isAvailable());
        out.put("modelVersion", embedder.modelVersion());
        out.put("indexedJobs", cache.size());
        out.put("lastFailure", embedder.lastFailure());
        return out;
    }

    @PostMapping(value = "/match", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> match(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "k", defaultValue = "" + DEFAULT_K) int k) {

        int topK = Math.max(1, Math.min(k, MAX_K));

        String resumeText;
        try {
            resumeText = pdfExtractor.extract(file);
        } catch (ResumePdfExtractor.ExtractionException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        List<SemanticSearchService.Result> results = search.search(resumeText, topK);
        if (results.isEmpty()) {
            // 503 으로 내리면 CloudType 게이트웨이가 본문을 자기 HTML 로 바꾸고 CORS 헤더까지 지워서
            // 브라우저엔 "네트워크 오류" 로만 보인다. 문구가 사용자에게 닿도록 4xx + JSON 본문으로 준다.
            // (ResponseStatusException 은 server.error.include-message=never 라 사유가 빠진다)
            log.warn("PDF 매칭 결과 없음 — available={} indexedJobs={} lastFailure={}",
                    embedder.isAvailable(), cache.size(), embedder.lastFailure());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", unavailableMessage()));
        }

        List<JobSearchController.JobSearchHit> out = new ArrayList<>(results.size());
        for (SemanticSearchService.Result r : results) {
            out.add(JobSearchController.JobSearchHit.from(r));
        }
        return ResponseEntity.ok(out);
    }

    private String unavailableMessage() {
        if (embedder.isAvailable() && embedder.lastFailure() == null) {
            return "공고 매칭을 준비하는 중입니다. 잠시 후 다시 시도해주세요.";
        }
        return "지금은 공고 매칭을 사용할 수 없습니다. 잠시 후 다시 시도해주세요.";
    }
}
