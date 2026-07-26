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
import com.nklcbdty.common.vo.Job_mst;

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
    	// 끝에 떨어진 '%'(sort=updateDate%) 때문에 URL escape 가 깨져 호출이 예외로 빠지며 크롤이 실패했다.
    	// sort 파라미터 제거(기본 정렬). 목록 정렬은 조회/메일 단계에서 별도 처리한다.
    	return "https://career.woowahan.com/w1/recruits?category=jobGroupCodes%3ABA005001&recruitCampaignSeq=0&jobGroupCodes=BA005001&page=0&size=21";
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
			JSONObject data = jsonObj.optJSONObject("data");
			JSONArray jsonArr = data == null ? null : data.optJSONArray("list");

			if (jsonArr == null) {
				// 응답 envelope 변경/차단 페이지 등으로 list 를 못 찾은 경우. 통째로 실패하지 않고 로그만 남긴다.
				log.error("배민 크롤 응답에서 data.list 를 찾지 못함. 응답 앞부분: {}",
					resultJSONStr == null ? "null" : resultJSONStr.substring(0, Math.min(300, resultJSONStr.length())));
				return CompletableFuture.completedFuture(crawlerCommonService.getNotSaveJobItem("BAEMIN", list));
			}

			for(int i = 0; i < jsonArr.length(); i++) {
				try {
					JSONObject item = jsonArr.getJSONObject(i);

					// 우아한형제들 API 는 공고 상태(예약/상시/드래프트 등)에 따라 일부 필드를 null 로 내려준다.
					// getString/getJSONObject 는 null 에서 예외를 던져 루프 전체를 중단시키므로 opt* 로 안전하게 읽는다.
					String annoId = item.opt("recruitSeq") == null ? null : item.get("recruitSeq").toString();
					String recruitNumber = item.optString("recruitNumber", "");
					String annoSubject = item.optString("recruitName", "");

					if (annoId == null || annoId.isBlank() || annoSubject.isBlank()) {
						log.warn("배민 공고 필수값 누락으로 건너뜀 (index={}, recruitSeq={}, recruitName='{}')", i, annoId, annoSubject);
						continue;
					}

					Job_mst job_mst = new Job_mst();

					JSONObject employmentType = item.optJSONObject("employmentType");
					String empTypeCdNm = baeminConvertCodeToEmpType(employmentType);

	                job_mst.setAnnoId(annoId);
					job_mst.setJobDetailLink("https://career.woowahan.com/recruitment/" + recruitNumber + "/detail?jobCodes=&employmentTypeCodes=&serviceSectionCodes=&careerPeriod=&category=jobGroupCodes%3ABA005001");
					job_mst.setEmpTypeCdNm(empTypeCdNm);
					job_mst.setAnnoSubject(annoSubject);
	                job_mst.setStartDate(item.optString("recruitOpenDate", null));
	                job_mst.setEndDate(item.optString("recruitCloseDate", null));
	                job_mst.setPersonalHistory(normalizeCareerYears(item.opt("careerRestrictionMinYears")));
	                job_mst.setPersonalHistoryEnd(normalizeCareerYears(item.opt("careerRestrictionMaxYears")));
					list.add(job_mst);
				} catch (Exception itemEx) {
					// 한 공고 파싱 실패가 전체 크롤을 무너뜨리지 않도록 항목 단위로 격리한다.
					log.error("배민 공고 파싱 실패 (index={}): {}", i, itemEx.getMessage(), itemEx);
				}
			}

            for (Job_mst item : list) {
                // 공고명 형식이 "백엔드 개발자" → "Server(배차시스템)", "데이터엔지니어링(마케팅플랫폼)" 처럼
                // 바뀌어 기존 키워드가 거의 안 맞았다. 공백 제거 + 대문자화 후 포함 검사로 완화한다.
                // (최종 분류는 컨트롤러의 Gemini 보정이 담당하며, 이건 보조/폴백 규칙이다.)
                final String subject = item.getAnnoSubject();
                final String norm = subject.replace(" ", "").toUpperCase();

                if (norm.contains("프론트엔드") || norm.contains("FRONTEND") || norm.contains("FRONT-END")) {
                    item.setSubJobCdNm(JobEnums.FrontEnd.getTitle());
                } else if (norm.contains("백엔드") || norm.contains("BACKEND") || norm.contains("BACK-END") ||
                    norm.contains("서버") || norm.contains("SERVER") || norm.contains("SRE") ||
                    norm.contains("플랫폼엔지니어") || norm.contains("플랫폼엔지니어링")
                ) {
                    item.setSubJobCdNm(JobEnums.BackEnd.getTitle());
                } else if (norm.contains("데이터분석") || norm.contains("데이터과학") || norm.contains("데이터사이언")
                ) {
                    item.setSubJobCdNm(JobEnums.DataAnalyst.getTitle());
                } else if (norm.contains("데이터엔지니어") || norm.contains("데이터베이스엔지니어")
                ) {
                    item.setSubJobCdNm(JobEnums.DataEngineering.getTitle());
                } else if (norm.contains("QAENGINEER") || norm.contains("TESTENGINEER") ||
                    norm.contains("QA") || norm.contains("테스트엔지니어")
                ) {
                    item.setSubJobCdNm(JobEnums.QA.getTitle());
                } else if (norm.contains("보안") || norm.contains("SECURITY") || norm.contains("기술보안")) {
                    item.setSubJobCdNm(JobEnums.Security.getTitle());
                } else if (norm.contains("ML엔지니어") || norm.contains("머신러닝") || norm.contains("MLENGINEER")) {
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
		if (employmentType == null) return resStr; // 고용형태 미제공 공고 대비
		String recruitItemCode = employmentType.optString("recruitItemCode", "");

		if (recruitItemCode.isBlank()) return resStr;

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

	/**
	 * careerRestrictionMin/MaxYears 를 long 으로 정규화한다.
	 * org.json 은 정수를 Integer 또는 Long 으로 담을 수 있고, -1 은 "경력제한 없음"이라 0 으로 본다.
	 * null/비정상 값은 0 으로 처리한다.
	 */
	private long normalizeCareerYears(Object value) {
		if (!(value instanceof Number)) return 0L;
		long years = ((Number) value).longValue();
		return years == -1 ? 0L : years;
	}
}
