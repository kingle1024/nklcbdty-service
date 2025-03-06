package com.nklcbdty.api.crawler.common;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import com.nklcbdty.api.crawler.vo.Job_mst;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CrawlerCommonService {

    public String fetchApiResponse(String apiUrl) {

        try {
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setRequestMethod("GET");

            StringBuilder response = new StringBuilder();
            try (BufferedReader in = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
            }

            return response.toString();
        } catch (Exception e) {
            log.error("Error occurred while fetching API response: {}", e.getMessage(), e);
            throw new ApiException("Failed to fetch API response");  // 커스텀 예외 던지기
        }
    }
    
    /**
     * <p>서버통신 리스폰스 데이터가 HTML데이터인경우 Jsoup으로 파싱한다.</p>
     * @author DavieLee
     * @return Document
     * @param String apiUrl
     * 
     * */
    public List<Job_mst> parseHtmlData(String apiUrl) {
		List<Job_mst> resList = new ArrayList<>();
		
        try {
        	Document doc = Jsoup.connect(apiUrl).get();
        	Elements root = doc.select("main#content div#js-job-search-results .card.card-job");
        	
        	for (Element cardJobRoot : root) {
        		Job_mst job_mst = new Job_mst();
        		// 상세공고 url
        		String jobDetailLink = cardJobRoot.select("a.stretched-link.js-view-job").attr("href");
        		// 공고명
        		String annoSubject = cardJobRoot.select("a.stretched-link.js-view-job").text();
        		String rowAnnoId = cardJobRoot.select(".card-job-actions.js-job").attr("data-id");
        		// 공고번호
        		Long annoId = Long.parseLong(rowAnnoId);
        		// 근무지
        		String workplace = cardJobRoot.select(".list-inline.job-meta > li").text();

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
    public int getTotalListCnt(String apiUrl) {
    	int resultCnt = 0;
    	String strTotalCnt = "";
		
    	try {
    		Document doc = Jsoup.connect(apiUrl).get();
//    		Elements root = doc.select("main#content p.job-count");
    		// 총건수 파싱하기.
//    		strTotalCnt = root.select("strong:nth-of-type(3)").text();
    		strTotalCnt = doc.select("main#content div#js-job-search-results").attr("data-results");
    		
        } catch (Exception e) {
            log.error("Error occurred while fetching API response: {}", e.getMessage(), e);
            throw new ApiException("Failed to Jsoup response");  // 커스텀 예외 던지기
        }
		
		return resultCnt = Integer.parseInt(strTotalCnt);
    }
}
