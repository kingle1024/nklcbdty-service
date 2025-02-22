package com.nklcbdty.api.crawler.vo;
import javax.persistence.*;

@Entity
@Table(name = "job_mst")
public class Job_mst {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String classCdNm;

    @Column(nullable = false)
    private String empTypeCdNm;

    @Column(nullable = false)
    private String subjectNm;

    @Column(nullable = false)
    private String companyCd;

}
