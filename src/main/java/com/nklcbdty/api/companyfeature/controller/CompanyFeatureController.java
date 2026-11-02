package com.nklcbdty.api.companyfeature.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.companyfeature.dto.WelfareCategoryDto;
import com.nklcbdty.api.companyfeature.dto.WorkStyleCompanyDto;
import com.nklcbdty.api.companyfeature.service.CompanyFeatureService;
import com.nklcbdty.api.companyfeature.service.WelfareCategoryService;
import com.nklcbdty.api.companyfeature.vo.WorkStyle;

/**
 * 근무형태별 회사 모아보기. /api/company/** 는 로그인 없이 공개(AllowedPaths.COMPANY_ALL).
 * <ul>
 *   <li>{@code GET /api/company/work-style/REMOTE?welfare=MEAL&welfare=HEALTH} : 재택 회사 중 식대·건강 복지가 모두 있는 회사</li>
 *   <li>{@code GET /api/company/welfare-categories} : 필터에 쓸 활성 복지 카테고리</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/company")
public class CompanyFeatureController {

    private final CompanyFeatureService companyFeatureService;
    private final WelfareCategoryService welfareCategoryService;

    public CompanyFeatureController(CompanyFeatureService companyFeatureService,
                                    WelfareCategoryService welfareCategoryService) {
        this.companyFeatureService = companyFeatureService;
        this.welfareCategoryService = welfareCategoryService;
    }

    @GetMapping("/work-style/{workStyle}")
    public List<WorkStyleCompanyDto> companies(@PathVariable String workStyle,
                                               @RequestParam(required = false) List<String> welfare) {
        return companyFeatureService.findCompanies(WorkStyle.fromCode(workStyle), welfare);
    }

    @GetMapping("/welfare-categories")
    public List<WelfareCategoryDto> welfareCategories() {
        return welfareCategoryService.listActive();
    }
}
