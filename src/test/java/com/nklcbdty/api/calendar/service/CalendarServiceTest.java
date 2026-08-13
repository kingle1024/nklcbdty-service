package com.nklcbdty.api.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.YearMonth;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nklcbdty.api.calendar.dto.CalendarDayDto;
import com.nklcbdty.api.calendar.dto.CalendarJobDto;
import com.nklcbdty.api.calendar.dto.CalendarMonthDto;
import com.nklcbdty.api.crawler.service.JobService;
import com.nklcbdty.common.crawler.repository.JobRepository;
import com.nklcbdty.common.vo.Job_mst;

@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    @Mock
    private JobRepository jobRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CalendarService calendarService() {
        return new CalendarService(new JobService(jobRepository, objectMapper), objectMapper);
    }

    private Job_mst job(String annoId, String subject, String endDate) {
        Job_mst j = new Job_mst();
        j.setCompanyCd("NAVER");
        j.setAnnoId(annoId);
        j.setAnnoSubject(subject);
        j.setSubJobCdNm("백엔드");
        j.setEndDate(endDate);
        return j;
    }

    private void givenJobs(Job_mst... jobs) {
        when(jobRepository.findAllByCompanyCdAndSubJobCdNmIsNotNullOrderByEndDateAsc("NAVER"))
            .thenReturn(Arrays.asList(jobs));
    }

    @Test
    @DisplayName("같은 날 마감되는 공고는 한 날짜로 묶이고, 날짜는 오름차순으로 나온다")
    void groupsJobsByDeadlineDate() {
        givenJobs(
            job("3", "8월 20일 마감", "2099-08-20 18:00:00"),
            job("1", "8월 10일 마감 A", "2099-08-10 23:59:00"),
            job("2", "8월 10일 마감 B", "2099-08-10 12:00:00")
        );

        CalendarMonthDto result = calendarService().getMonthlyDeadlines(YearMonth.of(2099, 8), "NAVER");

        assertThat(result.getYear()).isEqualTo(2099);
        assertThat(result.getMonth()).isEqualTo(8);
        assertThat(result.getTotalCount()).isEqualTo(3);
        assertThat(result.getDays()).extracting(CalendarDayDto::getDate)
            .containsExactly("2099-08-10", "2099-08-20");
        // 하루 안에서는 마감 시간이 빠른 공고가 먼저
        assertThat(result.getDays().get(0).getJobs()).extracting(CalendarJobDto::getAnnoSubject)
            .containsExactly("8월 10일 마감 B", "8월 10일 마감 A");
        assertThat(result.getDays().get(0).getCount()).isEqualTo(2);
        assertThat(result.getDays().get(0).getJobs().get(0).getEndTime()).isEqualTo("12:00");
    }

    @Test
    @DisplayName("다른 달에 마감되는 공고는 그 달 캘린더에 나오지 않는다")
    void excludesOtherMonths() {
        givenJobs(
            job("1", "이번 달 마감", "2099-08-31 23:59:00"),
            job("2", "다음 달 마감", "2099-09-01 00:00:00"),
            job("3", "지난 달 마감", "2099-07-31 23:59:00")
        );

        CalendarMonthDto result = calendarService().getMonthlyDeadlines(YearMonth.of(2099, 8), "NAVER");

        assertThat(result.getTotalCount()).isEqualTo(1);
        assertThat(result.getDays()).hasSize(1);
        assertThat(result.getDays().get(0).getJobs()).extracting(CalendarJobDto::getAnnoSubject)
            .containsExactly("이번 달 마감");
    }

    @Test
    @DisplayName("마감일이 없는 상시채용과 손상된 마감일 공고는 캘린더에서 제외된다")
    void excludesJobsWithoutUsableDeadline() {
        givenJobs(
            job("1", "마감일 있는 공고", "2099-08-15 18:00:00"),
            job("2", "상시채용(null)", null),
            job("3", "상시채용(영입종료시)", "영입종료시"),
            job("4", "손상 데이터", "error")
        );

        CalendarMonthDto result = calendarService().getMonthlyDeadlines(YearMonth.of(2099, 8), "NAVER");

        assertThat(result.getTotalCount()).isEqualTo(1);
        assertThat(result.getDays().get(0).getJobs()).extracting(CalendarJobDto::getAnnoSubject)
            .containsExactly("마감일 있는 공고");
    }

    @Test
    @DisplayName("크롤 중복으로 같은 annoId 가 여러 건이어도 캘린더에는 한 건만 나온다")
    void deduplicatesSameAnnoId() {
        givenJobs(
            job("12345", "Product Data Analyst", "2099-08-15 18:00:00"),
            job("12345", "Product Data Analyst", "2099-08-15 18:00:00")
        );

        CalendarMonthDto result = calendarService().getMonthlyDeadlines(YearMonth.of(2099, 8), "NAVER");

        assertThat(result.getTotalCount()).isEqualTo(1);
        assertThat(result.getDays().get(0).getCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 지난 마감도 그 달 캘린더에는 남는다 (마감 여부 판정은 프론트가 endDate 로 한다)")
    void keepsPastDeadlines() {
        givenJobs(
            job("1", "이미 마감된 공고", "2000-01-10 18:00:00"),
            job("2", "아직 열린 공고", "2099-08-10 18:00:00")
        );
        CalendarService service = calendarService();

        CalendarMonthDto past = service.getMonthlyDeadlines(YearMonth.of(2000, 1), "NAVER");
        assertThat(past.getTotalCount()).isEqualTo(1);
        assertThat(past.getDays().get(0).getJobs().get(0).getEndDate()).isEqualTo("2000-01-10 18:00:00");

        CalendarMonthDto future = service.getMonthlyDeadlines(YearMonth.of(2099, 8), "NAVER");
        assertThat(future.getTotalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("응답 JSON 은 '지금' 에 의존하는 값을 담지 않는다 — 그래야 캐싱해도 안전하다")
    void jsonHasNoTimeDependentField() {
        givenJobs(job("1", "마감 지난 공고", "2000-01-10 18:00:00"));

        String json = calendarService().getMonthlyDeadlinesAsJson(YearMonth.of(2000, 1), "NAVER");

        assertThat(json).doesNotContain("closed");
        assertThat(json).contains("\"endDate\":\"2000-01-10 18:00:00\"", "\"endTime\":\"18:00\"");
    }

    @Test
    @DisplayName("JSON 응답은 객체 응답을 그대로 직렬화한 것이다")
    void jsonMatchesObject() throws Exception {
        givenJobs(job("1", "공고", "2099-08-10 18:00:00"));
        CalendarService service = calendarService();

        String json = service.getMonthlyDeadlinesAsJson(YearMonth.of(2099, 8), "NAVER");

        assertThat(json).isEqualTo(
            objectMapper.writeValueAsString(service.getMonthlyDeadlines(YearMonth.of(2099, 8), "NAVER")));
    }

    @Test
    @DisplayName("company=ALL 이면 전체 회사 공고를 보고, 회사명은 한글로 채워진다")
    void allCompaniesAndKoreanCompanyName() {
        Job_mst naver = job("1", "네이버 공고", "2099-08-05 18:00:00");
        Job_mst kakao = job("2", "카카오 공고", "2099-08-06 18:00:00");
        kakao.setCompanyCd("KAKAO");
        when(jobRepository.findAllBySubJobCdNmIsNotNull()).thenReturn(Arrays.asList(naver, kakao));

        CalendarMonthDto result = calendarService().getMonthlyDeadlines(YearMonth.of(2099, 8), "ALL");

        assertThat(result.getTotalCount()).isEqualTo(2);
        assertThat(result.getDays()).flatExtracting(CalendarDayDto::getJobs)
            .extracting(CalendarJobDto::getCompanyNm)
            .containsExactly("네이버", "카카오");
    }

    @Test
    @DisplayName("달 정보(1일 요일 · 일수)를 함께 줘서 프론트가 달력 칸을 배치할 수 있다")
    void includesMonthMetadata() {
        when(jobRepository.findAllBySubJobCdNmIsNotNull()).thenReturn(Arrays.asList());

        CalendarMonthDto result = calendarService().getMonthlyDeadlines(YearMonth.of(2026, 8), "ALL");

        assertThat(result.getFirstDayOfWeek()).isEqualTo(6); // 2026-08-01 은 토요일
        assertThat(result.getLengthOfMonth()).isEqualTo(31);
        assertThat(result.getDays()).isEmpty();
    }
}
