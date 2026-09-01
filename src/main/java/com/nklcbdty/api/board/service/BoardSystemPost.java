package com.nklcbdty.api.board.service;

import java.time.LocalDateTime;

import com.nklcbdty.api.board.vo.BoardType;

/**
 * 시스템(샘플 시드·패치노트)이 넣는 글 한 건.
 *
 * @param boardType 공지사항 / 자유게시판
 * @param dedupeKey 이미 넣은 글인지 판정하는 제목 접두사. 같은 접두사로 시작하는 글이 있으면 넣지 않는다.
 * @param title     저장할 제목. {@code dedupeKey} 로 시작해야 다음 기동에서 같은 글로 인식된다.
 * @param content   본문
 * @param authorName 목록에 보일 작성자 표시명
 * @param adminAuthor 관리자 글이면 감사용 계정, 사용자 글처럼 보여야 하면 null.
 *                    이 값이 있으면 목록에 관리자 뱃지가 붙는다(BoardPostSummaryDto.writtenByAdmin).
 * @param pinned    목록 상단 고정 여부
 * @param writtenAt 작성 시각. 목록이 시간순으로 자연스럽게 보이도록 항목마다 다르게 준다.
 */
public record BoardSystemPost(BoardType boardType, String dedupeKey, String title, String content,
                              String authorName, String adminAuthor, boolean pinned, LocalDateTime writtenAt) {
}
