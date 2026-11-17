package com.nklcbdty.api.log.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.nklcbdty.api.log.repository.VisitorRepository;
import com.nklcbdty.api.log.service.LogService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aspect
@Component
public class LogAspect {

    private final LogService logService;

    @Autowired
    public LogAspect(LogService logService) {
        this.logService = logService;
    }

    @Before("execution (* com.nklcbdty.api.crawler.controller.JobController.*(..)) " +
            "&& !execution (* com.nklcbdty.api.crawler.controller.JobController.cralwer(..))")
    public void before(JoinPoint joinPoint) {
        // 방문 로그는 부가 기능이다. 기록이 실패해도(DB 컬럼 누락 등) 공고 목록 응답까지 죽이면 안 된다.
        try {
            logService.insertLog();
        } catch (Exception e) {
            log.warn("방문 로그 기록 실패: {}", e.getMessage());
        }
    }
}
