package com.nklcbdty.api.calendar.controller;

import java.time.YearMonth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/deadlines")
    public ResponseEntity<?> deadlines(
        @RequestParam(required = false) Integer year,
        @RequestParam(required = false) Integer month,
        @RequestParam(defaultValue = "ALL") String company) {

        final YearMonth thisMonth = YearMonth.now();
        final int targetYear = year != null ? year : thisMonth.getYear();
        final int targetMonth = month != null ? month : thisMonth.getMonthValue();

        if (targetMonth < 1 || targetMonth > 12) {
            return ResponseEntity.badRequest().body("month 는 1~12 여야 합니다. (요청값: " + targetMonth + ")");
        }
        if (targetYear < MIN_YEAR || targetYear > MAX_YEAR) {
            return ResponseEntity.badRequest()
                                 .body("year 는 " + MIN_YEAR + "~" + MAX_YEAR + " 여야 합니다. (요청값: " + targetYear + ")");
        }

        return ResponseEntity.ok(
            calendarService.getMonthlyDeadlines(YearMonth.of(targetYear, targetMonth), company));
    }
}
