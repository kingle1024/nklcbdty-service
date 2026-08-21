package com.nklcbdty.api.mycalendar.dto;

import lombok.Data;

/**
 * 일정 등록·수정 요청.
 *
 * <p>필수는 {@code companyName} 뿐이다. URL·메모는 나중에 채워 넣을 수 있게 비워 둘 수 있고,
 * {@code ongoing} 이 참이면 날짜도 필요 없다(상시채용).</p>
 *
 * <p>완료 여부는 여기에 없다 — 전용 API({@code PUT /entries/{id}/complete}) 로만 바꾼다.</p>
 */
@Data
public class MyCalendarEntryRequest {

    /** yyyy-MM-dd. {@code ongoing} 이 참이면 무시한다. */
    private String applyDate;

    /**
     * 상시채용인지. 마감일이 없는 공고라 달력 칸에 찍지 않고 상시채용 목록에 담는다.
     * 예전 프론트가 안 보낼 수 있어 {@code Boolean} 으로 받고, 없으면 거짓으로 본다.
     */
    private Boolean ongoing;

    private String companyName;

    private String url;

    private String memo;
}
