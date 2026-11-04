package com.nklcbdty.api.companyfeature.vo;

/** 복지를 여러 개 골랐을 때 묶는 방식. */
public enum WelfareMatch {
    /** 고른 복지를 모두 가진 회사만 */
    ALL,
    /** 고른 복지 중 하나라도 가진 회사 */
    ANY,
    ;

    /** 대소문자 무시, 비어 있으면 ALL. 없는 값이면 IllegalArgumentException(→ 400). */
    public static WelfareMatch fromCode(String code) {
        if (code == null || code.isBlank()) {
            return ALL;
        }
        for (WelfareMatch match : values()) {
            if (match.name().equalsIgnoreCase(code.trim())) {
                return match;
            }
        }
        throw new IllegalArgumentException("알 수 없는 복지 조건입니다: " + code);
    }
}
