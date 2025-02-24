package com.nklcbdty.api.crawler.vo;
import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Entity
@Table(name = "job_mst")
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Job_mst {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String companyCd; // 회사구분

    @Column(nullable = false)
    private Long annoId; // 공고 고유 번호

    @Column(nullable = false)
    private String classCdNm; // Tech, Business, ...

    @Column(nullable = false)
    private String empTypeCdNm; // 정규, 비정규

    @Column(nullable = false)
    private String annoSubject; // 채용공고명

    @Column(nullable = false)
    private String subJobCdNm; // 직무 구분

    @Column(nullable = false)
    private String sysCompanyCdNm; // 회사 구분

    @Column(nullable = false)
    private String jobDetailLink; // 공고 URL

}
