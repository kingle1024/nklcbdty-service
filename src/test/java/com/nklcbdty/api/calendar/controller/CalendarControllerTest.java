package com.nklcbdty.api.calendar.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.time.YearMonth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.nklcbdty.api.calendar.service.CalendarService;

class CalendarControllerTest {

    private CalendarService calendarService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        calendarService = mock(CalendarService.class);
        mockMvc = standaloneSetup(new CalendarController(calendarService)).build();
    }

    @Test
    @DisplayName("GET /api/calendar/deadlines: year/month 로 그 달 캘린더를 조회한다")
    void deadlines_returnsRequestedMonth() throws Exception {
        when(calendarService.getMonthlyDeadlinesAsJson(YearMonth.of(2026, 8), "NAVER"))
            .thenReturn("{\"year\":2026,\"month\":8,\"totalCount\":0,\"days\":[]}");

        mockMvc.perform(get("/api/calendar/deadlines?year=2026&month=8&company=NAVER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.year").value(2026))
            .andExpect(jsonPath("$.month").value(8))
            .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    @DisplayName("year/month 를 안 주면 이번 달, company 를 안 주면 전체 회사를 조회한다")
    void deadlines_defaultsToThisMonthAndAllCompanies() throws Exception {
        YearMonth thisMonth = YearMonth.now();
        when(calendarService.getMonthlyDeadlinesAsJson(any(), any())).thenReturn("{}");

        mockMvc.perform(get("/api/calendar/deadlines"))
            .andExpect(status().isOk());

        verify(calendarService).getMonthlyDeadlinesAsJson(eq(thisMonth), eq("ALL"));
    }

    @Test
    @DisplayName("한글이 깨지지 않도록 UTF-8 로 응답한다")
    void deadlines_respondsAsUtf8Json() throws Exception {
        when(calendarService.getMonthlyDeadlinesAsJson(any(), any()))
            .thenReturn("{\"companyNm\":\"네이버\"}");

        mockMvc.perform(get("/api/calendar/deadlines"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/json;charset=UTF-8"))
            .andExpect(jsonPath("$.companyNm").value("네이버"));
    }

    @Test
    @DisplayName("month 가 1~12 밖이면 400 을 준다")
    void deadlines_rejectsInvalidMonth() throws Exception {
        mockMvc.perform(get("/api/calendar/deadlines?year=2026&month=13"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("year 가 지원 범위 밖이면 400 을 준다")
    void deadlines_rejectsInvalidYear() throws Exception {
        mockMvc.perform(get("/api/calendar/deadlines?year=1900&month=1"))
            .andExpect(status().isBadRequest());
    }
}
