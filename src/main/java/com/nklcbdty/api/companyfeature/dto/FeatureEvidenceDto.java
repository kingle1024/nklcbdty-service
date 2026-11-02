package com.nklcbdty.api.companyfeature.dto;

import com.nklcbdty.api.companyfeature.vo.CompanyFeature;

import lombok.Builder;
import lombok.Getter;

/** 어느 공고의 어느 문구에서 그 특징을 찾았는지. */
@Getter
@Builder
public class FeatureEvidenceDto {
    private final Long jobId;
    private final String annoSubject;
    private final String sysCompanyNm;
    private final String jobDetailLink;
    private final String matchedKeyword;
    private final String snippet;

    public static FeatureEvidenceDto from(CompanyFeature f) {
        return FeatureEvidenceDto.builder()
            .jobId(f.getJobId())
            .annoSubject(f.getAnnoSubject())
            .sysCompanyNm(f.getSysCompanyNm())
            .jobDetailLink(f.getJobDetailLink())
            .matchedKeyword(f.getMatchedKeyword())
            .snippet(f.getSnippet())
            .build();
    }
}
