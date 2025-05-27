package com.nklcbdty.api.statistics.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.log.service.VisitorService;
import com.nklcbdty.api.statistics.service.StatisticsService;

@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;
    private final VisitorService visitorService;

    @Autowired
    public StatisticsController(StatisticsService statisticsService, VisitorService visitorService) {
        this.statisticsService = statisticsService;
        this.visitorService = visitorService;
    }

    @GetMapping("")
    public ResponseEntity<?> visitors() {
        try {
            return ResponseEntity.ok(statisticsService.getVisitorList());
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }

    @GetMapping("/count-by-date")
    public ResponseEntity<?> countByDate() {
        try {
            return ResponseEntity.ok(visitorService.getCountsByDate());
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }
}
