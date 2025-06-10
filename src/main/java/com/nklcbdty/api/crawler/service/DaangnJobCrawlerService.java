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
public class DaangnJobCrawlerService implements JobCrawler{
	
	private final CrawlerCommonService crawlerCommonService;
    private String apiUrl;
    
    @Autowired
    public DaangnJobCrawlerService(CrawlerCommonService crawlerCommonService) {
		this.crawlerCommonService = crawlerCommonService;
		this.apiUrl = getApiUrl();
	}
    
    private String getApiUrl() {
    	return "https://about.daangn.com/page-data/jobs/page-data.json";
    }
	
	@Override
    @Async
	public CompletableFuture<List<Job_mst>> crawlJobs() {
		List<Job_mst> list = new ArrayList<>();
		try {
			
			String formattedDate = crawlerCommonService.formatCurrentTime(); 
			log.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> {}의 크롤러가 {}로 시작됩니다.", this.getClass(), formattedDate);
			String resultJSONStr = crawlerCommonService.fetchApiResponse(apiUrl);
			
			JSONObject jsonObj = new JSONObject(resultJSONStr);
			JSONArray allDepartmentFilteredJobPostNodes = jsonObj.getJSONObject("result").getJSONObject("data").getJSONObject("allDepartmentFilteredJobPost").getJSONArray("nodes");
			JSONArray allJobPostNodes = jsonObj.getJSONObject("result").getJSONObject("data").getJSONObject("allJobPost").getJSONArray("nodes");
			
			for(int i = 0; i < allDepartmentFilteredJobPostNodes.length(); i++) {
				Job_mst job_mst = new Job_mst();
				JSONObject item = allDepartmentFilteredJobPostNodes.getJSONObject(i);
				String classCdNm = "";
				String subJobCdNm = "";
				
				String jobDetailLink = item.get("absoluteUrl").toString(); 
				String annoId = item.get("ghId").toString();
				String annoSubject = item.get("title").toString();
				String rowEmploymentType = item.get("employmentType").toString(); // 컨버트 필요.
				String empTypeCdNm = ConvertCodeToEmpType(rowEmploymentType);
				
				String sysCompanyCdNm = allJobPostNodes.getJSONObject(i).get("corporate").toString();
				
				JSONArray departmentsNodes = item.getJSONArray("departments");
				for (int j = 0; j < departmentsNodes.length(); j++) {
					JSONObject department = departmentsNodes.getJSONObject(j);
					String rowDepartmentId = department.get("id").toString();
					String departmentName = convertDepartmentIdToName(rowDepartmentId);
					if (departmentName.contains(",")) {
						String[] departmentNames = splitDepartmentName(departmentName);
						classCdNm = departmentNames[0];
						subJobCdNm = departmentNames[1];
						
						job_mst.setClassCdNm(classCdNm);
						job_mst.setSubJobCdNm(subJobCdNm.trim());
					} else {
						classCdNm = departmentName;
						job_mst.setClassCdNm(classCdNm);
					}
				}
				
				job_mst.setJobDetailLink(jobDetailLink);
                job_mst.setAnnoId(annoId);
				job_mst.setAnnoSubject(annoSubject);
				job_mst.setEmpTypeCdNm(empTypeCdNm);
				job_mst.setSysCompanyCdNm(sysCompanyCdNm);
				
				list.add(job_mst);
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

            for (Job_mst item : list) {
                switch (item.getSysCompanyCdNm()) {
                    case "KARROT_MARKET": {
                        item.setSysCompanyCdNm("당근마켓");
                        break;
                    }

                    case "KARROT_PAY": {
                        item.setSysCompanyCdNm("당근페이");
                        break;
                    }
                }
            }

			crawlerCommonService.getNotSaveJobItem("DAANGN", list);
		} catch (Exception e) {
            log.error("Error occurred while crawling jobs: {}", e.getMessage(), e);
        }

        return CompletableFuture.completedFuture(list);
	}
	
	/**
	 * <p>직무형태 코드를 이름으로 바꾼다</p>
	 * @author David Lee
	 * */
	private String ConvertCodeToEmpType(String rowEmploymentType) {
		String resStr = "";
		if (rowEmploymentType.isBlank() || rowEmploymentType.isBlank() || rowEmploymentType == null) return resStr;
		
		switch (rowEmploymentType.toUpperCase()) {
		case "FULL_TIME":
			resStr = "정규";
			break;
		case "CONTRACTOR" :
			resStr = "계약직";
			break;
		default:
			resStr = "인턴";
			break;
		}	
		return resStr;
	}
	
	/**
	 * <p>정제된 departmentName에 ,가 있으면 스플릿해서 [0]인덱스는 classCdNm을
	 *    [1]인덱스는 subJobCdNm에 매핑할 것이다.</p>
	 * @author David Lee
	 * */
	private String[] splitDepartmentName(String departmentName) {
		
		if (!departmentName.contains(",")) {	
			
		} 
		String[] resultStrArr = departmentName.split(",");
		return resultStrArr;
	}
	
	/**
	 * <p>departmentId를 departmentName으로 정제한다.</p>
	 * @author David Lee
	 * */
	private String convertDepartmentIdToName(String rowDepartmentId) {
		String resultStr = "";
		if (rowDepartmentId.isBlank() || rowDepartmentId.isEmpty() || rowDepartmentId == null) return resultStr;
		
		switch (rowDepartmentId) {
			case "885068ee-7068-5e0b-bcc4-93048745888a":
				resultStr = "Business";
				break;
			case "3c9ebf9f-e46c-5652-85ad-9a59a622074":
				resultStr = "Communications";
				break;
			case "04f58fce-ec93-5f23-a5fc-70b30f9bb34e":
				resultStr = "Content";
				break;
			case "ca3db0f1-5de4-5e29-a195-1b6fb3c55d4f":
				resultStr = "Data";
				break;
			case "58b938e1-367e-5a81-ab06-84ad3203e9b7":
				resultStr = "Database Engineering";
				break;
			case "60f61570-40a8-5153-9a70-a690d903b839":
				resultStr = "Design";
				break;
			case "70660a18-95e5-508d-88a2-b77446b0090b":
				resultStr = "Finance";
				break;
			case "c34079f4-6a5f-5c9f-95d3-4d4aaf6375ce":
				resultStr = "Information Security Manager";
				break;
			case "88217783-fa3a-527c-9fd0-651b1fb891bc":
				resultStr = "Marketing";
				break;
			case "f4817a69-9e3a-5415-ae38-6aa13483fe24":
				resultStr = "Product Manager";
				break;
			case "dc6966a8-2515-510b-b8b2-271c08795633":
				resultStr = "QA";
				break;
			case "c71bb9ce-a2bb-576d-8f6f-49f6fe38ddd7":
				resultStr = "Sales";
				break;
			case "dcdbd459-8bfb-58b5-bd02-15edd2c2e312":
				resultStr = "Security";
				break;
			case "3378dc82-535d-5ae1-880c-0994f871ab0a":
				resultStr = "Security Engineer";
				break;
			case "a0e803de-0ddb-5df7-a4e4-9a15017b5c1b":
				resultStr = "Service Operations";
				break;
			case "48b43ceb-f343-512f-bda2-4c4b01ba5436":
				resultStr = "Site Reliability Engineering";
				break;
			case "cf10c6ef-ef9c-59e8-a8b1-12d4743bab5c":
				resultStr = "Software Engineer, Android";
				break;
			case "a2c23853-918c-560e-be7a-81ee256965d7":
				resultStr = "Software Engineer, Backend";
				break;
			case "0928eb77-9ffe-5572-93ae-39d685b19ac0":
				resultStr = "Software Engineer, Frontend";
				break;
			case "98c5efc7-4d92-5909-9454-cd428f42723c":
				resultStr = "Software Engineer, iOS";
				break;
			default:
				resultStr = "Software Engineer, Machine Learning";
				break;
		}
		return resultStr;
	}

}
