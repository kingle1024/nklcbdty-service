package com.nklcbdty.api.troubleshooting;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.nklcbdty.api.troubleshooting.controller.AdminTroubleshootingController;
import com.nklcbdty.api.troubleshooting.dto.TroubleshootingNoteDetailDto;
import com.nklcbdty.api.troubleshooting.service.TroubleshootingNoteService;

/**
 * 경로가 서로를 먹지 않는지 못박는다.
 *
 * <p>{@code /export} 는 {@code /{slug}} 와 같은 자리에 있다. 스프링은 경로 변수보다 고정
 * 문자열 패턴을 먼저 고르므로 지금은 맞게 동작하지만, 사람이 읽고 헷갈리는 자리이고
 * 누군가 매핑을 손대면 조용히 깨진다 — 그러면 '전체 복사' 가 slug 가 "export" 인 기록을
 * 찾다가 404 를 받는다. 화면에서만 드러나는 실패라 코드만 읽어서는 안 잡힌다.
 *
 * <p>컨텍스트를 띄우지 않는 {@code standaloneSetup} 을 쓴다(이 저장소의 다른 컨트롤러
 * 테스트와 같은 방식). 매핑 해석은 실제와 같은 경로를 타므로 확인하려는 것은 그대로 검증된다.
 */
class AdminTroubleshootingRouteTest {

    private TroubleshootingNoteService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(TroubleshootingNoteService.class);
        mockMvc = standaloneSetup(new AdminTroubleshootingController(service)).build();
    }

    private static TroubleshootingNoteDetailDto note(String slug) {
        return TroubleshootingNoteDetailDto.builder()
            .id(1L)
            .slug(slug)
            .occurredOn(LocalDate.of(2026, 8, 20))
            .project("dart-tracker")
            .title("제목")
            .build();
    }

    @Test
    @DisplayName("/export 는 상세가 아니라 export 로 간다")
    void exportIsNotTreatedAsSlug() throws Exception {
        when(service.export(any(), any(), any(), any())).thenReturn(List.of(note("a"), note("b")));

        mockMvc.perform(get("/api/admin/troubleshooting/export"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(2))
            .andExpect(jsonPath("$.notes[0].slug").value("a"));

        verify(service).export(isNull(), isNull(), isNull(), isNull());
        // slug 조회로 새지 않아야 한다
        verify(service, never()).findBySlug(any());
    }

    @Test
    @DisplayName("/export 는 목록과 같은 조건을 그대로 받는다")
    void exportPassesFilters() throws Exception {
        when(service.export(any(), any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/troubleshooting/export")
                .param("keyword", "Connection refused")
                .param("project", "OmniEsol ERP10")
                .param("severity", "critical")
                .param("tag", "jsch"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(0));

        verify(service).export("Connection refused", "OmniEsol ERP10", "critical", "jsch");
    }

    @Test
    @DisplayName("보통 slug 는 그대로 상세로 간다")
    void normalSlugStillGoesToDetail() throws Exception {
        when(service.findBySlug(eq("dart-tracker-list-json-page-shift-21000")))
            .thenReturn(note("dart-tracker-list-json-page-shift-21000"));

        mockMvc.perform(get("/api/admin/troubleshooting/dart-tracker-list-json-page-shift-21000"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.slug").value("dart-tracker-list-json-page-shift-21000"));

        verify(service, never()).export(any(), any(), any(), any());
    }

    @Test
    @DisplayName("없는 slug 는 404 와 메세지를 준다")
    void missingSlugIs404() throws Exception {
        when(service.findBySlug(any())).thenReturn(null);

        mockMvc.perform(get("/api/admin/troubleshooting/no-such-note"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("목록은 기본 페이지·크기로 서비스를 부른다")
    void listUsesDefaults() throws Exception {
        when(service.list(any(), any(), any(), any(), eq(0), eq(20)))
            .thenReturn(com.nklcbdty.api.troubleshooting.dto.TroubleshootingPageResponse.builder()
                .rows(List.of())
                .totalElements(0)
                .totalPages(0)
                .pageNumber(0)
                .pageSize(20)
                .totalNotes(0)
                .projects(List.of())
                .tags(List.of())
                .severityCounts(java.util.Map.of())
                .build());

        mockMvc.perform(get("/api/admin/troubleshooting"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pageSize").value(20));

        // 매처를 쓰면 모든 인자를 매처로 줘야 한다. 숫자를 그대로 두면 InvalidUseOfMatchers 가 난다.
        verify(service).list(isNull(), isNull(), isNull(), isNull(), eq(0), eq(20));
    }
}
