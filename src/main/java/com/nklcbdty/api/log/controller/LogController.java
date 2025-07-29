package com.nklcbdty.api.log.controller;

import java.time.LocalDateTime;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.log.service.KafkaProducerService;
import com.nklcbdty.api.log.dto.JobHistoryLog;
import com.nklcbdty.api.log.service.LogService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/log")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;
    private final KafkaProducerService kafkaProducerService;

    @GetMapping("/job_history")
    public void job_history(
        @RequestParam String anno_id,
        @RequestParam String anno_subject,
        HttpServletRequest request) {

        log.info("Job history request received for annoId: {}, sending to Kafka.", anno_id);
        kafkaProducerService.sendJobHistoryLog(request, JobHistoryLog.builder()
                .annoId(anno_id)
                .annoSubject(anno_subject)
                .build());
    }
}
