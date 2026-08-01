package com.nklcbdty.api.calendar.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.Getter;

/** 마감 공고가 하나 이상 있는 하루. 공고가 없는 날은 응답에 담지 않는다(빈 칸은 프론트가 그린다). */
@Getter
public class CalendarDayDto {

    /** yyyy-MM-dd */
    private final String date;
    /** 1(월) ~ 7(일). 요일 색 처리에 쓴다. */
    private final int dayOfWeek;
    private final int count;
    private final List<CalendarJobDto> jobs;

    public CalendarDayDto(LocalDate date, List<CalendarJobDto> jobs) {
        this.date = date.toString();
        this.dayOfWeek = date.getDayOfWeek().getValue();
        this.count = jobs.size();
        this.jobs = jobs;
    }
}
