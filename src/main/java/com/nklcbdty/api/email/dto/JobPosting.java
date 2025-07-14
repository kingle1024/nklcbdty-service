package com.nklcbdty.api.email.dto;

import jakarta.persistence.Column;
import lombok.Data;

@Data
public class JobPosting {
    private String title;
    private String url;
    private String company;
    private String jobType;
    private String startDate; // 시작일
    private String endDate;   // 마감일
    private long personalHistory;
    private long personalHistoryEnd;
}
