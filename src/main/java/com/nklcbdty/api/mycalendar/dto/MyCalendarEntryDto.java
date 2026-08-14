package com.nklcbdty.api.mycalendar.dto;

import com.nklcbdty.api.mycalendar.vo.MyCalendarEntry;

import lombok.Getter;

/** 달력 한 칸에 들어가는 내 일정 1건. */
@Getter
public class MyCalendarEntryDto {

    private final Long id;
    /** yyyy-MM-dd */
    private final String applyDate;
    private final String companyName;
    private final String url;
    private final String memo;

    public MyCalendarEntryDto(MyCalendarEntry entry) {
        this.id = entry.getId();
        this.applyDate = entry.getApplyDate().toString();
        this.companyName = entry.getCompanyName();
        this.url = entry.getUrl();
        this.memo = entry.getMemo();
    }
}
