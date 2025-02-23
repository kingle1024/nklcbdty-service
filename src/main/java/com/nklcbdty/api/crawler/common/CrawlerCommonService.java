package com.nklcbdty.api.crawler.common;

import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

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
}
