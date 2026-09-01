package com.nklcbdty.api.board.service;

import java.time.LocalDate;

/**
 * 패치노트 한 항목. {@code classpath:board/patch-notes.md} 의 {@code ## yyyy-MM-dd | 제목} 한 덩어리다.
 *
 * @param date    패치 날짜. 공지의 작성 시각으로도 쓴다.
 * @param title   사용자에게 보일 제목. 비어 있을 수 있다(날짜만 쓴 항목).
 * @param body    공지 본문. 비어 있으면 공지로 올리지 않는다.
 * @param dedupeKey 이미 올라간 항목인지 판정하는 열쇠이자 공지 제목의 접두사.
 *                  제목/본문을 나중에 고쳐도 같은 항목으로 인식되도록 날짜(+같은 날짜 안 순번)로만 만든다.
 */
public record PatchNote(LocalDate date, String title, String body, String dedupeKey) {

    /** 게시판에 저장할 제목. 접두사를 붙여 두면 나중에 제목만 보고도 자동 등록분임을 알 수 있다. */
    public String noticeTitle() {
        return title.isBlank() ? dedupeKey : dedupeKey + " " + title;
    }
}
