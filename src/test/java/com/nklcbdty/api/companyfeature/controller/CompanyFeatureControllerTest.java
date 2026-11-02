package com.nklcbdty.api.companyfeature.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.nklcbdty.api.companyfeature.dto.WelfareCategoryDto;
import com.nklcbdty.api.companyfeature.dto.WorkStyleCompanyDto;
import com.nklcbdty.api.companyfeature.service.CompanyFeatureService;
import com.nklcbdty.api.companyfeature.service.WelfareCategoryService;
import com.nklcbdty.api.companyfeature.vo.WorkStyle;

class CompanyFeatureControllerTest {

    private CompanyFeatureService companyFeatureService;
    private WelfareCategoryService welfareCategoryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        companyFeatureService = mock(CompanyFeatureService.class);
        welfareCategoryService = mock(WelfareCategoryService.class);
        mockMvc = standaloneSetup(
            new CompanyFeatureController(companyFeatureService, welfareCategoryService),
            new AdminWelfareCategoryController(welfareCategoryService))
            .setControllerAdvice(new CompanyFeatureExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("GET /api/company/work-style/remote?welfare=MEAL&welfare=HEALTH: 근무형태는 대소문자 무시, 복지는 다중 선택")
    void companies_passesWorkStyleAndWelfares() throws Exception {
        when(companyFeatureService.findCompanies(eq(WorkStyle.REMOTE), eq(List.of("MEAL", "HEALTH"))))
            .thenReturn(List.of(WorkStyleCompanyDto.builder().companyCd("LINE").companyNm("라인").postingCount(2).build()));

        mockMvc.perform(get("/api/company/work-style/remote").param("welfare", "MEAL", "HEALTH"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].companyCd").value("LINE"))
            .andExpect(jsonPath("$[0].postingCount").value(2));
    }

    @Test
    @DisplayName("없는 근무형태는 400")
    void companies_unknownWorkStyle() throws Exception {
        mockMvc.perform(get("/api/company/work-style/FOUR_DAY"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @DisplayName("GET /api/company/welfare-categories: 활성 카테고리만")
    void welfareCategories_activeOnly() throws Exception {
        when(welfareCategoryService.listActive()).thenReturn(List.of(
            WelfareCategoryDto.builder().id(1L).code("MEAL").name("식대").keywords(List.of("식대")).active(true).build()));

        mockMvc.perform(get("/api/company/welfare-categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].code").value("MEAL"))
            .andExpect(jsonPath("$[0].keywords[0]").value("식대"));
    }

    @Test
    @DisplayName("PUT /api/admin/welfare-categories/order: id 배열을 그대로 넘긴다(/{id} 와 겹치지 않음)")
    void reorder() throws Exception {
        when(welfareCategoryService.reorder(List.of(3L, 1L))).thenReturn(List.of());

        mockMvc.perform(put("/api/admin/welfare-categories/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[3,1]"))
            .andExpect(status().isOk());

        verify(welfareCategoryService).reorder(List.of(3L, 1L));
    }

    @Test
    @DisplayName("POST /api/admin/welfare-categories: 검증 실패는 400 + 메시지")
    void create_validationError() throws Exception {
        when(welfareCategoryService.create(any())).thenThrow(new IllegalArgumentException("이미 있는 코드입니다: MEAL"));

        mockMvc.perform(post("/api/admin/welfare-categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"MEAL\",\"name\":\"식대\",\"keywords\":[\"식대\"]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("이미 있는 코드입니다: MEAL"));
    }
}
