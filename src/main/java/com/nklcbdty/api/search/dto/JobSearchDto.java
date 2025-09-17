package com.nklcbdty.api.search.dto;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JobSearchDto {
    private String id;
    private String companyCd;
    private Integer annoId;
    private String classCdNm;
    private String empTypeCdNm;
    private String annoSubject;
    private String subJobCdNm;
    private String sysCompanyCdNm;
    private String jobDetailLink;
    private Date endDate;
    private Integer personalHistory;
    private Integer personalHistoryEnd;
    private Date insertDts;

    // 하이라이팅된 필드
    private String annoSubjectHl;
    private String subJobCdNmHl;
}
