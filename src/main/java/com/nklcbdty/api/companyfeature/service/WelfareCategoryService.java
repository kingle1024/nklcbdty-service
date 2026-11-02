package com.nklcbdty.api.companyfeature.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nklcbdty.api.companyfeature.dto.WelfareCategoryDto;
import com.nklcbdty.api.companyfeature.dto.WelfareCategoryRequest;
import com.nklcbdty.api.companyfeature.repository.WelfareCategoryRepository;
import com.nklcbdty.api.companyfeature.vo.WelfareCategory;

/**
 * 복지 카테고리 관리. 키워드를 바꾸면 다음 batch 실행(companyFeatureJob)부터 반영된다.
 * 삭제는 두지 않는다 — 비활성화하면 목록·필터에서 빠지고, 근거 행은 남아 있다가 다시 켜면 바로 보인다.
 */
@Service
public class WelfareCategoryService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,29}$");
    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_KEYWORD_LENGTH = 100;

    private final WelfareCategoryRepository repository;

    public WelfareCategoryService(WelfareCategoryRepository repository) {
        this.repository = repository;
    }

    public List<WelfareCategoryDto> listAll() {
        return repository.findAllByOrderBySortOrderAscIdAsc().stream()
                         .map(WelfareCategoryDto::from)
                         .collect(Collectors.toList());
    }

    public List<WelfareCategoryDto> listActive() {
        return repository.findByActiveTrueOrderBySortOrderAscIdAsc().stream()
                         .map(WelfareCategoryDto::from)
                         .collect(Collectors.toList());
    }

    @Transactional
    public WelfareCategoryDto create(WelfareCategoryRequest request) {
        String code = request.getCode() == null ? "" : request.getCode().trim().toUpperCase();
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("코드는 영문 대문자로 시작하는 2~30자(영문 대문자·숫자·_)여야 합니다.");
        }
        if (repository.existsByCode(code)) {
            throw new IllegalArgumentException("이미 있는 코드입니다: " + code);
        }
        WelfareCategory category = new WelfareCategory();
        category.setCode(code);
        category.setName(validName(request.getName()));
        category.setKeywordList(validKeywords(request.getKeywords()));
        category.setActive(request.getActive() == null || request.getActive());
        category.setSortOrder(repository.findMaxSortOrder() + 1);
        return WelfareCategoryDto.from(repository.save(category));
    }

    @Transactional
    public WelfareCategoryDto update(Long id, WelfareCategoryRequest request) {
        WelfareCategory category = find(id);
        if (request.getName() != null) {
            category.setName(validName(request.getName()));
        }
        if (request.getKeywords() != null) {
            category.setKeywordList(validKeywords(request.getKeywords()));
        }
        if (request.getActive() != null) {
            category.setActive(request.getActive());
        }
        return WelfareCategoryDto.from(repository.save(category));
    }

    /**
     * 화면에서 정한 순서대로 sort_order 를 1부터 다시 매긴다. 목록에 빠진 카테고리는 그 뒤로 기존 순서를 유지한다.
     */
    @Transactional
    public List<WelfareCategoryDto> reorder(List<Long> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            throw new IllegalArgumentException("정렬할 카테고리 id 목록이 비어 있습니다.");
        }
        List<WelfareCategory> all = repository.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, WelfareCategory> byId = all.stream()
            .collect(Collectors.toMap(WelfareCategory::getId, Function.identity()));

        Set<Long> ordered = new LinkedHashSet<>(orderedIds);
        for (Long id : ordered) {
            if (!byId.containsKey(id)) {
                throw new IllegalArgumentException("없는 카테고리입니다: " + id);
            }
        }
        int order = 1;
        for (Long id : ordered) {
            byId.get(id).setSortOrder(order++);
        }
        for (WelfareCategory c : all) {
            if (!ordered.contains(c.getId())) {
                c.setSortOrder(order++);
            }
        }
        repository.saveAll(all);
        return all.stream()
                  .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                  .map(WelfareCategoryDto::from)
                  .collect(Collectors.toList());
    }

    private WelfareCategory find(Long id) {
        return repository.findById(id)
                         .orElseThrow(() -> new IllegalArgumentException("없는 카테고리입니다: " + id));
    }

    private String validName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("이름은 1~" + MAX_NAME_LENGTH + "자여야 합니다.");
        }
        return trimmed;
    }

    /** 앞뒤 공백·빈 값·중복(대소문자 무시)을 걸러낸다. 하나도 안 남으면 매칭할 수 없으므로 거부한다. */
    static List<String> validKeywords(List<String> keywords) {
        if (keywords == null) {
            throw new IllegalArgumentException("키워드를 하나 이상 입력해 주세요.");
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String k : keywords) {
            if (k == null) {
                continue;
            }
            String trimmed = k.replaceAll("\\s+", " ").trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() > MAX_KEYWORD_LENGTH) {
                throw new IllegalArgumentException("키워드는 " + MAX_KEYWORD_LENGTH + "자 이하여야 합니다: " + trimmed);
            }
            unique.putIfAbsent(trimmed.toLowerCase(), trimmed);
        }
        if (unique.isEmpty()) {
            throw new IllegalArgumentException("키워드를 하나 이상 입력해 주세요.");
        }
        return List.copyOf(unique.values());
    }
}
