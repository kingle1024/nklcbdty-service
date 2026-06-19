package com.nklcbdty.api.ai.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

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

    public JobMatchController(SemanticSearchService search, ResumePdfExtractor pdfExtractor) {
        this.search = search;
        this.pdfExtractor = pdfExtractor;
    }

    @PostMapping(value = "/match", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<JobSearchController.JobSearchHit> match(
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
            // 모델 미로드 또는 인덱싱된 공고 없음 → 빈 결과로 갈리지 않도록 503 으로 구분
            log.warn("PDF 매칭 결과 없음 — 임베딩 모델 미로드이거나 인덱싱된 공고가 없습니다.");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "현재 공고 매칭을 사용할 수 없습니다. (임베딩 준비 중이거나 매칭 가능한 공고가 없습니다)");
        }

        List<JobSearchController.JobSearchHit> out = new ArrayList<>(results.size());
        for (SemanticSearchService.Result r : results) {
            out.add(JobSearchController.JobSearchHit.from(r));
        }
        return out;
    }
}
