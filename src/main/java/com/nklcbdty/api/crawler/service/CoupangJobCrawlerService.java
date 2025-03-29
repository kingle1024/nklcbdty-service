package com.nklcbdty.api.crawler.service;

import java.util.ArrayList;
import java.util.List;


import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nklcbdty.api.crawler.common.ApiException;
import com.nklcbdty.api.crawler.common.CrawlerCommonService;
import com.nklcbdty.api.crawler.interfaces.JobCrawler;
import com.nklcbdty.api.crawler.vo.Job_mst;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CoupangJobCrawlerService implements JobCrawler{
	
    private final CrawlerCommonService crawlerCommonService;
    private String apiUrl;

    @Autowired
	public CoupangJobCrawlerService(CrawlerCommonService crawlerCommonService) {
		this.crawlerCommonService = crawlerCommonService;
		this.apiUrl = getApiUrl();
	}

    private String getApiUrl() {
    	return "https://www.coupang.jobs/kr/jobs/?page=1";
    }

    
    @Override
	public List<Job_mst> crawlJobs() {
		List<Job_mst> resList;
		String formattedDate = crawlerCommonService.formatCurrentTime(); 
		log.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> {}의 크롤러가 {}로 시작됩니다.", this.getClass(), formattedDate);
		
		int totalCnt = getCoupangTotalListCnt(apiUrl);
		resList = coupangParseHtmlData(apiUrl);
		crawlerCommonService.insertJobMst(resList);

        int pagesize = 20;
        double totalLoopCnt = totalCnt % pagesize
            == 0 ? (double)(totalCnt / pagesize) : Math.ceil((double)totalCnt / pagesize);

		for (int i = 1; i <= (int)totalLoopCnt; i++) {
			apiUrl = "https://www.coupang.jobs/kr/jobs/?page="+i+"#results";
			resList = coupangParseHtmlData(apiUrl);
			crawlerCommonService.insertJobMst(resList);
	
		    try {
		    	// 1초에서 2초 사이의 랜덤한 시간(1000ms ~ 2000ms) 동안 대기
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
     * <p>서버통신 리스폰스 데이터가 HTML데이터인경우 Jsoup으로 파싱한다.</p>
     * @author DavieLee
     * @return Document
     * @param String apiUrl
     * 
     * */
    private List<Job_mst> coupangParseHtmlData(String apiUrl) {
		List<Job_mst> resList = new ArrayList<>();
		
        try {
        	Document doc = Jsoup.connect(apiUrl)
                .header("User-Agent", "PostmanRuntime/7.43.2") // User-Agent 설정
                .get();
        	Elements root = doc.select("main#content div#js-job-search-results .card.card-job");
        	
        	for (Element cardJobRoot : root) {
				// 근무지
				String workplace = cardJobRoot.select(".list-inline.job-meta > li").text();
				if (!"서울".equals(workplace)) {
					continue;
				}

        		Job_mst job_mst = new Job_mst();
        		// 상세공고 url
        		String jobDetailLink = cardJobRoot.select("a.stretched-link.js-view-job").attr("href");
        		// 공고명
        		String annoSubject = cardJobRoot.select("a.stretched-link.js-view-job").text();
        		String rowAnnoId = cardJobRoot.select(".card-job-actions.js-job").attr("data-id");
        		// 공고번호
        		Long annoId = Long.parseLong(rowAnnoId);

        		job_mst.setJobDetailLink("https://www.coupang.jobs".concat(jobDetailLink));
        		job_mst.setAnnoSubject(annoSubject);
        		job_mst.setAnnoId(annoId);
        		job_mst.setWorkplace(workplace);
        		resList.add(job_mst);
        	}
        	
        } catch (Exception e) {
            log.error("Error occurred while fetching API response: {}", e.getMessage(), e);
            throw new ApiException("Failed to fetch API response");  // 커스텀 예외 던지기
        }
        return resList;
    }
    
    
    /**
     * <p>모든 공고를 파싱하기 위해 필요한 총 건수를 반환한다.</p>
     * 
     * */
    private int getCoupangTotalListCnt(String apiUrl) {
        String strTotalCnt;

    	try {
            Document doc = Jsoup.connect(apiUrl)
                                .header("User-Agent", "PostmanRuntime/7.43.2") // User-Agent 설정
                                .get(); // GET 요청

    		// 총건수 파싱하기.
    		strTotalCnt = doc.select("main#content div#js-job-search-results").attr("data-results");
    		
        } catch (Exception e) {
            log.error("Error occurred while fetching API response: {}", e.getMessage(), e);
            throw new ApiException("Failed to Jsoup response");  // 커스텀 예외 던지기
        }
		
		return Integer.parseInt(strTotalCnt);
    }
}
