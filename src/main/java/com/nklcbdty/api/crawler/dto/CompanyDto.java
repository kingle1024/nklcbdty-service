package com.nklcbdty.api.crawler.dto;

import com.nklcbdty.api.crawler.common.CompanyEnums;

import lombok.Getter;

/** 회사 목록 응답. 회사 탭과 "채용 페이지로 가기" 버튼에서 쓴다. */
@Getter
public class CompanyDto {

    private final String companyCd;
    private final String companyNm;
    private final String careerPageUrl;

    public CompanyDto(CompanyEnums company) {
        this.companyCd = company.getCompanyCd();
        this.companyNm = company.getCompanyNm();
        this.careerPageUrl = company.getCareerPageUrl();
    }
}
