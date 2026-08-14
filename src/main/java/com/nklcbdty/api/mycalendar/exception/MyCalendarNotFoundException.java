package com.nklcbdty.api.mycalendar.exception;

/**
 * 그 일정이 없거나 내 것이 아닐 때. 두 경우를 구분하지 않는다 —
 * 구분해서 알려주면 남의 일정 id 가 존재하는지 떠볼 수 있다.
 */
public class MyCalendarNotFoundException extends RuntimeException {

    public MyCalendarNotFoundException() {
        super("일정을 찾을 수 없습니다.");
    }
}
