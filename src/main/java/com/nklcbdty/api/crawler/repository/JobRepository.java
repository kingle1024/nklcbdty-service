package com.nklcbdty.api.crawler.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nklcbdty.api.crawler.vo.Job_mst;

@Repository
public interface JobRepository extends JpaRepository<Job_mst, Long> {
    List<Job_mst> findAllByCompanyCd(String company);
}
