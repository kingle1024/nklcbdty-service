package com.nklcbdty.api.crawler.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.nklcbdty.api.crawler.interfaces.JobCrawler;
import com.nklcbdty.api.crawler.vo.Job_mst;

@Service
public class NaverJobCrawlerService implements JobCrawler {

    private final String apiUrl;

    public NaverJobCrawlerService() {
        this.apiUrl = createApiUrl();
    }

    protected String createApiUrl() {
        List<String> subJobCodes = new ArrayList<>();
        subJobCodes.add("1010001");
        subJobCodes.add("1010002");
        subJobCodes.add("1010003");

        return "https://recruit.navercorp.com/rcrt/loadJobList.do?annoId=&sw=&subJobCdArr="
                + String.join(",", subJobCodes) + "&sysCompanyCdArr=&empTypeCdArr=&entTypeCdArr=&workAreaCdArr=&firstIndex=10";
    }

    protected HttpURLConnection createConnection(URL url) throws Exception {
        return (HttpURLConnection) url.openConnection();
    }

    @Override
    public List<Job_mst> crawlJobs() {
        List<Job_mst> result = Collections.emptyList();

        try {
            URL url = new URL(apiUrl);
            HttpURLConnection conn = createConnection(url);
            conn.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            JSONObject jsonResponse = new JSONObject(response.toString());
            JSONArray jobList = jsonResponse.getJSONArray("list");

            result = new ArrayList<>();
            for (int i = 0; i < jobList.length(); i++) {
                JSONObject job = jobList.getJSONObject(i);
                Job_mst item = new Job_mst(); // 필요한 필드 추가
                result.add(item);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }
}
