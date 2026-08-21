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
import java.time.LocalDateTime;
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

    /** 마감일 없는 상시채용 등록 요청. 날짜를 같이 넘겨 "그래도 버리는지" 를 볼 수 있게 해 둔다. */
    private static MyCalendarEntryRequest ongoingRequest(String date, String company) {
        MyCalendarEntryRequest request = request(date, company, null, null);
        request.setOngoing(true);
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
    @DisplayName("상시채용이 아닌데 날짜가 없거나 형식이 틀리면 400")
    void applyDateMustBeValid() {
        assertThatThrownBy(() -> service.create(ME, request(null, "카카오", null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            // 날짜를 비우는 방법(상시채용)을 알려 줘야 사용자가 막히지 않는다.
            .hasMessageContaining("상시채용");
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

    // --------------------------------------------------------------------- 상시채용

    @Test
    @DisplayName("상시채용은 날짜 없이 저장된다")
    void ongoingEntryIsSavedWithoutDate() {
        when(entryRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        MyCalendarEntryDto created = service.create(ME, ongoingRequest(null, "카카오"));

        assertThat(created.isOngoing()).isTrue();
        assertThat(created.getApplyDate()).isNull();
    }

    @Test
    @DisplayName("상시채용으로 저장하면 함께 온 날짜는 버린다 — 표식 날짜를 남기지 않는다")
    void ongoingEntryDropsTheDate() {
        when(entryRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.create(ME, ongoingRequest("2026-09-01", "카카오"));

        ArgumentCaptor<MyCalendarEntry> saved = ArgumentCaptor.forClass(MyCalendarEntry.class);
        verify(entryRepository).save(saved.capture());
        assertThat(saved.getValue().getApplyDate()).isNull();
    }

    @Test
    @DisplayName("날짜 있는 일정을 상시채용으로 바꿀 수 있다")
    void updateCanTurnADatedEntryIntoOngoing() {
        MyCalendarEntry mine = entry(7L, ME, LocalDate.of(2026, 9, 1), "카카오");
        when(entryRepository.findByIdAndUserId(7L, ME)).thenReturn(Optional.of(mine));
        when(entryRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        MyCalendarEntryDto updated = service.update(ME, 7L, ongoingRequest("2026-09-01", "카카오"));

        assertThat(updated.isOngoing()).isTrue();
        assertThat(updated.getApplyDate()).isNull();
    }

    @Test
    @DisplayName("상시채용은 어느 달을 조회해도 함께 온다 — 특정 달의 일정이 아니다")
    void ongoingEntriesComeWithEveryMonth() {
        when(entryRepository.findByUserIdAndApplyDateBetweenOrderByApplyDateAscIdAsc(
            ME, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
            .thenReturn(List.of(entry(1L, ME, LocalDate.of(2026, 9, 3), "카카오")));
        when(entryRepository.findByUserIdAndApplyDateIsNullOrderByCompletedAscIdAsc(ME))
            .thenReturn(List.of(entry(2L, ME, null, "네이버"), entry(3L, ME, null, "토스")));

        MyCalendarMonthDto month = service.getMonth(ME, YearMonth.of(2026, 9));

        assertThat(month.getOngoingCount()).isEqualTo(2);
        assertThat(month.getOngoingEntries()).extracting(MyCalendarEntryDto::getCompanyName)
                                             .containsExactly("네이버", "토스");
        // 상시채용은 그 달의 일정 건수에 섞이지 않는다 — "9월에 적어 둔 일정 N건" 이 틀리면 안 된다.
        assertThat(month.getTotalCount()).isEqualTo(1);
        assertThat(month.getDays()).hasSize(1);
    }

    // ------------------------------------------------------------------- 완료 표시

    @Test
    @DisplayName("완료로 표시하면 완료 시각이 남는다")
    void completingStampsTheTime() {
        when(entryRepository.findByIdAndUserId(7L, ME))
            .thenReturn(Optional.of(entry(7L, ME, null, "카카오")));
        when(entryRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        MyCalendarEntryDto completed = service.setCompleted(ME, 7L, true);

        assertThat(completed.isCompleted()).isTrue();
        assertThat(completed.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("완료를 되돌리면 완료 시각도 지운다 — 앞뒤 안 맞는 값을 남기지 않는다")
    void uncompletingClearsTheTime() {
        MyCalendarEntry mine = entry(7L, ME, null, "카카오");
        mine.setCompleted(true);
        mine.setCompletedDts(LocalDateTime.of(2026, 8, 20, 10, 0));
        when(entryRepository.findByIdAndUserId(7L, ME)).thenReturn(Optional.of(mine));
        when(entryRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        MyCalendarEntryDto reopened = service.setCompleted(ME, 7L, false);

        assertThat(reopened.isCompleted()).isFalse();
        assertThat(reopened.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("같은 완료 요청을 두 번 보내도 결과가 같다 — 두 곳에서 눌릴 수 있다")
    void completingTwiceIsHarmless() {
        MyCalendarEntry mine = entry(7L, ME, null, "카카오");
        when(entryRepository.findByIdAndUserId(7L, ME)).thenReturn(Optional.of(mine));
        when(entryRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.setCompleted(ME, 7L, true);
        MyCalendarEntryDto again = service.setCompleted(ME, 7L, true);

        assertThat(again.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("일정을 수정해도 완료 표시는 풀리지 않는다")
    void updateKeepsTheCompletedFlag() {
        MyCalendarEntry mine = entry(7L, ME, null, "카카오");
        mine.setCompleted(true);
        mine.setCompletedDts(LocalDateTime.of(2026, 8, 20, 10, 0));
        when(entryRepository.findByIdAndUserId(7L, ME)).thenReturn(Optional.of(mine));
        when(entryRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        MyCalendarEntryDto updated = service.update(ME, 7L, ongoingRequest(null, "카카오"));

        assertThat(updated.isCompleted()).isTrue();
        assertThat(updated.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("남의 일정은 완료로 표시할 수 없다")
    void completeOnlyTouchesMyOwnEntry() {
        when(entryRepository.findByIdAndUserId(7L, SOMEONE_ELSE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setCompleted(SOMEONE_ELSE, 7L, true))
            .isInstanceOf(MyCalendarNotFoundException.class);

        verify(entryRepository, never()).save(any());
    }
}
