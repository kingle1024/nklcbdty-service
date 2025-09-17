package com.nklcbdty.api.search.model;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(indexName = "job_mst")
@NoArgsConstructor
@Getter
@Setter
public class JobMstDocument {
    @Id
    private String id;

    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "korean", searchAnalyzer = "korean"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword, normalizer = "lowercase_normalizer")
        }
    )
    private String annoSubject;

    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "korean", searchAnalyzer = "korean"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword, normalizer = "lowercase_normalizer")
        }
    )
    private String subJobCdNm;

    @Field(type = FieldType.Keyword)
    private String companyCd;

    @Field(type = FieldType.Integer)
    private Integer annoId;

    @Field(type = FieldType.Keyword)
    private String classCdNm;

    @Field(type = FieldType.Keyword)
    private String empTypeCdNm;

    @Field(type = FieldType.Keyword)
    private String sysCompanyCdNm;

    @Field(type = FieldType.Keyword)
    private String jobDetailLink;

    @Field(type = FieldType.Date)
    private Date endDate;

    @Field(type = FieldType.Integer)
    private Integer personalHistory;

    @Field(type = FieldType.Integer)
    private Integer personalHistoryEnd;

    @Field(type = FieldType.Date) // 날짜는 Date 타입
    private Date insertDts;

    @Builder
    public JobMstDocument(String id, String annoSubject, String subJobCdNm,
                          String companyCd, Integer annoId, String classCdNm, String empTypeCdNm,
                          String sysCompanyCdNm, String jobDetailLink, Date endDate,
                          Integer personalHistory, Integer personalHistoryEnd, Date insertDts) {
        this.id = id;
        this.annoSubject = annoSubject;
        this.subJobCdNm = subJobCdNm;
        this.companyCd = companyCd;
        this.annoId = annoId;
        this.classCdNm = classCdNm;
        this.empTypeCdNm = empTypeCdNm;
        this.sysCompanyCdNm = sysCompanyCdNm;
        this.jobDetailLink = jobDetailLink;
        this.endDate = endDate;
        this.personalHistory = personalHistory;
        this.personalHistoryEnd = personalHistoryEnd;
        this.insertDts = insertDts;
    }
}
