package com.nklcbdty.api.log.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobHistoryLog {
    private String annoId;
    private String annoSubject;
    private String insertIp;
}
