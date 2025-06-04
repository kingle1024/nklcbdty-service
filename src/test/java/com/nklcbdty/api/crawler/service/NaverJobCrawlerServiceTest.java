package com.nklcbdty.api.crawler.service;

import com.nklcbdty.api.crawler.vo.Job_mst;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class NaverJobCrawlerServiceTest {

    @InjectMocks
    private NaverJobCrawlerService naverJobCrawlerService;

    @Mock
    private HttpURLConnection mockConnection;

    @BeforeEach
    public void setUp() throws Exception {
        // Mockito 초기화
        MockitoAnnotations.openMocks(this);

        // 모의 API 응답 생성
        String mockApiResponse = getMockApiResponse();
        InputStream mockInputStream = new ByteArrayInputStream(mockApiResponse.getBytes());

        // mockConnection.getInputStream()이 mockInputStream을 반환하도록 설정
        when(mockConnection.getInputStream()).thenReturn(mockInputStream);

        // createConnection 메서드를 오버라이드하여 mockConnection을 사용
        // doReturn(mockConnection).when(naverJobCrawlerService).createConnection(any(URL.class));
    }

    private String getMockApiResponse() {
        // 모의 API 응답 생성
        JSONObject jsonResponse = new JSONObject();
        JSONArray jobList = new JSONArray();

        // 샘플 Job 객체 추가
        JSONObject job1 = new JSONObject();
        job1.put("title", "개발자");
        jobList.put(job1);

        JSONObject job2 = new JSONObject();
        job2.put("title", "디자이너");
        jobList.put(job2);

        jsonResponse.put("list", jobList);
        return jsonResponse.toString();
    }

    @Test
    public void testCrawlJobs() {

    }
}
