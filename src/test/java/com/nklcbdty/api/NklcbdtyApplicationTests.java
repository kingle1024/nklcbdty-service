package com.nklcbdty.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;

import com.nklcbdty.common.crawler.repository.CrawlerRepository;
import com.nklcbdty.api.crawler.service.NaverJobCrawlerService;
import com.nklcbdty.common.vo.Job_mst;

@ExtendWith(MockitoExtension.class)
@SpringBootTest
// 앱 전체 컨텍스트를 띄우는 테스트라 운영 DB/Redis/OAuth 자격증명이 환경변수로 있어야
// 통과한다. 그게 없는 환경(로컬·CI)에서는 항상 실패해서, 이 한 건 때문에
// ./gradlew test 를 CI 에 걸지 못하고 있었다. 자격증명이 있을 때만 돌린다.
@EnabledIfEnvironmentVariable(named = "DATASOURCE_URL", matches = ".+")
class NklcbdtyApplicationTests {

    @Mock
    CrawlerRepository crawlerRepository;

    @InjectMocks
    NaverJobCrawlerService service;

    @Mock
    HttpURLConnection connection; // HttpURLConnection 모킹

    @Test
    void contextLoads() throws Exception {
        // Given: 예시 Job_mst 객체 설정
        List<Job_mst> mockJobs = new ArrayList<>();
        Job_mst job = new Job_mst();
        job.setAnnoSubject("Test Subject");
        job.setSubJobCdNm("Test Code");
        mockJobs.add(job);

        // When: saveAll 메서드가 호출될 때 mockJobs를 반환하도록 설정
        // when(crawlerRepository.saveAll(any())).thenReturn(mockJobs);

        // Mocking HttpURLConnection behavior
        // when(connection.getInputStream()).thenReturn(new ByteArrayInputStream("{\"list\": []}".getBytes(StandardCharsets.UTF_8)));

        // Mocking createConnection method
        // when(service.createConnection(any(URL.class))).thenReturn(connection);

        // Then: crawlJobs 메서드 실행
        // List<Job_mst> result = (List<Job_mst>)service.crawlJobs();

        // Verify: 결과 확인 및 메서드 호출 검증
        // verify(crawlerRepository).saveAll(any());
        // assertNotNull(result);
    }
}
