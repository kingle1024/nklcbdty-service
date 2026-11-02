package com.nklcbdty.api.companyfeature.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.companyfeature.dto.WelfareCategoryDto;
import com.nklcbdty.api.companyfeature.dto.WelfareCategoryRequest;
import com.nklcbdty.api.companyfeature.service.WelfareCategoryService;

/**
 * 관리자용 복지 카테고리 관리. /api/admin/** 은 AuthFilter 가 ADMIN 토큰을 요구한다.
 * <ul>
 *   <li>{@code GET  /api/admin/welfare-categories}         : 전체(비활성 포함)</li>
 *   <li>{@code POST /api/admin/welfare-categories}         : 추가</li>
 *   <li>{@code PUT  /api/admin/welfare-categories/{id}}    : 이름·키워드·활성 여부 수정</li>
 *   <li>{@code PUT  /api/admin/welfare-categories/order}   : 정렬 (id 배열 순서대로)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/welfare-categories")
public class AdminWelfareCategoryController {

    private final WelfareCategoryService service;

    public AdminWelfareCategoryController(WelfareCategoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<WelfareCategoryDto> list() {
        return service.listAll();
    }

    @PostMapping
    public WelfareCategoryDto create(@RequestBody WelfareCategoryRequest request) {
        return service.create(request);
    }

    @PutMapping("/order")
    public List<WelfareCategoryDto> reorder(@RequestBody List<Long> orderedIds) {
        return service.reorder(orderedIds);
    }

    @PutMapping("/{id:\\d+}")
    public WelfareCategoryDto update(@PathVariable Long id, @RequestBody WelfareCategoryRequest request) {
        return service.update(id, request);
    }
}
