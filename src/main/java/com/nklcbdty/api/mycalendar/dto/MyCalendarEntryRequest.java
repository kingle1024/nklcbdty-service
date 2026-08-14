package com.nklcbdty.api.mycalendar.dto;

import lombok.Data;

/**
 * 일정 등록·수정 요청.
 *
 * <p>필수는 {@code applyDate}(어느 칸인지)와 {@code companyName} 뿐이다.
 * URL·메모는 나중에 채워 넣을 수 있게 비워 둘 수 있다.</p>
 */
@Data
public class MyCalendarEntryRequest {

    /** yyyy-MM-dd */
    private String applyDate;

    private String companyName;

    private String url;

    private String memo;
}
