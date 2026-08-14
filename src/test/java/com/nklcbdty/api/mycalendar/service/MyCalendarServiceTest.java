package com.nklcbdty.api.mycalendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nklcbdty.api.mycalendar.dto.MyCalendarEntryDto;
import com.nklcbdty.api.mycalendar.dto.MyCalendarEntryRequest;
import com.nklcbdty.api.mycalendar.dto.MyCalendarMonthDto;
import com.nklcbdty.api.mycalendar.exception.MyCalendarNotFoundException;
import com.nklcbdty.api.mycalendar.repository.MyCalendarEntryRepository;
import com.nklcbdty.api.mycalendar.vo.MyCalendarEntry;

@ExtendWith(MockitoExtension.class)
class MyCalendarServiceTest {

    private static final String ME = "local@me";
    private static final String SOMEONE_ELSE = "kakao@999";

    @Mock
    private MyCalendarEntryRepository entryRepository;

    @InjectMocks
    private MyCalendarService service;

    private static MyCalendarEntryRequest request(String date, String company, String url, String memo) {
        MyCalendarEntryRequest request = new MyCalendarEntryRequest();
        request.setApplyDate(date);
        request.setCompanyName(company);
        request.setUrl(url);
        request.setMemo(memo);
        return request;
    }

    private static MyCalendarEntry entry(Long id, String userId, LocalDate date, String company) {
        MyCalendarEntry entry = new MyCalendarEntry();
        entry.setId(id);
        entry.setUserId(userId);
        entry.setApplyDate(date);
        entry.setCompanyName(company);
        return entry;
    }

    // ------------------------------------------------------------------- 등록

    @Test
    @DisplayName("회사명만 있으면 저장된다 — URL·메모는 선택값")
    void createRequiresOnlyCompanyName() {
        when(entryRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        MyCalendarEntryDto created = service.create(ME, request("2026-09-01", "카카오", null, null));

        assertThat(created.getCompanyName()).isEqualTo("카카오");
        assertThat(created.getApplyDate()).isEqualTo("2026-09-01");
        assertThat(created.getUrl()).isNull();
        assertThat(created.getMemo()).isNull();
    }

    @Test
    @DisplayName("일정은 언제나 요청한 사용자 것으로 저장된다")
    void createStampsTheOwner() {
        when(entryRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.create(ME, request("2026-09-01", "카카오", null, null));

        ArgumentCaptor<MyCalendarEntry> saved = ArgumentCaptor.forClass(MyCalendarEntry.class);
        verify(entryRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(ME);
    }

    @Test
    @DisplayName("회사명이 없거나 공백뿐이면 400")
    void companyNameIsRequired() {
        assertThatThrownBy(() -> service.create(ME, request("2026-09-01", null, null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("회사명");
        assertThatThrownBy(() -> service.create(ME, request("2026-09-01", "   ", null, null)))
            .isInstanceOf(IllegalArgumentException.class);

        verify(entryRepository, never()).save(any());
    }

    @Test
    @DisplayName("날짜가 없거나 형식이 틀리면 400")
    void applyDateMustBeValid() {
        assertThatThrownBy(() -> service.create(ME, request(null, "카카오", null, null)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(ME, request("2026/09/01", "카카오", null, null)))
            .isInstanceOf(IllegalArgumentException.class);

        verify(entryRepository, never()).save(any());
    }

    @Test
    @DisplayName("스킴 없이 적은 URL 에는 https:// 를 붙여 준다 — 그래야 링크로 열린다")
    void urlGetsHttpsPrefix() {
        when(entryRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        MyCalendarEntryDto created =
            service.create(ME, request("2026-09-01", "카카오", "careers.kakao.com/jobs/1", null));

        assertThat(created.getUrl()).isEqualTo("https://careers.kakao.com/jobs/1");
    }

    @Test
    @DisplayName("http/https 가 아닌 스킴은 거부한다 — 링크로 걸면 위험하다")
    void nonHttpSchemesAreRejected() {
        assertThatThrownBy(() ->
            service.create(ME, request("2026-09-01", "카카오", "javascript:alert(1)", null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("http");

        verify(entryRepository, never()).save(any());
    }

    // ------------------------------------------------------------- 조회 / 수정 / 삭제

    @Test
    @DisplayName("그 달 일정을 날짜별로 모아 준다 — 같은 날은 한 칸에 함께")
    void getMonthGroupsByDate() {
        when(entryRepository.findByUserIdAndApplyDateBetweenOrderByApplyDateAscIdAsc(
            ME, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
            .thenReturn(List.of(
                entry(1L, ME, LocalDate.of(2026, 9, 3), "카카오"),
                entry(2L, ME, LocalDate.of(2026, 9, 3), "네이버"),
                entry(3L, ME, LocalDate.of(2026, 9, 20), "토스")));

        MyCalendarMonthDto month = service.getMonth(ME, YearMonth.of(2026, 9));

        assertThat(month.getTotalCount()).isEqualTo(3);
        assertThat(month.getLengthOfMonth()).isEqualTo(30);
        assertThat(month.getDays()).hasSize(2);
        assertThat(month.getDays().get(0).getDate()).isEqualTo("2026-09-03");
        assertThat(month.getDays().get(0).getCount()).isEqualTo(2);
        assertThat(month.getDays().get(1).getDate()).isEqualTo("2026-09-20");
    }

    @Test
    @DisplayName("남의 일정은 수정할 수 없다")
    void updateOnlyTouchesMyOwnEntry() {
        when(entryRepository.findByIdAndUserId(7L, SOMEONE_ELSE)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.update(SOMEONE_ELSE, 7L, request("2026-09-01", "카카오", null, null)))
            .isInstanceOf(MyCalendarNotFoundException.class);

        verify(entryRepository, never()).save(any());
    }

    @Test
    @DisplayName("남의 일정은 삭제할 수 없다")
    void deleteOnlyTouchesMyOwnEntry() {
        when(entryRepository.findByIdAndUserId(7L, SOMEONE_ELSE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(SOMEONE_ELSE, 7L))
            .isInstanceOf(MyCalendarNotFoundException.class);

        verify(entryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("내 일정은 수정된다")
    void updateChangesMyEntry() {
        MyCalendarEntry mine = entry(7L, ME, LocalDate.of(2026, 9, 1), "카카오");
        when(entryRepository.findByIdAndUserId(7L, ME)).thenReturn(Optional.of(mine));
        when(entryRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        MyCalendarEntryDto updated =
            service.update(ME, 7L, request("2026-09-05", "토스", null, "1차 면접"));

        assertThat(updated.getCompanyName()).isEqualTo("토스");
        assertThat(updated.getApplyDate()).isEqualTo("2026-09-05");
        assertThat(updated.getMemo()).isEqualTo("1차 면접");
    }

    @Test
    @DisplayName("id 만으로 일정을 찾지 않는다 — 언제나 userId 를 함께 건다")
    void lookupsAreAlwaysScopedByUser() {
        when(entryRepository.findByIdAndUserId(anyLong(), anyString()))
            .thenReturn(Optional.of(entry(7L, ME, LocalDate.of(2026, 9, 1), "카카오")));

        service.delete(ME, 7L);

        verify(entryRepository).findByIdAndUserId(7L, ME);
        verify(entryRepository, never()).findById(anyLong());
    }
}
