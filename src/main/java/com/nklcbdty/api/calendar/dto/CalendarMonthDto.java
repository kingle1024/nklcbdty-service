package com.nklcbdty.api.calendar.dto;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import lombok.Getter;

/**
 * 한 달치 마감 캘린더 응답.
 *
 * <p>{@code days} 는 마감 공고가 있는 날만 날짜 오름차순으로 담긴다. 달의 칸 배치는
 * {@code firstDayOfWeek}/{@code lengthOfMonth} 로 프론트가 계산한다.</p>
 */
@Getter
public class CalendarMonthDto {

    private final int year;
    private final int month;
    /** 1일의 요일 (1=월 ~ 7=일) */
    private final int firstDayOfWeek;
    /** 그 달의 일수 */
    private final int lengthOfMonth;
    /** 이 달에 마감되는 공고 총 건수 */
    private final int totalCount;
    private final List<CalendarDayDto> days;

    public CalendarMonthDto(YearMonth yearMonth, int totalCount, List<CalendarDayDto> days) {
        this.year = yearMonth.getYear();
        this.month = yearMonth.getMonthValue();
        final LocalDate firstDay = yearMonth.atDay(1);
        this.firstDayOfWeek = firstDay.getDayOfWeek().getValue();
        this.lengthOfMonth = yearMonth.lengthOfMonth();
        this.totalCount = totalCount;
        this.days = days;
    }
}
