package com.nklcbdty.api.crawler.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.nklcbdty.api.crawler.vo.CategoryMst;

@Repository
public interface CategoryRepository extends JpaRepository<CategoryMst, Long> {

}
