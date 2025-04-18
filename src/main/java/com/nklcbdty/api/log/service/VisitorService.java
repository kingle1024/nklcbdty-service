package com.nklcbdty.api.log.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nklcbdty.api.log.dto.VisitorCountDTO;
import com.nklcbdty.api.log.repository.VisitorRepository;

@Service
public class VisitorService {
    private final VisitorRepository visitorRepository;

    @Autowired
    public VisitorService(VisitorRepository visitorRepository) {
        this.visitorRepository = visitorRepository;
    }

    public List<VisitorCountDTO> getCountsByDate() {
        return visitorRepository.countsByDate();
    }
}
