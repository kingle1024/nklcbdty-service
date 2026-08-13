package com.nklcbdty.api.crawler.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.nklcbdty.api.crawler.common.CrawlerCommonService;
import com.nklcbdty.api.crawler.common.JobEnums;
import com.nklcbdty.api.crawler.dto.PersonalHistoryDto;
import com.nklcbdty.common.vo.Job_mst;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DaangnJobCrawlerService {

	private final CrawlerCommonService crawlerCommonService;
    private String apiUrl;

    @Autowired
    public DaangnJobCrawlerService(CrawlerCommonService crawlerCommonService) {
		this.crawlerCommonService = crawlerCommonService;
		this.apiUrl = getApiUrl();
	}

    /**
     * 당근 채용 사이트가 about.daangn.com(Gatsby) → careers.daangn.com(Astro) 으로 옮겨가면서
     * 기존 {@code /page-data/jobs/page-data.json} 이 404 가 되어 크롤이 0건이 됐다.
     * 새 사이트는 Greenhouse 보드(daangn)를 그대로 렌더링하므로 공개 보드 API 를 직접 호출한다.
     * {@code content=true} 를 붙이면 departments/offices 와 공고 본문(HTML)까지 한 번에 내려와,
     * 공고마다 상세 페이지를 다시 긁을 필요가 없다(기존 38회 요청 → 1회).
     */
    private String getApiUrl() {
    	return "https://boards-api.greenhouse.io/v1/boards/daangn/jobs?content=true";
    }

    // 공고 상세는 새 채용 사이트의 role 페이지로 연결한다.
    // (Greenhouse 가 주는 absolute_url 은 about.daangn.com?gh_jid=... 형태라 리다이렉트를 한 번 더 탄다)
    private static final String JOB_DETAIL_URL_PREFIX = "https://careers.daangn.com/jobs/role/";

    private static final Pattern VALID_THROUGH_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    @Async
	public CompletableFuture<List<Job_mst>> crawlJobs() {
		List<Job_mst> list = new ArrayList<>();
		try {

			String formattedDate = crawlerCommonService.formatCurrentTime();
			log.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> {}의 크롤러가 {}로 시작됩니다.", this.getClass(), formattedDate);
			String resultJSONStr = crawlerCommonService.fetchApiResponse(apiUrl);

			JSONObject jsonObj = new JSONObject(resultJSONStr);
			JSONArray jobNodes = jsonObj.optJSONArray("jobs");

			if (jobNodes == null) {
				// 보드 이관/차단 등으로 jobs 를 못 찾은 경우. 통째로 실패하지 않고 원인 진단이 되게 로그만 남긴다.
				log.error("당근 크롤 응답에서 jobs 를 찾지 못함. 응답 앞부분: {}",
					resultJSONStr == null ? "null" : resultJSONStr.substring(0, Math.min(300, resultJSONStr.length())));
				return CompletableFuture.completedFuture(crawlerCommonService.getNotSaveJobItem("DAANGN", list));
			}

			for (int i = 0; i < jobNodes.length(); i++) {
				try {
					JSONObject item = jobNodes.getJSONObject(i);

					// Greenhouse job id. 구 크롤러의 ghId 와 같은 값이라 기존 DB row 와 그대로 매칭된다.
					String annoId = item.opt("id") == null ? null : item.get("id").toString();
					// Greenhouse 의 title 은 "... (세일즈, Agency) " 처럼 끝에 공백이 붙어 내려오는 건이 있다.
					// 그대로 저장하면 링크 점검 배치의 "HTML 에 공고명이 있는가" 판정이 항상 실패한다.
					String annoSubject = item.optString("title", "").strip();

					if (annoId == null || annoId.isBlank() || annoSubject.isBlank()) {
						log.warn("당근 공고 필수값 누락으로 건너뜀 (index={}, id={})", i, annoId);
						continue;
					}

					Job_mst job_mst = new Job_mst();

					String jobDetailLink = JOB_DETAIL_URL_PREFIX + annoId + "/";
					job_mst.setAnnoId(annoId);
					job_mst.setAnnoSubject(annoSubject);
					job_mst.setJobDetailLink(jobDetailLink);
					job_mst.setEmpTypeCdNm(ConvertCodeToEmpType(findMetadata(item, "Employment Type")));
					job_mst.setSysCompanyCdNm(normalizeCorporate(findMetadata(item, "Corporate")));

					// Greenhouse 의 "Valid Through" 는 대부분 빈 값(상시채용)이다. 값이 있을 때만 마감일로 쓴다.
					// 조회(JobService)가 파싱 못 하는 형식이 들어오면 살아있는 공고가 목록에서 빠지므로,
					// yyyy-MM-dd 로 확인된 값만 반영하고 나머지는 상시채용(null)으로 둔다.
					String validThrough = findMetadata(item, "Valid Through");
					if (VALID_THROUGH_PATTERN.matcher(validThrough).matches()) {
						job_mst.setEndDate(validThrough + " 23:59:59");
					} else if (!validThrough.isBlank()) {
						log.warn("당근 공고 Valid Through 형식이 예상과 달라 무시함 (id={}, value='{}')", annoId, validThrough);
					}

					applyDepartment(job_mst, item.optJSONArray("departments"));

					// content=true 로 함께 내려온 공고 본문에서 경력을 추출한다(상세 페이지 재요청 불필요).
					PersonalHistoryDto personalHistoryDto = extractPersonalHistory(item.optString("content", ""));
					job_mst.setPersonalHistory(personalHistoryDto.getFrom());
					job_mst.setPersonalHistoryEnd(personalHistoryDto.getTo());

					list.add(job_mst);
				} catch (Exception itemEx) {
					log.error("당근 공고 파싱 실패 (index={}): {}", i, itemEx.getMessage(), itemEx);
				}
			}

            for (Job_mst item : list) {
                if (item.getAnnoSubject().contains("Software Engineer, Frontend")) {
                    item.setSubJobCdNm(JobEnums.FrontEnd.getTitle());
                } else if (item.getAnnoSubject().contains("Software Engineer, Backend")) {
                    item.setSubJobCdNm(JobEnums.BackEnd.getTitle());
                } else if (item.getAnnoSubject().contains("Machine Learning")) {
                    item.setSubJobCdNm(JobEnums.ML.getTitle());
                } else if (item.getAnnoSubject().contains("Software Engineer, iOS")) {
                    item.setSubJobCdNm(JobEnums.iOS.getTitle());
                } else if (item.getAnnoSubject().contains("Test Automation Engineer")) {
                    item.setSubJobCdNm(JobEnums.QA.getTitle());
                } else if (
                    item.getAnnoSubject().contains("Security Manager") ||
                    item.getAnnoSubject().contains("Privacy Manager")
                ) {
                    item.setSubJobCdNm(JobEnums.Security.getTitle());
                } else if (item.getAnnoSubject().contains("Security Engineer")) {
                    item.setSubJobCdNm(JobEnums.SecurityEngineering.getTitle());
                } else if (item.getAnnoSubject().contains("Site Reliability Engineer")) {
                    item.setSubJobCdNm(JobEnums.DevOps.getTitle());
                } else if (item.getAnnoSubject().contains("Software Engineer, Data")) {
                    item.setSubJobCdNm(JobEnums.DataEngineering.getTitle());
                } else if (
                    item.getAnnoSubject().contains("Brand Design") ||
                    item.getAnnoSubject().contains("Designer")
                ) {
                    item.setSubJobCdNm(JobEnums.ProductDesigner.getTitle());
                } else if (item.getAnnoSubject().contains("Brand Designer")) {
                    item.setSubJobCdNm(JobEnums.ProductDesigner.getTitle());
                }
            }

		} catch (Exception e) {
            log.error("Error occurred while crawling jobs: {}", e.getMessage(), e);
        }

        return CompletableFuture.completedFuture(crawlerCommonService.getNotSaveJobItem("DAANGN", list));
	}

	/**
	 * <p>Greenhouse 의 metadata 배열(name/value 쌍)에서 이름으로 값을 찾는다.
	 *    없거나 null 이면 빈 문자열.</p>
	 * */
	private String findMetadata(JSONObject item, String name) {
		JSONArray metadata = item.optJSONArray("metadata");
		if (metadata == null) return "";

		for (int i = 0; i < metadata.length(); i++) {
			JSONObject meta = metadata.optJSONObject(i);
			if (meta == null) continue;
			if (!name.equals(meta.optString("name", ""))) continue;
			return meta.optString("value", "");
		}
		return "";
	}

	/**
	 * <p>departments[0] 의 이름을 직군/직무로 매핑한다.
	 *    "Software Engineer, Backend" 처럼 ,가 있으면 앞은 classCdNm, 뒤는 subJobCdNm 이다.</p>
	 * */
	private void applyDepartment(Job_mst job_mst, JSONArray departments) {
		if (departments == null || departments.isEmpty()) return;

		JSONObject department = departments.optJSONObject(0);
		if (department == null) return;

		String departmentName = department.optString("name", "");
		if (departmentName.isBlank()) return;

		if (departmentName.contains(",")) {
			String[] departmentNames = departmentName.split(",");
			job_mst.setClassCdNm(departmentNames[0].trim());
			job_mst.setSubJobCdNm(departmentNames[1].trim());
		} else {
			job_mst.setClassCdNm(departmentName);
		}
	}

	/**
	 * <p>Greenhouse content 는 HTML 이 escape 된 문자열이다.
	 *    태그를 걷어낸 본문 텍스트에서 경력 연차를 추출한다.</p>
	 * */
	private PersonalHistoryDto extractPersonalHistory(String content) {
		if (content == null || content.isBlank()) return new PersonalHistoryDto();

		try {
			return crawlerCommonService.getPersonalHistory(Jsoup.parse(content).text());
		} catch (Exception e) {
			log.error("당근 공고 본문 경력 추출 실패: {}", e.getMessage());
			return new PersonalHistoryDto();
		}
	}

	/**
	 * <p>직무형태 코드를 이름으로 바꾼다</p>
	 * @author David Lee
	 * */
	private String ConvertCodeToEmpType(String rowEmploymentType) {
		String resStr = "";
		if (rowEmploymentType == null || rowEmploymentType.isBlank()) return resStr;

		// Greenhouse 보드로 옮겨오면서 값이 FULL_TIME/CONTRACTOR 에서 한글(정규직/계약직/인턴)로 바뀌었다.
		// 예전 표기도 함께 받아 크롤러 교체 전후로 동일한 결과가 나오게 한다.
		switch (rowEmploymentType.toUpperCase()) {
		case "FULL_TIME":
		case "정규직":
			resStr = "정규";
			break;
		case "CONTRACTOR" :
		case "계약직":
			resStr = "계약직";
			break;
		default:
			resStr = "인턴";
			break;
		}
		return resStr;
	}

	/**
	 * <p>Corporate 메타데이터를 화면 표기용 회사명으로 정리한다.
	 *    같은 법인인데도 "당근"/"당근마켓" 두 표기가 섞여 내려와 카드마다 다르게 보이던 것을 하나로 맞춘다.</p>
	 * */
	private String normalizeCorporate(String corporate) {
		if (corporate == null || corporate.isBlank()) return "당근마켓";

		switch (corporate) {
			// 구 크롤러(page-data)의 corporate 코드도 그대로 받아준다.
			case "KARROT_MARKET":
			case "당근":
				return "당근마켓";
			case "KARROT_PAY":
				return "당근페이";
			default:
				return corporate;
		}
	}

}
