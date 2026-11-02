package com.nklcbdty.api.companyfeature.vo;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 채용공고 한 건에서 찾아낸 회사 특징(근무형태 태그 / 복지 카테고리)과 그 근거.
 *
 * <p>batch(nklcbdty-batch 의 companyFeatureJob)가 공고 본문을 키워드 매칭해 쌓고, 이 서비스는 읽기만 한다.
 * 회사가 어떤 특징을 가졌는지는 "그 특징의 근거 공고가 한 건 이상 있는가"로 정한다. 그래서 행은
 * 공고 단위(job_id)이고, 어느 공고의 어느 문구(snippet)에서 나왔는지 함께 남긴다.</p>
 */
@Entity
@Table(name = "company_feature")
@Data
public class CompanyFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String companyCd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeatureType featureType;

    @Column(nullable = false, length = 30)
    private String featureCode;

    @Column(nullable = false)
    private Long jobId;

    // 공고 스냅샷 — 공고가 정리돼도 근거를 볼 수 있게
    @Column(length = 500)
    private String annoSubject;

    @Column(length = 255)
    private String sysCompanyNm;

    @Column(length = 1000)
    private String jobDetailLink;

    /** 매칭된 키워드(예: "재택") */
    @Column(nullable = false, length = 100)
    private String matchedKeyword;

    /** 키워드 앞뒤 본문 발췌 */
    @Column(nullable = false, length = 500)
    private String snippet;

    @Column(nullable = false)
    private LocalDateTime detectedDts;
}
