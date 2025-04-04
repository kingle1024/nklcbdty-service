package com.nklcbdty.api.statistics.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nklcbdty.api.log.entity.VisitorEntity;
import com.nklcbdty.api.log.repository.VisitorRepository;

@Service
public class StatisticsService {

    private final VisitorRepository visitorRepository;

    @Autowired
    public StatisticsService(VisitorRepository visitorRepository) {
        this.visitorRepository = visitorRepository;
    }

    public List<VisitorEntity> getVisitorList() {
        return visitorRepository.findAll();
    }
}
