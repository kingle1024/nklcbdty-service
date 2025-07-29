package com.nklcbdty.api.log.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.nklcbdty.api.log.dto.JobHistoryLog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerService {

    private final LogService logService;

    @KafkaListener(topics = "job-history-topic", groupId = "job-log-group", containerFactory = "kafkaListenerContainerFactory")
    public void consumeJobHistoryLog(JobHistoryLog logEntry) {
        log.info("Kafka: Job History Log 수신 성공. Log: {}", logEntry);

        try {
            logService.insertJobHistory(logEntry);
            log.info("Job History Log DB 저장 성공: {}", logEntry.getAnnoId());
        } catch (Exception e) {
            log.error("Job History Log DB 저장 실패: {}", logEntry, e);
        }
    }
}
