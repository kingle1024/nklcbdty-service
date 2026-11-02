package com.nklcbdty.api.companyfeature.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/** 근무형태(예: 재택) 회사 목록의 한 줄. */
@Getter
@Builder
public class WorkStyleCompanyDto {
    private final String companyCd;
    private final String companyNm;
    private final String careerPageUrl;
    private final String workStyle;
    private final String workStyleLabel;
    /** 근무형태 근거가 나온 공고 수(중복 없음) */
    private final int postingCount;
    /** 근무형태 근거(최대 {@code CompanyFeatureService.MAX_EVIDENCES}건) */
    private final List<FeatureEvidenceDto> evidences;
    /** 카테고리 정렬 순서대로 */
    private final List<CompanyWelfareDto> welfares;
}
