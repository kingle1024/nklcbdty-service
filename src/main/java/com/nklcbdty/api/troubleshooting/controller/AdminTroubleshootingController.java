package com.nklcbdty.api.troubleshooting.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.troubleshooting.dto.TroubleshootingNoteDetailDto;
import com.nklcbdty.api.troubleshooting.dto.TroubleshootingNoteRequest;
import com.nklcbdty.api.troubleshooting.dto.TroubleshootingPageResponse;
import com.nklcbdty.api.troubleshooting.service.TroubleshootingNoteService;

/**
 * 트러블슈팅 기록 API.
 *
 * <ul>
 *   <li>GET /api/admin/troubleshooting?page=&size=&keyword=&project=&severity=&tag=</li>
 *   <li>GET /api/admin/troubleshooting/export?keyword=&project=&severity=&tag= — 본문까지 전부</li>
 *   <li>GET /api/admin/troubleshooting/{slug}</li>
 *   <li>POST /api/admin/troubleshooting — 추가(slug 를 비우면 서버가 만든다)</li>
 *   <li>PUT /api/admin/troubleshooting/{slug} — 수정(slug 는 그대로)</li>
 *   <li>DELETE /api/admin/troubleshooting/{slug} — 삭제</li>
 * </ul>
 *
 * <p>쓰기의 잘못된 입력(400)과 없는 기록(404)은 {@link TroubleshootingExceptionHandler} 가 메시지로 바꾼다.
 *
 * <p><b>왜 관리자 경로인가.</b> 기록에는 사내·고객사 시스템 이름과 장애 내용, 로그 원문이
 * 그대로 들어 있다. 포트폴리오로 쓸 때는 사람이 골라서 옮기는 것이고, 화면은 본인이
 * 들여다보는 용도라 공개하지 않는다. {@code /api/admin/**} 은 {@code AllowedPaths} 에
 * 없으므로 {@code AuthFilter} 가 role=ADMIN 토큰을 요구한다 — 여기에 따로 검사를 두지 않는
 * 이유가 그것이다. 공개로 바꾸려면 경로를 옮기고 AllowedPaths 에 등록해야 한다.
 */
@RestController
@RequestMapping("/api/admin/troubleshooting")
public class AdminTroubleshootingController {

    private final TroubleshootingNoteService troubleshootingNoteService;

    public AdminTroubleshootingController(TroubleshootingNoteService troubleshootingNoteService) {
        this.troubleshootingNoteService = troubleshootingNoteService;
    }

    @GetMapping
    public ResponseEntity<TroubleshootingPageResponse> list(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String project,
        @RequestParam(required = false) String severity,
        @RequestParam(required = false) String tag,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
            troubleshootingNoteService.list(keyword, project, severity, tag, page, size));
    }

    /**
     * 조건에 맞는 기록을 본문까지 전부. 목록 화면의 '전체 복사' 가 쓴다.
     *
     * <p>경로가 {@code /{slug}} 와 겹쳐 보이지만, 스프링은 경로 변수보다 <b>고정 문자열
     * 패턴을 먼저</b> 고른다. 그래도 사람이 읽고 헷갈릴 수 있는 자리라 테스트로 못박아 뒀다
     * ({@code AdminTroubleshootingRouteTest}).
     */
    @GetMapping("/export")
    public ResponseEntity<?> export(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String project,
        @RequestParam(required = false) String severity,
        @RequestParam(required = false) String tag
    ) {
        List<TroubleshootingNoteDetailDto> notes =
            troubleshootingNoteService.export(keyword, project, severity, tag);
        return ResponseEntity.ok(Map.of("notes", notes, "count", notes.size()));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<?> detail(@PathVariable String slug) {
        TroubleshootingNoteDetailDto note = troubleshootingNoteService.findBySlug(slug);
        if (note == null) {
            return ResponseEntity.status(404).body(Map.of("message", "해당 기록을 찾을 수 없습니다."));
        }
        return ResponseEntity.ok(note);
    }

    @PostMapping
    public ResponseEntity<TroubleshootingNoteDetailDto> create(@RequestBody TroubleshootingNoteRequest request) {
        return ResponseEntity.ok(troubleshootingNoteService.create(request));
    }

    @PutMapping("/{slug}")
    public ResponseEntity<TroubleshootingNoteDetailDto> update(
        @PathVariable String slug,
        @RequestBody TroubleshootingNoteRequest request
    ) {
        return ResponseEntity.ok(troubleshootingNoteService.update(slug, request));
    }

    @DeleteMapping("/{slug}")
    public ResponseEntity<?> delete(@PathVariable String slug) {
        troubleshootingNoteService.delete(slug);
        return ResponseEntity.ok(Map.of("status", "deleted", "slug", slug));
    }
}
