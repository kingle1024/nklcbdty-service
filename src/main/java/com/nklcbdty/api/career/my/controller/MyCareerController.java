package com.nklcbdty.api.career.my.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.career.dto.CareerEntryDto;
import com.nklcbdty.api.career.dto.CareerEntryRequest;
import com.nklcbdty.api.career.my.service.MyCareerService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 마이페이지 "내 경력". 로그인한 사용자가 자기 경력을 적어 둔다.
 *
 * <ul>
 *   <li>GET    /api/my-career      — 내 경력 전부, 최근 것부터</li>
 *   <li>POST   /api/my-career      — 추가</li>
 *   <li>PUT    /api/my-career/{id} — 수정</li>
 *   <li>DELETE /api/my-career/{id} — 삭제</li>
 * </ul>
 *
 * <p>요청·응답 모양은 관리자 경력({@code /api/admin/career})과 같다(slug 만 없다).
 *
 * <p>이 경로는 {@code AllowedPaths} 에 <b>없다</b> — {@code AuthFilter} 가 유효한 토큰을 요구하고,
 * 사용자는 필터가 넣어 둔 {@code userId} 속성으로 구분한다. 400·404 는 career 패키지의
 * {@code CareerExceptionHandler} 가 같은 모양으로 바꾼다.
 */
@RestController
@RequestMapping("/api/my-career")
public class MyCareerController {

    private final MyCareerService myCareerService;

    public MyCareerController(MyCareerService myCareerService) {
        this.myCareerService = myCareerService;
    }

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest httpRequest) {
        List<CareerEntryDto> entries = myCareerService.findAll(userId(httpRequest));
        return ResponseEntity.ok(Map.of("entries", entries, "count", entries.size()));
    }

    @PostMapping
    public ResponseEntity<CareerEntryDto> create(@RequestBody CareerEntryRequest request,
                                                 HttpServletRequest httpRequest) {
        return ResponseEntity.ok(myCareerService.create(userId(httpRequest), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CareerEntryDto> update(@PathVariable Long id,
                                                 @RequestBody CareerEntryRequest request,
                                                 HttpServletRequest httpRequest) {
        return ResponseEntity.ok(myCareerService.update(userId(httpRequest), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        myCareerService.delete(userId(httpRequest), id);
        return ResponseEntity.ok(Map.of("status", "deleted", "id", id));
    }

    /**
     * AuthFilter 가 넣어 둔 로그인 사용자. 누군가 이 경로를 공개 목록에 올리면
     * 사용자 구분 없이 열리므로, 그때도 남의 경력이 섞이지 않게 401 로 막는다.
     */
    private String userId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null || userId.toString().isBlank()) {
            throw new UnauthorizedException();
        }
        return userId.toString();
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, String>> handleUnauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("status", "error", "message", "로그인이 필요합니다."));
    }

    static class UnauthorizedException extends RuntimeException {
    }
}
