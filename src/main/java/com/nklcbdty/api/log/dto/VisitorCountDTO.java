package com.nklcbdty.api.log.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class VisitorCountDTO {
    private LocalDate date;
    private long count;
}
