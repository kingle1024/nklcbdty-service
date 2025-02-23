package com.nklcbdty.api.crawler.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nklcbdty.api.crawler.vo.Job_mst;

@Repository
public interface CrawlerRepository extends JpaRepository<Job_mst, Long> {
    boolean existsByAnnoId(Long annoId); // annoId 존재 여부 확인
    Job_mst findByAnnoId(Long annoId);   // annoId로 Job_mst 조회
    List<Job_mst> findAllByAnnoIdIn(List<Long> annoIds); // 모든 annoId 조회
}
