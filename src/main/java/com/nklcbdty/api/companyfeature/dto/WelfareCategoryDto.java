package com.nklcbdty.api.companyfeature.dto;

import java.util.List;

import com.nklcbdty.api.companyfeature.vo.WelfareCategory;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WelfareCategoryDto {
    private final Long id;
    private final String code;
    private final String name;
    private final List<String> keywords;
    private final int sortOrder;
    private final boolean active;

    public static WelfareCategoryDto from(WelfareCategory c) {
        return WelfareCategoryDto.builder()
            .id(c.getId())
            .code(c.getCode())
            .name(c.getName())
            .keywords(c.getKeywordList())
            .sortOrder(c.getSortOrder())
            .active(c.isActive())
            .build();
    }
}
