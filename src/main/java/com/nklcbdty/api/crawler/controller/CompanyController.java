package com.nklcbdty.api.crawler.controller;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.crawler.common.CompanyEnums;
import com.nklcbdty.api.crawler.dto.CompanyDto;

/**
 * 회사 목록 + 각 회사의 채용 페이지 주소.
 *
 * <p>예: {@code GET /api/company/list}</p>
 */
@RestController
@RequestMapping("/api/company")
public class CompanyController {

    @GetMapping("/list")
    public List<CompanyDto> list() {
        return Arrays.stream(CompanyEnums.values())
                     .map(CompanyDto::new)
                     .collect(Collectors.toList());
    }
}
