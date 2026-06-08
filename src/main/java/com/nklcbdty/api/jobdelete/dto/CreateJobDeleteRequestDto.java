package com.nklcbdty.api.jobdelete.dto;

import lombok.Data;

/** 사용자 삭제요청 본문 */
@Data
public class CreateJobDeleteRequestDto {
    private Long jobId;
    private String reason;
}
