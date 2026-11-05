package com.nklcbdty.api.resumereview.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.nklcbdty.api.resumereview.dto.ResumeReviewResponse;
import com.nklcbdty.api.resumereview.service.ResumeReviewException;
import com.nklcbdty.api.resumereview.service.ResumeReviewService;

/**
 * 공고 대비 이력서·포트폴리오 첨삭.
 *
 * <p>{@code POST /api/resume-review} (multipart/form-data)</p>
 * <ul>
 *   <li>{@code files}: 이력서·포트폴리오 (PDF/TXT/MD, 최대 5개)</li>
 *   <li>{@code jobUrl}: 공고 주소 — 또는 —</li>
 *   <li>{@code jobText}: 공고 내용을 붙여넣은 글 (둘 다 오면 이쪽을 쓴다)</li>
 * </ul>
 *
 * <p>이 경로는 {@code AllowedPaths} 에 없어서 로그인 토큰이 있어야 들어온다. AI 호출 한 번이 비싸고
 * 서버가 대신 외부 주소를 열어 주는 기능이라 익명에게 열지 않는다.</p>
 *
 * <p>실패는 4xx + {@code {"message": ...}} 로 준다. 5xx 는 CloudType 게이트웨이가 본문을 자기 HTML 로
 * 바꾸고 CORS 헤더까지 지워서 사용자에게 사유가 닿지 않는다({@code JobMatchController} 참고).</p>
 */
@RestController
@RequestMapping("/api/resume-review")
public class ResumeReviewController {

    private final ResumeReviewService service;

    public ResumeReviewController(ResumeReviewService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResumeReviewResponse review(
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "jobUrl", required = false) String jobUrl,
            @RequestParam(value = "jobText", required = false) String jobText) {
        return service.review(jobUrl, jobText, files);
    }

    @ExceptionHandler(ResumeReviewException.class)
    public ResponseEntity<Map<String, String>> handle(ResumeReviewException e) {
        final HttpStatus status = e.getKind() == ResumeReviewException.Kind.BAD_INPUT
                ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(Map.of("message", e.getMessage()));
    }
}
