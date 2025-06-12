package com.nklcbdty.api.log.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.nklcbdty.api.log.repository.VisitorRepository;
import com.nklcbdty.api.log.service.LogService;

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
        logService.insertLog();
    }
}
