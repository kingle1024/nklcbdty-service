package com.nklcbdty.api.career;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.time.LocalDate;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.nklcbdty.api.career.controller.AdminCareerController;
import com.nklcbdty.api.career.controller.CareerExceptionHandler;
import com.nklcbdty.api.career.dto.CareerEntryDto;
import com.nklcbdty.api.career.dto.CareerEntryRequest;
import com.nklcbdty.api.career.service.CareerEntryService;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 쓰기 경로의 요청 해석과 오류 응답 모양. 화면은 {@code message} 를 그대로 띄우므로
 * 400/404 가 메시지를 실어 오는지가 중요하다.
 */
class AdminCareerRouteTest {

    private CareerEntryService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(CareerEntryService.class);
        mockMvc = standaloneSetup(new AdminCareerController(service))
            .setControllerAdvice(new CareerExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("추가 요청의 날짜·배열이 그대로 서비스로 간다")
    void createParsesBody() throws Exception {
        when(service.create(any())).thenReturn(CareerEntryDto.builder().id(7L).slug("company-x").build());

        mockMvc.perform(post("/api/admin/career")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"entryType\":\"company\",\"title\":\"개발\",\"startedOn\":\"2023-05-01\","
                    + "\"achievements\":[\"a\",\"b\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(7));

        ArgumentCaptor<CareerEntryRequest> captor = ArgumentCaptor.forClass(CareerEntryRequest.class);
        verify(service).create(captor.capture());
        assertEquals(LocalDate.of(2023, 5, 1), captor.getValue().getStartedOn());
        assertEquals(2, captor.getValue().getAchievements().size());
    }

    @Test
    @DisplayName("검사에 걸리면 400 과 메시지")
    void invalidIs400() throws Exception {
        when(service.update(eq(1L), any())).thenThrow(new IllegalArgumentException("제목을 입력해 주세요."));

        mockMvc.perform(put("/api/admin/career/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"entryType\":\"company\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("제목을 입력해 주세요."));
    }

    @Test
    @DisplayName("없는 항목은 404 와 메시지")
    void missingIs404() throws Exception {
        doThrow(new NoSuchElementException("해당 경력 항목을 찾을 수 없습니다.")).when(service).delete(9L);

        mockMvc.perform(delete("/api/admin/career/9"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").exists());
    }
}
