package com.nklcbdty.api.resumereview.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.nklcbdty.api.resumereview.dto.ResumeReviewResponse;
import com.nklcbdty.api.resumereview.service.ResumeReviewException;
import com.nklcbdty.api.resumereview.service.ResumeReviewService;

class ResumeReviewControllerTest {

    private ResumeReviewService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ResumeReviewService.class);
        mockMvc = standaloneSetup(new ResumeReviewController(service)).build();
    }

    @Test
    @DisplayName("파일과 공고를 받아 첨삭 결과를 준다")
    void review() throws Exception {
        when(service.review(isNull(), eq("공고"), any())).thenReturn(new ResumeReviewResponse(
                "백엔드", "SK", List.of(), List.of(), List.of(), List.of("보강"), "m"));

        mockMvc.perform(multipart("/api/resume-review")
                                .file(new MockMultipartFile("files", "a.txt", "text/plain", "x".getBytes()))
                                .param("jobText", "공고"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.company").value("SK"))
               .andExpect(jsonPath("$.suggestions[0]").value("보강"));
    }

    @Test
    @DisplayName("입력 오류는 400, AI 실패는 409 로 사유를 본문에 담는다")
    void errors() throws Exception {
        when(service.review(any(), eq("bad"), any())).thenThrow(ResumeReviewException.badInput("파일 형식"));
        when(service.review(any(), eq("busy"), any())).thenThrow(ResumeReviewException.unavailable("혼잡"));

        mockMvc.perform(multipart("/api/resume-review").param("jobText", "bad"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.message").value("파일 형식"));
        mockMvc.perform(multipart("/api/resume-review").param("jobText", "busy"))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.message").value("혼잡"));
    }
}
