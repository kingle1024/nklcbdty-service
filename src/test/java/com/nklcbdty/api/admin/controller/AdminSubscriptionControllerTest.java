package com.nklcbdty.api.admin.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.nklcbdty.api.admin.dto.AdminSubscriptionDetailDto;
import com.nklcbdty.api.admin.dto.AdminSubscriptionPageResponse;
import com.nklcbdty.api.admin.dto.AdminSubscriptionRowDto;
import com.nklcbdty.api.admin.dto.AdminSubscriptionStatsDto;
import com.nklcbdty.api.admin.service.AdminSubscriptionService;
import com.nklcbdty.api.user.dto.UserSettingsRequest;

class AdminSubscriptionControllerTest {

    private AdminSubscriptionService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(AdminSubscriptionService.class);
        mockMvc = standaloneSetup(new AdminSubscriptionController(service)).build();
    }

    @Test
    @DisplayName("GET /api/admin/subscriptions: 페이징 응답을 그대로 반환한다")
    void list_returnsPagedResponse() throws Exception {
        when(service.list(eq(0), eq(20), isNull())).thenReturn(
            AdminSubscriptionPageResponse.builder()
                .rows(List.of(AdminSubscriptionRowDto.builder()
                    .userId("kakao@1")
                    .email("u1@test.com")
                    .build()))
                .totalElements(1)
                .totalPages(1)
                .pageNumber(0)
                .pageSize(20)
                .build()
        );

        mockMvc.perform(get("/api/admin/subscriptions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.rows[0].userId").value("kakao@1"))
            .andExpect(jsonPath("$.rows[0].email").value("u1@test.com"));
    }

    @Test
    @DisplayName("GET /api/admin/subscriptions?keyword=...: keyword 파라미터가 서비스에 전달된다")
    void list_passesKeyword() throws Exception {
        when(service.list(eq(1), eq(10), eq("toss"))).thenReturn(
            AdminSubscriptionPageResponse.builder()
                .rows(List.of())
                .totalElements(0)
                .totalPages(0)
                .pageNumber(1)
                .pageSize(10)
                .build()
        );

        mockMvc.perform(get("/api/admin/subscriptions")
                .param("page", "1")
                .param("size", "10")
                .param("keyword", "toss"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pageNumber").value(1));

        verify(service).list(eq(1), eq(10), eq("toss"));
    }

    @Test
    @DisplayName("GET /api/admin/subscriptions/{userId}: 상세 응답을 반환한다")
    void detail_returnsUserDetail() throws Exception {
        when(service.detail("kakao@1")).thenReturn(
            AdminSubscriptionDetailDto.builder()
                .userId("kakao@1")
                .username("name")
                .email("n@test.com")
                .items(List.of())
                .build()
        );

        mockMvc.perform(get("/api/admin/subscriptions/kakao@1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value("kakao@1"))
            .andExpect(jsonPath("$.email").value("n@test.com"));
    }

    @Test
    @DisplayName("GET /api/admin/subscriptions/stats: 통계 응답을 반환한다")
    void stats_returnsStats() throws Exception {
        when(service.stats()).thenReturn(
            AdminSubscriptionStatsDto.builder()
                .totalSubscribers(3)
                .totalItems(6)
                .countsByItemType(Map.of("company", Map.of("naver", 2L)))
                .build()
        );

        mockMvc.perform(get("/api/admin/subscriptions/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSubscribers").value(3))
            .andExpect(jsonPath("$.totalItems").value(6))
            .andExpect(jsonPath("$.countsByItemType.company.naver").value(2));
    }

    @Test
    @DisplayName("DELETE /api/admin/subscriptions/items/{id}: 서비스 삭제 호출 후 200 반환")
    void deleteItem_callsService() throws Exception {
        mockMvc.perform(delete("/api/admin/subscriptions/items/42"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("deleted"))
            .andExpect(jsonPath("$.id").value(42));

        verify(service).deleteItem(eq(42L));
    }

    @Test
    @DisplayName("PUT /api/admin/subscriptions/{userId}: 통합 수정 요청을 서비스에 위임한다")
    void update_callsService() throws Exception {
        when(service.updateSubscriptions(eq("kakao@1"), any(UserSettingsRequest.class)))
            .thenReturn(
                AdminSubscriptionDetailDto.builder()
                    .userId("kakao@1")
                    .items(List.of())
                    .build()
            );

        String body = "{\"subscribedServices\":[\"naver\",\"toss\"],\"selectedJobRoles\":[\"Backend\"],\"selectedCareerYears\":\"5\"}";

        mockMvc.perform(put("/api/admin/subscriptions/kakao@1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value("kakao@1"));

        verify(service).updateSubscriptions(eq("kakao@1"), any(UserSettingsRequest.class));
    }
}
