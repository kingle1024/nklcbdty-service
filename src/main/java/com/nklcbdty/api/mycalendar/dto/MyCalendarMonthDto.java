package com.nklcbdty.api.mycalendar.dto;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import lombok.Getter;

/**
 * 한 달치 내 채용 캘린더 응답.
 *
 * <p>달의 칸 배치를 {@code firstDayOfWeek}/{@code lengthOfMonth} 로 프론트가 계산하는 것까지
 * 공개 채용 캘린더({@code CalendarMonthDto})와 같다.</p>
 */
@Getter
public class MyCalendarMonthDto {

    private final int year;
    private final int month;
    /** 1일의 요일 (1=월 ~ 7=일) */
    private final int firstDayOfWeek;
    /** 그 달의 일수 */
    private final int lengthOfMonth;
    /** 이 달에 적어 둔 일정 총 건수 */
    private final int totalCount;
    private final List<MyCalendarDayDto> days;

    public MyCalendarMonthDto(YearMonth yearMonth, int totalCount, List<MyCalendarDayDto> days) {
        this.year = yearMonth.getYear();
        this.month = yearMonth.getMonthValue();
        final LocalDate firstDay = yearMonth.atDay(1);
        this.firstDayOfWeek = firstDay.getDayOfWeek().getValue();
        this.lengthOfMonth = yearMonth.lengthOfMonth();
        this.totalCount = totalCount;
        this.days = days;
    }
}
