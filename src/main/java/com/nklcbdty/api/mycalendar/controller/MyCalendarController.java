package com.nklcbdty.api.mycalendar.controller;

import java.time.YearMonth;
import java.util.Collections;
import java.util.HashMap;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.mycalendar.dto.MyCalendarEntryDto;
import com.nklcbdty.api.mycalendar.dto.MyCalendarEntryRequest;
import com.nklcbdty.api.mycalendar.dto.MyCalendarMonthDto;
import com.nklcbdty.api.mycalendar.exception.MyCalendarNotFoundException;
import com.nklcbdty.api.mycalendar.service.MyCalendarService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 나의 채용 캘린더. 내가 지원할 회사를 달력에 적어 두는 개인 메모다.
 *
 * <ul>
 *   <li>GET    /api/my-calendar/entries?year=&month=  : 그 달에 적어 둔 일정</li>
 *   <li>POST   /api/my-calendar/entries               : 등록</li>
 *   <li>PUT    /api/my-calendar/entries/{entryId}     : 수정</li>
 *   <li>DELETE /api/my-calendar/entries/{entryId}     : 삭제</li>
 *   <li>GET    /api/my-calendar/company-name?url=     : URL 로 회사명 추측(입력 도우미)</li>
 * </ul>
 *
 * <p>이 경로는 {@code AllowedPaths} 에 <b>없다</b>. 그래서 {@code AuthFilter} 가 유효한 토큰을
 * 요구하고, 토큰이 없으면 컨트롤러까지 오지도 못한다(401). 사용자 구분은 필터가 넣어 둔
 * {@code userId} 속성으로 한다 — 공개 게시판처럼 헤더를 직접 파싱할 이유가 없다.</p>
 */
@RestController
@RequestMapping("/api/my-calendar")
public class MyCalendarController {

    // 잘못된 값으로 엉뚱한 달을 조회하는 것을 막는 상식적인 범위. 공개 캘린더와 같은 기준이다.
    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;

    private final MyCalendarService myCalendarService;

    public MyCalendarController(MyCalendarService myCalendarService) {
        this.myCalendarService = myCalendarService;
    }

    @GetMapping("/entries")
    public ResponseEntity<MyCalendarMonthDto> entries(
        @RequestParam(required = false) Integer year,
        @RequestParam(required = false) Integer month,
        HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(myCalendarService.getMonth(userId(httpRequest), targetMonth(year, month)));
    }

    @PostMapping("/entries")
    public ResponseEntity<MyCalendarEntryDto> create(
        @RequestBody MyCalendarEntryRequest request,
        HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(myCalendarService.create(userId(httpRequest), request));
    }

    @PutMapping("/entries/{entryId}")
    public ResponseEntity<MyCalendarEntryDto> update(
        @PathVariable Long entryId,
        @RequestBody MyCalendarEntryRequest request,
        HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(myCalendarService.update(userId(httpRequest), entryId, request));
    }

    @DeleteMapping("/entries/{entryId}")
    public ResponseEntity<Map<String, Object>> delete(
        @PathVariable Long entryId,
        HttpServletRequest httpRequest
    ) {
        myCalendarService.delete(userId(httpRequest), entryId);
        return ResponseEntity.ok(Map.of("status", "deleted", "entryId", entryId));
    }

    /**
     * URL 에서 회사명을 추측한다. 추측 실패는 오류가 아니므로 200 에 {@code companyName: null} 을 준다.
     * (값이 {@code null} 일 수 있어 {@code Map.of} 대신 {@code HashMap} 을 쓴다)
     */
    @GetMapping("/company-name")
    public ResponseEntity<Map<String, String>> companyName(@RequestParam(required = false) String url) {
        final Map<String, String> body = new HashMap<>();
        body.put("companyName", myCalendarService.guessCompanyName(url));
        return ResponseEntity.ok(body);
    }

    private YearMonth targetMonth(Integer year, Integer month) {
        final YearMonth thisMonth = YearMonth.now();
        final int targetYear = year != null ? year : thisMonth.getYear();
        final int targetMonth = month != null ? month : thisMonth.getMonthValue();

        if (targetMonth < 1 || targetMonth > 12) {
            throw new IllegalArgumentException("month 는 1~12 여야 합니다. (요청값: " + targetMonth + ")");
        }
        if (targetYear < MIN_YEAR || targetYear > MAX_YEAR) {
            throw new IllegalArgumentException(
                "year 는 " + MIN_YEAR + "~" + MAX_YEAR + " 여야 합니다. (요청값: " + targetYear + ")");
        }
        return YearMonth.of(targetYear, targetMonth);
    }

    /**
     * AuthFilter 가 넣어 둔 로그인 사용자. 필터를 통과했으면 반드시 있지만,
     * 누군가 이 경로를 공개 목록에 올리는 순간 남의 일정이 섞여 나가므로 그때는 401 로 막는다.
     */
    private String userId(HttpServletRequest request) {
        final Object userId = request.getAttribute("userId");
        if (userId == null || userId.toString().isBlank()) {
            throw new MyCalendarUnauthorizedException();
        }
        return userId.toString();
    }

    @ExceptionHandler(MyCalendarUnauthorizedException.class)
    public ResponseEntity<Map<String, String>> handleUnauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                             .body(Collections.singletonMap("message", "로그인이 필요합니다."));
    }

    @ExceptionHandler(MyCalendarNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(MyCalendarNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                             .body(Collections.singletonMap("message", e.getMessage()));
    }

    /** 잘못된 입력은 400. 이 컨트롤러 안에서만 처리해 다른 API 의 예외 처리에 영향을 주지 않는다. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidInput(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                             .body(Collections.singletonMap("message", e.getMessage()));
    }

    /** 필터를 지나왔는데도 사용자를 알 수 없는 경우. 이 컨트롤러 밖에서 쓸 일이 없어 안에 둔다. */
    static class MyCalendarUnauthorizedException extends RuntimeException {
    }
}
