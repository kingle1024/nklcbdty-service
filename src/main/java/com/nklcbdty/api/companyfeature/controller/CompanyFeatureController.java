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
import com.nklcbdty.api.companyfeature.vo.WelfareMatch;
import com.nklcbdty.api.companyfeature.vo.WorkStyle;

/**
 * 근무형태별 회사 모아보기. /api/company/** 는 로그인 없이 공개(AllowedPaths.COMPANY_ALL).
 * <ul>
 *   <li>{@code GET /api/company/features?workStyle=REMOTE&welfare=MEAL&welfare=HEALTH&match=ANY} :
 *       근무형태는 선택 사항(없으면 복지로만), 복지는 모두(ALL, 기본)/하나라도(ANY)</li>
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

    @GetMapping("/features")
    public List<WorkStyleCompanyDto> featureCompanies(@RequestParam(required = false) String workStyle,
                                                      @RequestParam(required = false) List<String> welfare,
                                                      @RequestParam(required = false) String match) {
        WorkStyle style = workStyle == null || workStyle.isBlank() ? null : WorkStyle.fromCode(workStyle);
        return companyFeatureService.findCompanies(style, welfare, WelfareMatch.fromCode(match));
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
