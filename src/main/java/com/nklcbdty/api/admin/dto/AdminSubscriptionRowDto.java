package com.nklcbdty.api.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class AdminSubscriptionRowDto {
    private String userId;
    private String username;
    private String email;
    private List<String> companies;
    private List<String> jobs;
    private Integer careerYear;
    private LocalDateTime latestUpdateDts;
}
