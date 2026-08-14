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

    /** 달력에서 이 일정이 찍히는 날 */
    @Column(nullable = false)
    private LocalDate applyDate;

    /** 유일한 필수 입력값 */
    @Column(nullable = false, length = 100)
    private String companyName;

    /** 공고 주소. 없어도 된다. */
    @Column(nullable = true, length = 1000)
    private String url;

    @Column(nullable = true, length = 2000)
    private String memo;

    @CreationTimestamp
    private LocalDateTime insertDts;

    @UpdateTimestamp
    private LocalDateTime updateDts;
}
