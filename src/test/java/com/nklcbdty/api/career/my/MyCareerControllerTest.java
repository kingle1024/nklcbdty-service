package com.nklcbdty.api.career.my;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.nklcbdty.api.career.controller.CareerExceptionHandler;
import com.nklcbdty.api.career.dto.CareerEntryDto;
import com.nklcbdty.api.career.my.controller.MyCareerController;
import com.nklcbdty.api.career.my.service.MyCareerService;

/** 프론트가 의지하는 경로·응답 모양과, 사용자를 모를 때 401 로 막는지를 고정한다 */
class MyCareerControllerTest {

    private static final String ME = "local@1";

    private MyCareerService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(MyCareerService.class);
        mockMvc = standaloneSetup(new MyCareerController(service))
            .setControllerAdvice(new CareerExceptionHandler())
            .build();
    }

    @Test
    void 목록은_entries_와_count_로_온다() throws Exception {
        when(service.findAll(ME)).thenReturn(List.of(CareerEntryDto.builder().id(1L).title("회사").build()));

        mockMvc.perform(get("/api/my-career").requestAttr("userId", ME))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.entries[0].title").value("회사"));
    }

    @Test
    void 사용자를_모르면_401_이고_서비스까지_가지_않는다() throws Exception {
        mockMvc.perform(get("/api/my-career"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
        verify(service, never()).findAll(anyString());
    }

    @Test
    void 추가는_로그인한_사용자로_저장한다() throws Exception {
        when(service.create(eq(ME), any())).thenReturn(CareerEntryDto.builder().id(7L).title("새 항목").build());

        mockMvc.perform(post("/api/my-career").requestAttr("userId", ME)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"entryType\":\"company\",\"title\":\"새 항목\",\"startedOn\":\"2024-01-01\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void 잘못된_입력은_400_메시지로_온다() throws Exception {
        when(service.update(eq(ME), eq(3L), any())).thenThrow(new IllegalArgumentException("제목을 입력해 주세요."));

        mockMvc.perform(put("/api/my-career/3").requestAttr("userId", ME)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("제목을 입력해 주세요."));
    }

    @Test
    void 없거나_남의_항목_삭제는_404() throws Exception {
        org.mockito.Mockito.doThrow(new NoSuchElementException("해당 경력 항목을 찾을 수 없습니다."))
            .when(service).delete(ME, 9L);

        mockMvc.perform(delete("/api/my-career/9").requestAttr("userId", ME))
            .andExpect(status().isNotFound());
    }
}
