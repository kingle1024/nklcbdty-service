package com.nklcbdty.api.mycalendar.dto;

import lombok.Data;

/**
 * 완료 표시 요청. {@code completed} 를 그대로 지정하므로 같은 요청을 두 번 보내도 결과가 같다
 * (토글로 만들면 응답을 놓쳤을 때 완료가 풀린다).
 *
 * <p>값을 안 보내면 완료로 본다 — 되돌릴 때만 {@code false} 를 명시하면 된다.</p>
 */
@Data
public class MyCalendarCompleteRequest {

    private Boolean completed;
}
