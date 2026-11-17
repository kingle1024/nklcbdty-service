package com.nklcbdty.api.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.nklcbdty.api.admin.dto.AdminVisitorPageResponse;
import com.nklcbdty.api.log.dto.VisitorSearchCondition;
import com.nklcbdty.api.log.entity.VisitorEntity;
import com.nklcbdty.api.log.repository.VisitorRepository;

@ExtendWith(MockitoExtension.class)
class AdminVisitorServiceTest {

    @Mock
    private VisitorRepository visitorRepository;

    private static Date at(LocalDateTime t) {
        return Date.from(t.atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    void 조건을_넘기고_행을_변환한다() {
        final VisitorEntity v = VisitorEntity.builder()
            .id(7L).path("/api/list?company=ALL").insert_ip("121.137.86.218")
            .ip_origin("DOMESTIC").insert_dts(at(LocalDateTime.of(2026, 11, 17, 13, 49, 15)))
            .build();
        when(visitorRepository.search(any(), any())).thenAnswer(inv ->
            new PageImpl<>(List.of(v), inv.getArgument(1, Pageable.class), 1));
        when(visitorRepository.countByOrigin(any())).thenReturn(Map.of("DOMESTIC", 1L));

        final AdminVisitorPageResponse res = new AdminVisitorService(visitorRepository)
            .list(0, 30, true, true, "list", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 17));

        final ArgumentCaptor<VisitorSearchCondition> cond = ArgumentCaptor.forClass(VisitorSearchCondition.class);
        verify(visitorRepository).search(cond.capture(), any());
        assertThat(cond.getValue().isDomesticOnly()).isTrue();
        assertThat(cond.getValue().isExcludeInternal()).isTrue();
        assertThat(cond.getValue().getFrom()).isEqualTo(at(LocalDateTime.of(2026, 11, 1, 0, 0)));
        // 끝 날짜는 그날 하루를 다 포함해야 한다
        assertThat(cond.getValue().getTo()).isEqualTo(at(LocalDateTime.of(2026, 11, 18, 0, 0)));

        assertThat(res.getTotalElements()).isEqualTo(1);
        assertThat(res.getRows()).hasSize(1);
        assertThat(res.getRows().get(0).getIp()).isEqualTo("121.137.86.218");
        assertThat(res.getRows().get(0).isDomestic()).isTrue();
        assertThat(res.getRows().get(0).getInsertDts()).isEqualTo(LocalDateTime.of(2026, 11, 17, 13, 49, 15));
        assertThat(res.getOriginCounts()).containsEntry("DOMESTIC", 1L);
    }

    @Test
    void 페이지_크기를_제한하고_출처가_비면_UNKNOWN() {
        final VisitorEntity v = VisitorEntity.builder().id(1L).path("/").insert_ip("1.1.1.1").build();
        when(visitorRepository.search(any(), any())).thenAnswer(inv ->
            new PageImpl<>(List.of(v), inv.getArgument(1, Pageable.class), 1));

        final AdminVisitorPageResponse res = new AdminVisitorService(visitorRepository)
            .list(-3, 10_000, false, false, null, null, null);

        final ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(visitorRepository).search(any(), pageable.capture());
        assertThat(pageable.getValue()).isEqualTo(PageRequest.of(0, AdminVisitorService.MAX_PAGE_SIZE));
        assertThat(res.getRows().get(0).getOrigin()).isEqualTo("UNKNOWN");
        assertThat(res.getRows().get(0).isDomestic()).isFalse();
        assertThat(res.getRows().get(0).getInsertDts()).isNull();
    }
}
