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

@Slf4j
@Service
public class TossJobCrawlerService implements JobCrawler {

    private final CrawlerCommonService commonService;

    @Autowired
    public TossJobCrawlerService(CrawlerCommonService commonService) {
        this.commonService = commonService;
    }

    @Override
    @Async
    public CompletableFuture<List<Job_mst>> crawlJobs() {
        List<Job_mst> result = new ArrayList<>();

        try {
            final String apiUrl = "https://api-public.toss.im/api/v3/ipd-eggnog/career/job-groups";
            final String jsonResponse = commonService.fetchApiResponse(apiUrl);

            // JSON 객체로 변환
            JSONObject jsonResult = new JSONObject(jsonResponse);

            // edges 배열 가져오기
            JSONArray edges = jsonResult.getJSONArray("success");
            for (int i = 0; i < edges.length(); i++) {
                JSONObject edge = edges.getJSONObject(i);
                JSONArray contents = edge.getJSONArray("jobs");
                for (int j = 0; j < contents.length(); j++) {
                    JSONObject contentItem = contents.getJSONObject(j);
                    JSONArray metadata = contentItem.getJSONArray("metadata");
                    boolean saveOk = true;
                    Job_mst item = new Job_mst();
                    for (int k = 0; k < metadata.length(); k++) {
                        JSONObject jsonObject = metadata.getJSONObject(k);
                        String name = jsonObject.getString("name");
                        Object objectValue = jsonObject.get("value");
                        String value = objectValue.toString();
                        if(value == null) {
                            continue;
                        }
                        switch (name) {
                            case "포지션의 소속 자회사를 선택해 주세요.": {
                                item.setSysCompanyCdNm(value);
                                break;
                            }
                            case "Job Category를 선택해 주세요. (미사용 필드입니다)": {
                                item.setClassCdNm(value);
                            }
                            case "커리어 페이지 노출 Job Category 값을 선택해주세요": {
                                item.setSubJobCdNm(value);
                                break;
                            }
                            case "Employment_Type": {
                                if ("정규직".equals(value)) {
                                    item.setEmpTypeCdNm("정규");
                                } else {
                                    item.setEmpTypeCdNm(value);
                                }
                                break;
                            }
                        }
                    }
                    item.setAnnoId(contentItem.getLong("id"));
                    item.setAnnoSubject(contentItem.getString("title"));
                    item.setJobDetailLink(contentItem.getString("absolute_url"));

                    if("null".equals(item.getSysCompanyCdNm())) {
                      saveOk = false;
                    }

                    if(saveOk) {
                        result.add(item);
                    }
                }
            }

            for (Job_mst item : result) {
                if (item.getAnnoSubject().contains("Frontend Developer") ||
                    item.getAnnoSubject().contains("Frontend UX")
                ) {
                    item.setSubJobCdNm(JobEnums.FrontEnd.getTitle());
                } else if (item.getAnnoSubject().contains("Server Developer")) {
                    item.setSubJobCdNm(JobEnums.BackEnd.getTitle());
                } else if (item.getAnnoSubject().contains("Full Stack Developer") ||
                    item.getAnnoSubject().contains("Finance(ALM) Developer") ||
                    item.getAnnoSubject().contains("Financial Systems Engineer") ||
                    item.getAnnoSubject().contains("Device Software Engineer") ||
                    item.getAnnoSubject().contains("Node.js Developer")
                ) {
                    item.setSubJobCdNm(JobEnums.FullStack.getTitle());
                } else if (item.getAnnoSubject().contains("iOS Platform Engineer") ||
                    item.getAnnoSubject().contains("iOS Platform Engineer (React Native)")
                ) {
                    item.setSubJobCdNm(JobEnums.iOS.getTitle());
                } else if(item.getAnnoSubject().contains("DevOps Engineer")) {
                    item.setSubJobCdNm(JobEnums.DevOps.getTitle());
                } else if (item.getAnnoSubject().contains("DataOps Manager") ||
                    item.getAnnoSubject().contains("Data Architect") ||
                    item.getAnnoSubject().contains("Data Analytics")
                ) {
                    item.setSubJobCdNm(JobEnums.DataAnalyst.getTitle());
                } else if (item.getAnnoSubject().contains("Data Engineer")) {
                    item.setSubJobCdNm(JobEnums.DataEngineering.getTitle());
                } else if (item.getAnnoSubject().contains("MLOps Engineer") ||
                    item.getAnnoSubject().contains("ML Engineer")
                ) {
                    item.setSubJobCdNm(JobEnums.ML.getTitle());
                } else if (item.getAnnoSubject().contains("Information Security Manager") ||
                    item.getAnnoSubject().contains("CISO") ||
                    item.getAnnoSubject().contains("Privacy Manager") ||
                    item.getAnnoSubject().contains("Security Audit Manager")
                ) {
                    item.setSubJobCdNm(JobEnums.Security.getTitle());
                } else if (item.getAnnoSubject().contains("Security Engineer") ||
                    item.getAnnoSubject().contains("Security Researcher")
                ) {
                    item.setSubJobCdNm(JobEnums.SecurityEngineering.getTitle());
                } else if (item.getAnnoSubject().contains("Network Engineer") ||
                    item.getAnnoSubject().contains("System Engineer") ||
                    item.getAnnoSubject().contains("IDC Engineer")
                ) {
                    item.setSubJobCdNm(JobEnums.Infra.getTitle());
                } else if (item.getAnnoSubject().contains("Product Manager")) {
                    item.setSubJobCdNm(JobEnums.PM.getTitle());
                } else if (item.getAnnoSubject().contains("Product Owner")) {
                    item.setSubJobCdNm(JobEnums.PO.getTitle());
                } else if (item.getAnnoSubject().contains("QA Manager") ||
                    item.getAnnoSubject().contains("Test Automation Engineer")
                ) {
                    item.setSubJobCdNm(JobEnums.QA.getTitle());
                } else if (item.getAnnoSubject().contains("Technical Product Manager") ||
                    item.getAnnoSubject().contains("Technical Account Manager")
                ) {
                    item.setSubJobCdNm(JobEnums.TechnicalSupport.getTitle());
                } else if (item.getAnnoSubject().contains("Platform Designer") ||
                    item.getAnnoSubject().contains("Product Designer") ||
                    item.getAnnoSubject().contains("UX Designe") ||
                    item.getAnnoSubject().contains("UI Design") ||
                    item.getAnnoSubject().contains("Graphic Designer")
                ) {
                    item.setSubJobCdNm(JobEnums.ProductDesigner.getTitle());
                } else {
                    item.setSubJobCdNm(null);
                }
            }

            for (Job_mst item : result) {
                if("Data Engineering".equals(item.getSubJobCdNm())) {
                    item.setSubJobCdNm(JobEnums.DataEngineering.getTitle());
                }
            }

            commonService.saveAll("TOSS", result);

        } catch (Exception e) {
            log.error("Error occurred while crawling jobs: {}", e.getMessage(), e);
        }

        return CompletableFuture.completedFuture(result);
    }
}
