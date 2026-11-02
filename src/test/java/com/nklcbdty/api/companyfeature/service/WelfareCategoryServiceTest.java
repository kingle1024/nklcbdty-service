package com.nklcbdty.api.companyfeature.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nklcbdty.api.companyfeature.dto.WelfareCategoryDto;
import com.nklcbdty.api.companyfeature.dto.WelfareCategoryRequest;
import com.nklcbdty.api.companyfeature.repository.WelfareCategoryRepository;
import com.nklcbdty.api.companyfeature.vo.WelfareCategory;

class WelfareCategoryServiceTest {

    private WelfareCategoryRepository repository;
    private WelfareCategoryService service;

    @BeforeEach
    void setUp() {
        repository = mock(WelfareCategoryRepository.class);
        service = new WelfareCategoryService(repository);
        when(repository.save(any(WelfareCategory.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("추가: 코드는 대문자로, 키워드는 공백 정리·중복 제거, 순서는 맨 뒤")
    void create_normalizesInput() {
        when(repository.existsByCode("REMOTE_ALLOWANCE")).thenReturn(false);
        when(repository.findMaxSortOrder()).thenReturn(5);

        WelfareCategoryDto dto = service.create(new WelfareCategoryRequest(
            " remote_allowance ", " 재택 수당 ", Arrays.asList(" 재택  수당 ", "", null, "Home Office", "home office"), null));

        assertThat(dto.getCode()).isEqualTo("REMOTE_ALLOWANCE");
        assertThat(dto.getName()).isEqualTo("재택 수당");
        assertThat(dto.getKeywords()).containsExactly("재택 수당", "Home Office");
        assertThat(dto.getSortOrder()).isEqualTo(6);
        assertThat(dto.isActive()).isTrue();
    }

    @Test
    @DisplayName("추가: 잘못된 코드·중복 코드·빈 키워드는 거부")
    void create_rejectsInvalid() {
        assertThatThrownBy(() -> service.create(new WelfareCategoryRequest("식대", "식대", List.of("식대"), null)))
            .isInstanceOf(IllegalArgumentException.class);

        when(repository.existsByCode("MEAL")).thenReturn(true);
        assertThatThrownBy(() -> service.create(new WelfareCategoryRequest("MEAL", "식대", List.of("식대"), null)))
            .hasMessageContaining("이미 있는 코드");

        assertThatThrownBy(() -> service.create(new WelfareCategoryRequest("GYM", "운동", List.of(" ", ""), null)))
            .hasMessageContaining("키워드");
    }

    @Test
    @DisplayName("수정: 넘긴 값만 바꾸고 코드는 그대로, 비활성화도 여기서")
    void update_changesOnlyGivenFields() {
        WelfareCategory existing = category(1L, "MEAL", 1);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        WelfareCategoryDto dto = service.update(1L, new WelfareCategoryRequest("HACK", null, null, false));

        assertThat(dto.getCode()).isEqualTo("MEAL");
        assertThat(dto.getName()).isEqualTo("MEAL 이름");
        assertThat(dto.isActive()).isFalse();
    }

    @Test
    @DisplayName("정렬: 받은 순서대로 1부터, 빠진 카테고리는 뒤에 기존 순서로")
    void reorder_assignsSequentialOrder() {
        List<WelfareCategory> all = new ArrayList<>(List.of(
            category(1L, "MEAL", 1), category(2L, "HEALTH", 2), category(3L, "EDUCATION", 3)));
        when(repository.findAllByOrderBySortOrderAscIdAsc()).thenReturn(all);

        List<WelfareCategoryDto> result = service.reorder(List.of(3L, 1L));

        assertThat(result).extracting(WelfareCategoryDto::getCode).containsExactly("EDUCATION", "MEAL", "HEALTH");
        assertThat(result).extracting(WelfareCategoryDto::getSortOrder).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("정렬: 없는 id 가 섞이면 거부")
    void reorder_rejectsUnknownId() {
        when(repository.findAllByOrderBySortOrderAscIdAsc()).thenReturn(List.of(category(1L, "MEAL", 1)));

        assertThatThrownBy(() -> service.reorder(List.of(1L, 99L))).hasMessageContaining("99");
    }

    private static WelfareCategory category(Long id, String code, int order) {
        WelfareCategory c = new WelfareCategory();
        c.setId(id);
        c.setCode(code);
        c.setName(code + " 이름");
        c.setKeywords("키워드");
        c.setSortOrder(order);
        return c;
    }
}
