package com.nklcbdty.api.companyfeature.vo;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 복지 카테고리(식대, 장비지원 등). 관리자가 백오피스에서 추가·수정·정렬·비활성화한다.
 *
 * <p>keywords 는 batch 가 채용공고 본문에서 찾을 문구 목록이다. 한 줄에 하나씩 저장한다
 * (키워드 안에 쉼표가 들어갈 수 있어 줄바꿈을 구분자로 쓴다).</p>
 */
@Entity
@Table(name = "welfare_category")
@Data
public class WelfareCategory {

    static final String KEYWORD_SEPARATOR = "\n";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 영문 대문자 코드. company_feature.feature_code 로 쓰이므로 만든 뒤에는 바꾸지 않는다. */
    @Column(nullable = false, length = 30, unique = true)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String keywords;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    private LocalDateTime insertDts;

    @UpdateTimestamp
    private LocalDateTime updateDts;

    public List<String> getKeywordList() {
        if (keywords == null || keywords.isBlank()) {
            return List.of();
        }
        return Arrays.stream(keywords.split(KEYWORD_SEPARATOR))
                     .map(String::trim)
                     .filter(k -> !k.isEmpty())
                     .collect(Collectors.toList());
    }

    public void setKeywordList(List<String> keywordList) {
        this.keywords = String.join(KEYWORD_SEPARATOR, keywordList);
    }
}
