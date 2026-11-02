package com.nklcbdty.api.companyfeature.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 복지 카테고리 생성/수정 요청. 수정 시 code 는 무시한다(근거 행이 code 로 묶여 있어 바꾸지 않는다).
 * active 가 null 이면 기존 값을 유지한다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WelfareCategoryRequest {
    private String code;
    private String name;
    private List<String> keywords;
    private Boolean active;
}
