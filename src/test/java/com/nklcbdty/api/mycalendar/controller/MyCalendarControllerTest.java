package com.nklcbdty.api.mycalendar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.nklcbdty.api.mycalendar.dto.MyCalendarEntryDto;
import com.nklcbdty.api.mycalendar.dto.MyCalendarEntryRequest;
import com.nklcbdty.api.mycalendar.dto.MyCalendarMonthDto;
import com.nklcbdty.api.mycalendar.service.MyCalendarService;
import com.nklcbdty.api.mycalendar.vo.MyCalendarEntry;

/**
 * 프론트가 의지하는 경로·JSON 모양을 고정한다. 특히 완료 표시는 PUT 이어야 한다 —
 * PATCH 로 바꾸면 CORS 허용 메서드에 없어 브라우저에서만 조용히 막힌다.
 */
class MyCalendarControllerTest {

    private static final String ME = "local@me";

    private MyCalendarService myCalendarService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        myCalendarService = mock(MyCalendarService.class);
        mockMvc = standaloneSetup(new MyCalendarController(myCalendarService)).build();
    }

    private static MyCalendarEntry entry(Long id, LocalDate applyDate, String company) {
        MyCalendarEntry entry = new MyCalendarEntry();
        entry.setId(id);
        entry.setUserId(ME);
        entry.setApplyDate(applyDate);
        entry.setCompanyName(company);
        return entry;
    }

    @Test
    @DisplayName("GET /entries: 상시채용은 ongoingEntries 로, applyDate 는 null 로 내려간다")
    void entries_returnsOngoingSeparately() throws Exception {
        MyCalendarMonthDto month = new MyCalendarMonthDto(
            YearMonth.of(2026, 9), 0, List.of(),
            List.of(new MyCalendarEntryDto(entry(2L, null, "네이버"))));
        when(myCalendarService.getMonth(eq(ME), eq(YearMonth.of(2026, 9)))).thenReturn(month);

        mockMvc.perform(get("/api/my-calendar/entries?year=2026&month=9").requestAttr("userId", ME))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ongoingCount").value(1))
            .andExpect(jsonPath("$.ongoingEntries[0].companyName").value("네이버"))
            .andExpect(jsonPath("$.ongoingEntries[0].ongoing").value(true))
            .andExpect(jsonPath("$.ongoingEntries[0].applyDate").isEmpty())
            .andExpect(jsonPath("$.ongoingEntries[0].completed").value(false));
    }

    @Test
    @DisplayName("POST /entries: ongoing 을 그대로 서비스에 넘긴다")
    void create_passesOngoingThrough() throws Exception {
        when(myCalendarService.create(eq(ME), any()))
            .thenReturn(new MyCalendarEntryDto(entry(1L, null, "카카오")));

        mockMvc.perform(post("/api/my-calendar/entries")
                .requestAttr("userId", ME)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ongoing\":true,\"companyName\":\"카카오\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ongoing").value(true));

        ArgumentCaptor<MyCalendarEntryRequest> sent =
            ArgumentCaptor.forClass(MyCalendarEntryRequest.class);
        verify(myCalendarService).create(eq(ME), sent.capture());
        assertThat(sent.getValue().getOngoing()).isTrue();
    }

    @Test
    @DisplayName("PUT /entries/{id}/complete: 완료로 표시한다")
    void complete_marksDone() throws Exception {
        MyCalendarEntry done = entry(7L, null, "카카오");
        done.setCompleted(true);
        when(myCalendarService.setCompleted(ME, 7L, true)).thenReturn(new MyCalendarEntryDto(done));

        mockMvc.perform(put("/api/my-calendar/entries/7/complete")
                .requestAttr("userId", ME)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"completed\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.completed").value(true));

        verify(myCalendarService).setCompleted(ME, 7L, true);
    }

    @Test
    @DisplayName("PUT /entries/{id}/complete: completed=false 면 완료를 되돌린다")
    void complete_canBeUndone() throws Exception {
        when(myCalendarService.setCompleted(ME, 7L, false))
            .thenReturn(new MyCalendarEntryDto(entry(7L, null, "카카오")));

        mockMvc.perform(put("/api/my-calendar/entries/7/complete")
                .requestAttr("userId", ME)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"completed\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.completed").value(false));

        verify(myCalendarService).setCompleted(ME, 7L, false);
    }

    @Test
    @DisplayName("PUT /entries/{id}/complete: 본문 없이 부르면 완료로 본다")
    void complete_defaultsToDone() throws Exception {
        MyCalendarEntry done = entry(7L, null, "카카오");
        done.setCompleted(true);
        when(myCalendarService.setCompleted(ME, 7L, true)).thenReturn(new MyCalendarEntryDto(done));

        mockMvc.perform(put("/api/my-calendar/entries/7/complete").requestAttr("userId", ME))
            .andExpect(status().isOk());

        verify(myCalendarService).setCompleted(ME, 7L, true);
    }

    @Test
    @DisplayName("로그인 사용자를 알 수 없으면 완료 표시도 401 — 남의 일정을 건드리면 안 된다")
    void complete_requiresLogin() throws Exception {
        mockMvc.perform(put("/api/my-calendar/entries/7/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"completed\":true}"))
            .andExpect(status().isUnauthorized());

        verify(myCalendarService, never()).setCompleted(any(), any(), anyBoolean());
    }
}
