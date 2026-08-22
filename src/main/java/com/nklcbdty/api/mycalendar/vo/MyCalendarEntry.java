package com.nklcbdty.api.mycalendar.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * "나의 채용 캘린더" 한 칸. 사용자가 직접 적어 넣는 지원 예정 회사다.
 *
 * <p>공개 채용 캘린더({@code /api/calendar/deadlines})가 크롤링한 공고의 마감일을 보여주는 것과 달리,
 * 이쪽은 크롤링 대상이 아닌 회사도 적을 수 있어야 하므로 {@code job_mst} 와 엮지 않는다.</p>
 *
 * <p>남의 일정이 보이면 안 되므로 조회·수정·삭제는 모두 {@code userId} 로 한정한다.
 * 게시판과 달리 soft delete 를 쓰지 않는다 — 남이 볼 일이 없는 개인 메모라 되살릴 이유가 없다.</p>
 */
@Entity
@Table(name = "my_calendar_entry")
@Data
public class MyCalendarEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 토큰 subject 와 같은 값("kakao@{id}" 또는 "local@{id}") */
    @Column(nullable = false)
    private String userId;

    /**
     * 달력에서 이 일정이 찍히는 날. <b>{@code null} 이면 상시채용</b>이다 — 마감일이 없어 어느 칸에도
     * 못 찍으므로 달력 아래 상시채용 목록에 따로 모아 보여준다.
     *
     * <p>마감일 대신 {@code 2000-01-01} 같은 표식을 넣지 않는다. {@code job_mst.end_date} 가 그렇게
     * 쓰이는데, 실제 날짜와 구분이 안 돼서 공고가 사라진 원인을 찾는 데 한참 걸렸다.</p>
     */
    @Column(nullable = true)
    private LocalDate applyDate;

    /** 유일한 필수 입력값 */
    @Column(nullable = false, length = 100)
    private String companyName;

    /** 공고 주소. 없어도 된다. */
    @Column(nullable = true, length = 1000)
    private String url;

    @Column(nullable = true, length = 2000)
    private String memo;

    /**
     * 지원을 마쳤는지. 상시채용은 마감일이 없어 언제까지고 목록에 남으므로, 손으로 끝냈다고
     * 표시할 수 있어야 한다. 날짜가 있는 일정에도 같이 쓴다.
     *
     * <p>등록·수정({@code apply})은 이 값을 건드리지 않는다. 완료 표시는 전용 API 만 바꾼다 —
     * 그러지 않으면 완료한 일정의 메모만 고쳐도 완료가 풀린다.</p>
     */
    @Column(nullable = false)
    private boolean completed;

    /** 완료로 표시한 시각. 완료를 되돌리면 다시 {@code null} 이 된다. */
    @Column(nullable = true)
    private LocalDateTime completedDts;

    @CreationTimestamp
    private LocalDateTime insertDts;

    @UpdateTimestamp
    private LocalDateTime updateDts;
}
