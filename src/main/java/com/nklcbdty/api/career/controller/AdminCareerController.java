package com.nklcbdty.api.career.controller;

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
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.career.dto.CareerEntryDto;
import com.nklcbdty.api.career.dto.CareerEntryRequest;
import com.nklcbdty.api.career.service.CareerEntryService;

/**
 * 내 경력 기록 API.
 *
 * <ul>
 *   <li>GET    /api/admin/career      — 전부, 최근 것부터</li>
 *   <li>POST   /api/admin/career      — 추가</li>
 *   <li>PUT    /api/admin/career/{id} — 수정(slug 는 그대로)</li>
 *   <li>DELETE /api/admin/career/{id} — 삭제</li>
 * </ul>
 *
 * <p>개인 이력이라 트러블슈팅 기록과 같은 이유로 관리자 경로에 둔다.
 * {@code /api/admin/**} 은 {@code AuthFilter} 가 role=ADMIN 토큰을 요구한다.
 * 잘못된 입력(400)과 없는 항목(404)은 {@link CareerExceptionHandler} 가 메시지로 바꾼다.
 */
@RestController
@RequestMapping("/api/admin/career")
public class AdminCareerController {

    private final CareerEntryService careerEntryService;

    public AdminCareerController(CareerEntryService careerEntryService) {
        this.careerEntryService = careerEntryService;
    }

    @GetMapping
    public ResponseEntity<?> list() {
        List<CareerEntryDto> entries = careerEntryService.findAll();
        return ResponseEntity.ok(Map.of("entries", entries, "count", entries.size()));
    }

    @PostMapping
    public ResponseEntity<CareerEntryDto> create(@RequestBody CareerEntryRequest request) {
        return ResponseEntity.ok(careerEntryService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CareerEntryDto> update(@PathVariable Long id, @RequestBody CareerEntryRequest request) {
        return ResponseEntity.ok(careerEntryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        careerEntryService.delete(id);
        return ResponseEntity.ok(Map.of("status", "deleted", "id", id));
    }
}
