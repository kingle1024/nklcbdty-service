package com.nklcbdty.api.mycalendar.dto;

import com.nklcbdty.api.mycalendar.vo.MyCalendarEntry;

import lombok.Getter;

/** 달력 한 칸(또는 상시채용 목록)에 들어가는 내 일정 1건. */
@Getter
public class MyCalendarEntryDto {

    private final Long id;
    /** yyyy-MM-dd. 상시채용이면 null. */
    private final String applyDate;
    /** 마감일이 없는 상시채용인지. {@code applyDate} 가 없다는 뜻이라 따로 저장하지 않고 여기서 만든다. */
    private final boolean ongoing;
    private final String companyName;
    private final String url;
    private final String memo;
    private final boolean completed;
    /** 완료로 표시한 시각. 완료가 아니면 null. */
    private final String completedAt;

    public MyCalendarEntryDto(MyCalendarEntry entry) {
        this.id = entry.getId();
        this.applyDate = entry.getApplyDate() != null ? entry.getApplyDate().toString() : null;
        this.ongoing = entry.getApplyDate() == null;
        this.companyName = entry.getCompanyName();
        this.url = entry.getUrl();
        this.memo = entry.getMemo();
        this.completed = entry.isCompleted();
        this.completedAt = entry.getCompletedDts() != null ? entry.getCompletedDts().toString() : null;
    }
}
