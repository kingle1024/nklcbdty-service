package com.nklcbdty.api.companyfeature.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nklcbdty.api.companyfeature.vo.WelfareCategory;

public interface WelfareCategoryRepository extends JpaRepository<WelfareCategory, Long> {

    List<WelfareCategory> findAllByOrderBySortOrderAscIdAsc();

    List<WelfareCategory> findByActiveTrueOrderBySortOrderAscIdAsc();

    boolean existsByCode(String code);

    /** 새 카테고리는 맨 뒤에 붙인다. */
    @Query("select coalesce(max(c.sortOrder), 0) from WelfareCategory c")
    int findMaxSortOrder();
}
