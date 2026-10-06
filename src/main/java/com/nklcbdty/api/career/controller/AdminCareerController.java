package com.nklcbdty.api.career.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.career.dto.CareerEntryDto;
import com.nklcbdty.api.career.service.CareerEntryService;

/**
 * 내 경력 기록 열람 API. 읽기 전용이다.
 *
 * <ul>
 *   <li>GET /api/admin/career — 전부, 최근 것부터</li>
 * </ul>
 *
 * <p>개인 이력이라 트러블슈팅 기록과 같은 이유로 관리자 경로에 둔다.
 * {@code /api/admin/**} 은 {@code AuthFilter} 가 role=ADMIN 토큰을 요구한다.
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
}
