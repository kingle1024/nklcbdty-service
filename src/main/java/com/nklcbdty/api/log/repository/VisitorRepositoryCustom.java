package com.nklcbdty.api.log.repository;

import java.util.List;

import com.nklcbdty.api.log.dto.VisitorCountDTO;

public interface VisitorRepositoryCustom {
    List<VisitorCountDTO> countsByDate();
}
