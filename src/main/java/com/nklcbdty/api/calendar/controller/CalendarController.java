package com.nklcbdty.api.calendar.controller;

import java.time.YearMonth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.calendar.service.CalendarService;

/**
 * 채용 캘린더. 그 달에 마감되는 공고를 날짜별로 보여준다.
 *
 * <p>예: {@code GET /api/calendar/deadlines?year=2026&month=8&company=NAVER}</p>
 *
 * <p>year/month 를 생략하면 이번 달, company 를 생략하면 전체 회사다.
 * 상시채용("영입종료시")처럼 마감일이 없는 공고는 캘린더에 찍을 날짜가 없어 제외된다.</p>
 */
@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    // 잘못된 값으로 엉뚱한 달을 조회하는 것을 막는 상식적인 범위.
    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;

    private final CalendarService calendarService;

    @Autowired
    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    /**
     * 응답은 캐시된 JSON 문자열을 그대로 흘려보낸다.
     *
     * <p>charset 을 명시하는 이유는 목록 API({@code /api/list})와 같다 — String 반환 시
     * StringHttpMessageConverter 의 프레임워크 기본 charset 이 ISO-8859-1 이라 한글이 깨질 수 있다.</p>
     */
    @GetMapping(value = "/deadlines", produces = "application/json;charset=UTF-8")
    public String deadlines(
        @RequestParam(required = false) Integer year,
        @RequestParam(required = false) Integer month,
        @RequestParam(defaultValue = "ALL") String company) {

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

        return calendarService.getMonthlyDeadlinesAsJson(YearMonth.of(targetYear, targetMonth), company);
    }

    /** 잘못된 year/month 는 400. 이 컨트롤러 안에서만 처리해 다른 API 의 예외 처리에 영향을 주지 않는다. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleInvalidParameter(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                             .contentType(MediaType.valueOf("text/plain;charset=UTF-8"))
                             .body(e.getMessage());
    }
}
