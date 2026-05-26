package com.nklcbdty.api.email.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nklcbdty.api.crawler.vo.Job_mst;

class EmailServiceTest {

    /**
     * pushFarFutureEndDateToBottom은 의존성 없이 순수 정렬 로직만 사용하므로
     * 다른 협력자 mock 없이 EmailService 인스턴스를 직접 생성해 검증한다.
     */
    private final EmailService emailService = new EmailService(null, null, null, null, null);

    @Test
    @DisplayName("종료일이 1년을 초과하는 공고는 1년 이내 공고 뒤로 밀린다")
    void farFutureEndDate_isPushedToBottom() {
        LocalDate today = LocalDate.now();
        Job_mst near = job("이번 달 마감", today.plusDays(7).toString() + " 23:59:59");
        Job_mst veryFar = job("2999-12-31 마감", "2999-12-31 23:59:59");
        Job_mst nextMonth = job("한 달 후 마감", today.plusMonths(1).toString() + " 23:59:59");

        // 원본은 endDate DESC라고 가정 → veryFar가 맨 앞
        List<Job_mst> input = List.of(veryFar, nextMonth, near);

        List<Job_mst> result = emailService.pushFarFutureEndDateToBottom(input);

        assertThat(result).extracting(Job_mst::getAnnoSubject)
            .containsExactly("한 달 후 마감", "이번 달 마감", "2999-12-31 마감");
    }

    @Test
    @DisplayName("정확히 1년 이내(threshold)는 위 그룹으로 유지된다")
    void exactlyWithinOneYear_staysInTopGroup() {
        LocalDate today = LocalDate.now();
        Job_mst justWithin = job("11개월 후 마감", today.plusMonths(11).toString() + " 00:00:00");
        Job_mst justBeyond = job("13개월 후 마감", today.plusMonths(13).toString() + " 00:00:00");

        List<Job_mst> result = emailService.pushFarFutureEndDateToBottom(List.of(justBeyond, justWithin));

        assertThat(result).extracting(Job_mst::getAnnoSubject)
            .containsExactly("11개월 후 마감", "13개월 후 마감");
    }

    @Test
    @DisplayName("파싱 불가한 종료일(영입종료시 등)은 위 그룹으로 유지된다")
    void unparsableEndDate_treatedAsWithin() {
        Job_mst rolling = job("영입종료시", "영입종료시");
        Job_mst veryFar = job("2999-12-31", "2999-12-31 23:59:59");

        List<Job_mst> result = emailService.pushFarFutureEndDateToBottom(List.of(veryFar, rolling));

        assertThat(result).extracting(Job_mst::getAnnoSubject)
            .containsExactly("영입종료시", "2999-12-31");
    }

    @Test
    @DisplayName("null/빈 종료일은 위 그룹으로 유지된다")
    void nullOrEmptyEndDate_treatedAsWithin() {
        Job_mst nullDate = job("null", null);
        Job_mst emptyDate = job("empty", "");
        Job_mst veryFar = job("2999", "2999-12-31 23:59:59");

        List<Job_mst> result = emailService.pushFarFutureEndDateToBottom(List.of(veryFar, nullDate, emptyDate));

        assertThat(result).extracting(Job_mst::getAnnoSubject)
            .containsExactly("null", "empty", "2999");
    }

    private Job_mst job(String title, String endDate) {
        Job_mst j = new Job_mst();
        j.setAnnoSubject(title);
        j.setEndDate(endDate);
        return j;
    }
}
