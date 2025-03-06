package com.nklcbdty.api.crawler.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nklcbdty.api.crawler.common.ApiException;
import com.nklcbdty.api.crawler.common.CrawlerCommonService;
import com.nklcbdty.api.crawler.interfaces.JobCrawler;
import com.nklcbdty.api.crawler.repository.CrawlerRepository;
import com.nklcbdty.api.crawler.vo.Job_mst;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CoupangJobCrawlerService implements JobCrawler{
	
	private final CrawlerRepository crawlerRepository;
    private final CrawlerCommonService crawlerCommonService;
    private String apiUrl;
    private final int pagesize = 20;
    private HttpServletRequest request;
	
    @Autowired
	public CoupangJobCrawlerService(CrawlerRepository crawlerRepository, CrawlerCommonService crawlerCommonService
				, HttpServletRequest request) {
		this.crawlerRepository = crawlerRepository;
		this.crawlerCommonService = crawlerCommonService;
		this.apiUrl = getApiUrl();

	}

    private String getApiUrl() {
    	return "https://www.coupang.jobs/kr/jobs/?page=1#results";
    }

    
    @Override
	public List<Job_mst> crawlJobs() {
		List<Job_mst> resList = new ArrayList<>();
		
		String formattedDate = formatCurrentTime(); 
		log.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> {}의 크롤러가 {}로 시작됩니다.", this.getClass(), formattedDate);
		
		int totalCnt = crawlerCommonService.getTotalListCnt(apiUrl);
		resList = crawlerCommonService.parseHtmlData(apiUrl);
		
		System.out.println(">>>>>>>>>>>>>>>>>>>>>>job_mst결과는!!??" + resList.toString());
		
		double totalLoopCnt = totalCnt % pagesize == 0 ? (double)(totalCnt / pagesize) : Math.ceil((double)(totalCnt / pagesize));

		for (int i = 1; i <= (int)totalLoopCnt; i++) {
			apiUrl = "https://www.coupang.jobs/kr/jobs/?page="+i+"#results";
			resList = crawlerCommonService.parseHtmlData(apiUrl);
			
			// TODO HTML 파싱 로직 common소스 말고, 이곳의 메소드로 옮겨놓기
			// TODO Repository 저장로직하기.
			// TODO for문안 HTML파싱로직 부분도 봐야함...
			
			// 1초에서 2초 사이의 랜덤한 시간(1000ms ~ 2000ms) 동안 대기
		    try {
		        int sleepTime = 1000 + (int)(Math.random() * 1000); // 1000ms ~ 2000ms
		        Thread.sleep(sleepTime);
		    } catch (InterruptedException e) {
		    	log.error("크롤링 서버에 요청 중 Interrupted오류가 발생했습니다. {} ", e.getMessage());
		        Thread.currentThread().interrupt(); // InterruptedException 발생 시 스레드 상태 복원
		        // 예외 처리 로직 추가 가능
		        throw new ApiException(e.getMessage(), e);
		    }
		}
		
		
		return resList;
		
	}
	
	/**
	 * <p>밀리세컨드로 된 데이터를 yyyy-MM-dd HH:mm:ss format String으로 변환한다. </p>
	 * */
	private String formatCurrentTime() {
		long currentTimeMillis = System.currentTimeMillis();
		
        // 밀리초를 LocalDateTime으로 변환
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(currentTimeMillis), ZoneId.systemDefault());
        // LocalDateTime today = LocalDateTime.now();
        
        // 원하는 형식으로 포맷팅
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedDate = dateTime.format(formatter);
        // String todayFormatter = today.format(formatter);
        
        return formattedDate;
	}
}
