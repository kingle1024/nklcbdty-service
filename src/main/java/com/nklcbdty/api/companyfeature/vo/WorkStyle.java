package com.nklcbdty.api.companyfeature.vo;

import lombok.Getter;

/**
 * 회사 근무형태 태그.
 *
 * <p>회사마다 boolean 컬럼(remote 등)을 두지 않고, company_feature 에 (WORK_STYLE, 코드) 행을 쌓는
 * 태그 집합으로 저장한다. 근무형태가 늘면(예: FLEXIBLE 유연근무제) 여기 상수와 batch 의 키워드 규칙만
 * 추가하면 되고 스키마는 그대로다.</p>
 *
 * <p>코드값은 batch(nklcbdty-batch 의 WorkStyleRule)가 company_feature.feature_code 에 쓰는 값과 같아야 한다.</p>
 */
@Getter
public enum WorkStyle {
    REMOTE("재택근무"),
    ;

    private final String label;

    WorkStyle(String label) {
        this.label = label;
    }

    /** 대소문자 무시. 없는 코드면 IllegalArgumentException(→ 400). */
    public static WorkStyle fromCode(String code) {
        for (WorkStyle style : values()) {
            if (style.name().equalsIgnoreCase(code)) {
                return style;
            }
        }
        throw new IllegalArgumentException("알 수 없는 근무형태입니다: " + code);
    }
}
