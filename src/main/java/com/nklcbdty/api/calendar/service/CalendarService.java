package com.nklcbdty.api.calendar.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nklcbdty.api.calendar.dto.CalendarDayDto;
import com.nklcbdty.api.calendar.dto.CalendarJobDto;
import com.nklcbdty.api.calendar.dto.CalendarMonthDto;
import com.nklcbdty.api.common.CacheConfig;
import com.nklcbdty.api.crawler.common.JobEndDates;
import com.nklcbdty.api.crawler.service.JobService;
import com.nklcbdty.common.vo.Job_mst;

/** 채용 캘린더. 공고를 마감일 기준으로 날짜별로 모아 준다. */
@Service
public class CalendarService {

    private final JobService jobService;
    private final ObjectMapper objectMapper;

    @Autowired
    public CalendarService(JobService jobService, ObjectMapper objectMapper) {
        this.jobService = jobService;
        this.objectMapper = objectMapper;
    }

    /**
     * {@code /api/calendar/deadlines} 응답 JSON. 캐시 히트 시 Redis GET 한 번으로 끝난다.
     *
     * <p>목록({@code /api/list})과 같은 이유로 캐싱한다 — 전건 조회 + 중복제거 + 문자열 날짜 파싱을
     * 달을 넘길 때마다 다시 하기 때문이다. 캐싱해도 안전한 이유는 {@link CacheConfig#JOB_CALENDAR}
     * 주석 참고. 무효화는 목록 캐시와 같은 지점(크롤 저장 · 공고 삭제)에서 함께 일어난다.</p>
     */
    @Cacheable(cacheNames = CacheConfig.JOB_CALENDAR, key = "#company + ':' + #yearMonth")
    public String getMonthlyDeadlinesAsJson(YearMonth yearMonth, String company) {
        try {
            return objectMapper.writeValueAsString(getMonthlyDeadlines(yearMonth, company));
        } catch (JsonProcessingException e) {
            // 직렬화 실패는 설정/모델 문제이므로 조용히 넘기지 않는다.
            throw new IllegalStateException(
                "캘린더 JSON 직렬화 실패 company=" + company + ", yearMonth=" + yearMonth, e);
        }
    }

    /**
     * 해당 월에 마감되는 공고를 날짜별로 모아 반환한다.
     *
     * @param company 회사 코드(예: NAVER). "ALL" 이면 전체 회사.
     */
    public CalendarMonthDto getMonthlyDeadlines(YearMonth yearMonth, String company) {
        final List<Job_mst> jobs =
            jobService.findClosingBetween(company, yearMonth.atDay(1), yearMonth.atEndOfMonth());

        // TreeMap: 날짜 오름차순. 하루 안에서는 findClosingBetween 이 준 마감 임박순을 유지한다.
        final Map<LocalDate, List<CalendarJobDto>> jobsByDate = new TreeMap<>();
        for (Job_mst job : jobs) {
            // findClosingBetween 이 마감일시 파싱 가능한 공고만 주므로 parse 결과는 null 이 아니다.
            final LocalDate endDay = JobEndDates.parse(job.getEndDate()).toLocalDate();
            jobsByDate.computeIfAbsent(endDay, key -> new ArrayList<>())
                      .add(new CalendarJobDto(job));
        }

        final List<CalendarDayDto> days = new ArrayList<>();
        for (Map.Entry<LocalDate, List<CalendarJobDto>> entry : jobsByDate.entrySet()) {
            days.add(new CalendarDayDto(entry.getKey(), entry.getValue()));
        }

        return new CalendarMonthDto(yearMonth, jobs.size(), days);
    }
}
