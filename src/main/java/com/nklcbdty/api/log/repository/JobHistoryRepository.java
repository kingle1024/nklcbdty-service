package com.nklcbdty.api.log.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nklcbdty.api.log.entity.JobHistoryEntity;

public interface JobHistoryRepository extends JpaRepository<JobHistoryEntity, Long> {
}
