package com.nklcbdty.api.calendar.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.nklcbdty.api.crawler.common.CompanyEnums;
import com.nklcbdty.api.crawler.common.JobEndDates;
import com.nklcbdty.common.vo.Job_mst;

import lombok.Getter;

/** 캘린더 한 칸에 들어가는 공고 한 건. */
@Getter
public class CalendarJobDto {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final Long id;
    /** 공고 클릭 로그(/api/log/job_history)에 쓰는 공고 번호 */
    private final String annoId;
    private final String companyCd;
    /** 회사 한글명. enum 에 없는 코드면 코드값 그대로. */
    private final String companyNm;
    private final String annoSubject;
    private final String subJobCdNm;
    private final String empTypeCdNm;
    private final String workplace;
    private final String jobDetailLink;
    /** 원본 마감일시 문자열 (yyyy-MM-dd HH:mm[:ss]) */
    private final String endDate;
    /** 마감 시각 (HH:mm). 캘린더 칸에 "18:00 마감" 처럼 쓴다. */
    private final String endTime;

    // 마감 여부(closed)는 일부러 담지 않는다. "지금" 기준 값이라 응답이 시점에 따라 달라지면
    // Redis 캐싱이 stale 을 만든다. endDate 를 그대로 주고 판정은 프론트가 한다.
    public CalendarJobDto(Job_mst job) {
        this.id = job.getId();
        this.annoId = job.getAnnoId();
        this.companyCd = job.getCompanyCd();
        final CompanyEnums company = CompanyEnums.fromCompanyCd(job.getCompanyCd());
        this.companyNm = company != null ? company.getCompanyNm() : job.getCompanyCd();
        this.annoSubject = job.getAnnoSubject();
        this.subJobCdNm = job.getSubJobCdNm();
        this.empTypeCdNm = job.getEmpTypeCdNm();
        this.workplace = job.getWorkplace();
        this.jobDetailLink = job.getJobDetailLink();
        this.endDate = job.getEndDate();

        // 캘린더에 올라온 공고는 마감일시가 이미 파싱된 것들이라 null 이 아니지만, 방어적으로 처리한다.
        final LocalDateTime endDateTime = JobEndDates.parse(job.getEndDate());
        this.endTime = endDateTime != null ? endDateTime.format(TIME_FORMATTER) : null;
    }
}
