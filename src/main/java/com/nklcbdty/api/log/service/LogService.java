package com.nklcbdty.api.log.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.nklcbdty.api.log.entity.VisitorEntity;
import com.nklcbdty.api.log.repository.VisitorRepository;

@Service
public class LogService {

    private final VisitorRepository logRepository;

    @Value("${ip2location.api.key}")  // application.properties에서 API 키를 가져옵니다.
    private String API_KEY;

    @Autowired
    public LogService(VisitorRepository logRepository) {
        this.logRepository = logRepository;
    }

    public VisitorEntity insertLog() {
        HttpServletRequest request =
                ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        HttpServletResponse response =
                ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getResponse();

        if("0:0:0:0:0:0:0:1".equals(request.getRemoteAddr()) || "127.0.0.1".equals(request.getRemoteAddr())) {
            // local에서는 히스토리 남기지 않도록 처리
            return null;
        }

        final String requestURI = request.getRequestURI();
        final String clientIpAddr = getClientIpAddr(request);

        final String lastPath = getLastPathFromCookie(request);
        if (lastPath != null && lastPath.equals(requestURI)) {
            // 추후 추가 로직 가능
            return null; // 로그 기록 생략
        }

        createCookie(requestURI, Objects.requireNonNull(response));

        VisitorEntity result = VisitorEntity.builder()
                .path(requestURI)
                .accept_language(request.getHeader("Accept-Language"))
                .referer(request.getHeader("Referer"))
                .insert_ip(clientIpAddr)
                .insert_dts(new Date())
          .build();

        Map<String, Object> ipLocation = getCountryInfo(clientIpAddr.trim().split(",")[0]);
        if(ipLocation != null) {
            result.setCountry_name((String)ipLocation.get("country_name"));
            result.setRegion_name((String)ipLocation.get("region_name"));
            result.setCity_name((String)ipLocation.get("city_name"));
        }
        return logRepository.save(result);
    }

    private String getLastPathFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies(); // 요청에서 쿠키 가져오기
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("lastPath".equals(cookie.getName())) {
                    return cookie.getValue(); // 쿠키에서 경로 값 반환
                }
            }
        }
        return null; // 쿠키가 없을 경우 null 반환
    }

    private Map<String, Object> getCountryInfo(String ip) {
        // https://www.ip2location.io/subscription#subscription-plan 사용
        BufferedReader rd = null;
        HttpURLConnection conn = null;
        if("0:0:0:0:0:0:0:1".equals(ip) || "127.0.0.1".equals(ip)) {
            return null; // localhost
        }

        try {
            final String fullURL = "https://api.ip2location.io/?key=" + API_KEY + "&ip=" + ip + "&format=json";
            URL url = new URL(fullURL);

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.4472.114 Safari/537.36"); // 없으면 에러

            // API 응답메시지를 불러와서 문자열로 저장
            rd = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) {
                sb.append(line);
            }

            // 결과 출력
            JSONObject jsonObject = new JSONObject(sb);
            return jsonObject.toMap();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (rd != null) {
                    rd.close();
                }
                if (conn != null) {
                    conn.disconnect();
                }
            } catch (Exception e) { e.printStackTrace(); }
        }

        return null;
    }

    public String getClientIpAddr(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-RealIP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("REMOTE_ADDR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        return ip;
    }

    private void createCookie(String path, HttpServletResponse response) {
        Cookie cookie = new Cookie("lastPath", path);
        cookie.setMaxAge(24 * 60 * 60); // 1일
        cookie.setPath("/"); // 모든 경로에서 접근 가능
        cookie.setSecure(true); // HTTPS에서만 전송되도록 설정
        cookie.setHttpOnly(true); // JavaScript에서 접근하지 못하도록 설정
        response.addCookie(cookie);
    }
}
