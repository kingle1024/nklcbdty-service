package com.nklcbdty.api.mycalendar.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.Getter;

/**
 * 내 일정이 하나 이상 있는 하루. 비어 있는 날은 응답에 담지 않는다(빈 칸은 프론트가 그린다).
 * 공개 채용 캘린더의 {@code CalendarDayDto} 와 같은 모양이라 프론트가 같은 방식으로 그린다.
 */
@Getter
public class MyCalendarDayDto {

    /** yyyy-MM-dd */
    private final String date;
    /** 1(월) ~ 7(일). 요일 색 처리에 쓴다. */
    private final int dayOfWeek;
    private final int count;
    private final List<MyCalendarEntryDto> entries;

    public MyCalendarDayDto(LocalDate date, List<MyCalendarEntryDto> entries) {
        this.date = date.toString();
        this.dayOfWeek = date.getDayOfWeek().getValue();
        this.count = entries.size();
        this.entries = entries;
    }
}
