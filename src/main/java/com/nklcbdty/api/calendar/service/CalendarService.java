package com.nklcbdty.api.calendar.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nklcbdty.api.calendar.dto.CalendarDayDto;
import com.nklcbdty.api.calendar.dto.CalendarJobDto;
import com.nklcbdty.api.calendar.dto.CalendarMonthDto;
import com.nklcbdty.api.crawler.common.JobEndDates;
import com.nklcbdty.api.crawler.service.JobService;
import com.nklcbdty.common.vo.Job_mst;

/** 채용 캘린더. 공고를 마감일 기준으로 날짜별로 모아 준다. */
@Service
public class CalendarService {

    private final JobService jobService;

    @Autowired
    public CalendarService(JobService jobService) {
        this.jobService = jobService;
    }

    /**
     * 해당 월에 마감되는 공고를 날짜별로 모아 반환한다.
     *
     * @param company 회사 코드(예: NAVER). "ALL" 이면 전체 회사.
     */
    public CalendarMonthDto getMonthlyDeadlines(YearMonth yearMonth, String company) {
        final List<Job_mst> jobs =
            jobService.findClosingBetween(company, yearMonth.atDay(1), yearMonth.atEndOfMonth());

        final LocalDateTime now = LocalDateTime.now();

        // TreeMap: 날짜 오름차순. 하루 안에서는 findClosingBetween 이 준 마감 임박순을 유지한다.
        final Map<LocalDate, List<CalendarJobDto>> jobsByDate = new TreeMap<>();
        for (Job_mst job : jobs) {
            // findClosingBetween 이 마감일시 파싱 가능한 공고만 주므로 parse 결과는 null 이 아니다.
            final LocalDate endDay = JobEndDates.parse(job.getEndDate()).toLocalDate();
            jobsByDate.computeIfAbsent(endDay, key -> new ArrayList<>())
                      .add(new CalendarJobDto(job, now));
        }

        final List<CalendarDayDto> days = new ArrayList<>();
        for (Map.Entry<LocalDate, List<CalendarJobDto>> entry : jobsByDate.entrySet()) {
            days.add(new CalendarDayDto(entry.getKey(), entry.getValue()));
        }

        return new CalendarMonthDto(yearMonth, jobs.size(), days);
    }
}
