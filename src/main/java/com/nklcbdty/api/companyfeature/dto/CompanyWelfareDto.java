package com.nklcbdty.api.companyfeature.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/** 회사가 가진 복지 카테고리 하나와 그 근거 공고들. */
@Getter
@Builder
public class CompanyWelfareDto {
    private final String code;
    private final String name;
    private final List<FeatureEvidenceDto> evidences;
}
