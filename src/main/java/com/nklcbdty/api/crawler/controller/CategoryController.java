package com.nklcbdty.api.crawler.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.crawler.service.CategoryService;
import com.nklcbdty.api.crawler.vo.CategoryMst;

@RestController
@RequestMapping("/api/category")
public class CategoryController {
    private final CategoryService categoryService;

    @Autowired
    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/list")
    public List<CategoryMst> list() {
        return categoryService.getAllCategoriesOrderedByRank();
    }
}
