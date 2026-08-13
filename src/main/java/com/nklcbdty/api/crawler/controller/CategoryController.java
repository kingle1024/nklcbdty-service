package com.nklcbdty.api.crawler.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.crawler.service.CategoryService;

@RestController
@RequestMapping("/api/category")
public class CategoryController {
    private final CategoryService categoryService;

    @Autowired
    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    // 캐시된 응답 JSON 을 그대로 흘려보낸다. charset 명시 이유는 JobController#list 주석 참고.
    @GetMapping(value = "/list", produces = "application/json;charset=UTF-8")
    public String list() {
        return categoryService.getAllCategoriesOrderedByRankAsJson();
    }
}
