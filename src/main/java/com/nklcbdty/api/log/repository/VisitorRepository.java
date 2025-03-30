package com.nklcbdty.api.log.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nklcbdty.api.log.entity.VisitorEntity;

public interface VisitorRepository extends JpaRepository<VisitorEntity, Long> {
}
