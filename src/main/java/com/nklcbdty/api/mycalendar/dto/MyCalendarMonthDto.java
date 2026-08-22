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
 *
 * <p>{@code ongoingEntries} 는 마감일이 없는 상시채용이라 <b>어느 달을 조회해도 같다</b>.
 * 달마다 다시 내려주는 게 중복 같아 보이지만, 달을 넘길 때 상시채용 목록이 사라지지 않아야 하고
 * 프론트가 요청을 하나만 보내면 되므로 이쪽이 낫다.</p>
 */
@Getter
public class MyCalendarMonthDto {

    private final int year;
    private final int month;
    /** 1일의 요일 (1=월 ~ 7=일) */
    private final int firstDayOfWeek;
    /** 그 달의 일수 */
    private final int lengthOfMonth;
    /** 이 달에 적어 둔 일정 총 건수. 상시채용은 특정 달의 것이 아니라 여기에 넣지 않는다. */
    private final int totalCount;
    private final List<MyCalendarDayDto> days;
    /** 날짜 없이 적어 둔 상시채용. 아직 완료하지 않은 것이 먼저 온다. */
    private final List<MyCalendarEntryDto> ongoingEntries;
    private final int ongoingCount;

    public MyCalendarMonthDto(YearMonth yearMonth, int totalCount, List<MyCalendarDayDto> days,
                              List<MyCalendarEntryDto> ongoingEntries) {
        this.year = yearMonth.getYear();
        this.month = yearMonth.getMonthValue();
        final LocalDate firstDay = yearMonth.atDay(1);
        this.firstDayOfWeek = firstDay.getDayOfWeek().getValue();
        this.lengthOfMonth = yearMonth.lengthOfMonth();
        this.totalCount = totalCount;
        this.days = days;
        this.ongoingEntries = ongoingEntries;
        this.ongoingCount = ongoingEntries.size();
    }
}
