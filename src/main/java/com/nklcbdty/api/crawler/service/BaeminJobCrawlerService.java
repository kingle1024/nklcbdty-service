package com.nklcbdty.api.crawler.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.nklcbdty.api.crawler.common.CrawlerCommonService;
import com.nklcbdty.api.crawler.common.JobEnums;
import com.nklcbdty.api.crawler.interfaces.JobCrawler;
import com.nklcbdty.api.crawler.vo.Job_mst;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class BaeminJobCrawlerService implements JobCrawler{
	
	private final CrawlerCommonService crawlerCommonService;
    private String apiUrl;
    
    @Autowired
    public BaeminJobCrawlerService(CrawlerCommonService crawlerCommonService) {
		this.crawlerCommonService = crawlerCommonService;
		this.apiUrl = getApiUrl();
	}
    
    private String getApiUrl() {
    	return "https://career.woowahan.com/w1/recruits?category=jobGroupCodes%3ABA005001&recruitCampaignSeq=0&jobGroupCodes=BA005001&page=0&size=21&sort=updateDate%";
    }
    
    
	@Override
    @Async
	public CompletableFuture<List<Job_mst>> crawlJobs() {
		List<Job_mst> list = new ArrayList<Job_mst>();
		try {
			
			String formattedDate = crawlerCommonService.formatCurrentTime(); 
			log.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> {}의 크롤러가 {}로 시작됩니다.", this.getClass(), formattedDate);
			String resultJSONStr = crawlerCommonService.fetchApiResponse(apiUrl);
			
			JSONObject jsonObj = new JSONObject(resultJSONStr);
			JSONArray jsonArr = jsonObj.getJSONObject("data").getJSONArray("list");
			
			for(int i = 0; i < jsonArr.length(); i++) {
				Job_mst job_mst = new Job_mst();
				
				JSONObject item = jsonArr.getJSONObject(i);
				String annoId = item.get("recruitSeq").toString();
				String recruitNumber = item.get("recruitNumber").toString();
				String annoSubject = item.get("recruitName").toString();
				
				JSONObject employmentType = item.getJSONObject("employmentType");
				String empTypeCdNm = baeminConvertCodeToEmpType(employmentType);
				
                job_mst.setAnnoId(annoId);
				job_mst.setJobDetailLink("https://career.woowahan.com/recruitment/" + recruitNumber + "/detail?jobCodes=&employmentTypeCodes=&serviceSectionCodes=&careerPeriod=&category=jobGroupCodes%3ABA005001");
				job_mst.setEmpTypeCdNm(empTypeCdNm);
				job_mst.setAnnoSubject(annoSubject);
                job_mst.setStartDate(item.getString("recruitOpenDate"));
                job_mst.setEndDate(item.getString("recruitCloseDate"));
                Object from = item.get("careerRestrictionMinYears");
                if (from instanceof Integer) {
                    long fromLong = ((Integer)from).longValue();
                    if (fromLong == -1) {
                        fromLong = 0; // -1은 경력제한 없음으로 간주
                    }
                    job_mst.setPersonalHistory(fromLong);
                }
                Object to = item.get("careerRestrictionMaxYears");
                if (to instanceof Integer) {
                    long endLong = ((Integer)to).longValue();
                    if (endLong == -1) {
                        endLong = 0; // -1은 경력제한 없음으로 간주
                    }
                    job_mst.setPersonalHistoryEnd(endLong);
                }
				list.add(job_mst);
			}

            for (Job_mst item : list) {
                if (item.getAnnoSubject().contains("프론트엔드 개발자")) {
                    item.setSubJobCdNm(JobEnums.FrontEnd.getTitle());
                } else if (item.getAnnoSubject().contains("백엔드 개발자") ||
                    item.getAnnoSubject().contains("서버 개발자") ||
                    item.getAnnoSubject().contains("백엔드 시스템 개발자") ||
                    item.getAnnoSubject().contains("SRE 개발자")
                ) {
                    item.setSubJobCdNm(JobEnums.BackEnd.getTitle());
                } else if (item.getAnnoSubject().contains("데이터분석가") ||
                    item.getAnnoSubject().contains("데이터과학자")
                ) {
                    item.setSubJobCdNm(JobEnums.DataAnalyst.getTitle());
                } else if (item.getAnnoSubject().contains("데이터엔지니어") ||
                    item.getAnnoSubject().contains("데이터베이스엔지니어")
                ) {
                    item.setSubJobCdNm(JobEnums.DataEngineering.getTitle());
                } else if (item.getAnnoSubject().contains("QA Engineer") ||
                    item.getAnnoSubject().contains("Test Engineer")
                ) {
                    item.setSubJobCdNm(JobEnums.QA.getTitle());
                } else if (item.getAnnoSubject().contains("보안 시스템 및 솔루션 운영자")) {
                    item.setSubJobCdNm(JobEnums.Security.getTitle());
                } else if (item.getAnnoSubject().contains("ML엔지니어")) {
                    item.setSubJobCdNm(JobEnums.ML.getTitle());
                }

                if (item.getSysCompanyCdNm() == null) {
                    item.setSysCompanyCdNm("배달의민족");
                }
            }

		} catch (Exception e) {
            log.error("Error occurred while crawling jobs: {}", e.getMessage(), e);
        }
        return CompletableFuture.completedFuture(crawlerCommonService.getNotSaveJobItem("BAEMIN", list));
	}
	
	/**
	 * <p>배달의민족 직무형태 코드를 이름으로 바꿔준다.
	 * recruitItemCode : BA002002 (기간제-추정), 
	 *					 BA002001 (정규직-추정),
	 *					 else (인턴)</p>
	 * @author DavieLee
	 * */
	private String baeminConvertCodeToEmpType(JSONObject employmentType) {
		String resStr = "";
		String recruitItemCode = employmentType.get("recruitItemCode").toString();
		
		if (recruitItemCode.isBlank() || recruitItemCode == null) return resStr;
		
		switch (recruitItemCode.toUpperCase()) {
		case "BA002001":
			resStr = "정규";
			break;
		case "BA002002" :
			resStr = "기간제";
			break;
		default:
			resStr = "인턴";
			break;
		}	
		return resStr;
	}
}
