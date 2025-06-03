package com.nklcbdty.api.auth.controller;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.nklcbdty.api.auth.service.TokenService;
import com.nklcbdty.api.user.service.UserService;
import com.nklcbdty.api.common.UtilityNklcb;

@RestController
@RequestMapping("/api")
public class KakaoController {
    private final UserService userService;
    private final TokenService tokenService;
    private final UtilityNklcb utilityNklcb;

    @Autowired
    public KakaoController(UserService userService, TokenService tokenService, UtilityNklcb utilityNklcb) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.utilityNklcb = utilityNklcb;
    }

    @PostMapping("/kakaoLogin")
    public ResponseEntity<?> kakaoLogin(@RequestBody Map<String, String> body) {
        final String accessToken = body.get("accessToken");

        RestTemplate restTemplate = new RestTemplate();

        // 카카오 API를 통해 사용자 정보 가져오기
        String userInfoEndpoint = "https://kapi.kakao.com/v2/user/me";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(userInfoEndpoint, HttpMethod.GET, entity, String.class);

        JSONObject jsonObject = new JSONObject(response.getBody());
        Object id = jsonObject.get("id");
        String userId = "kakao@" + id;
        String nickname = jsonObject.getJSONObject("kakao_account")
                                     .getJSONObject("profile")
                                     .getString("nickname");

        String jwtToken = utilityNklcb.generateToken(userId, false);
        String refreshToken = utilityNklcb.generateToken(userId, true);
        UserDetails userDetails = userService.loadUserById(userId, nickname, refreshToken);
        tokenService.saveRefreshToken(userId, refreshToken);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("token", jwtToken);
        responseBody.put("refreshToken", refreshToken);
        responseBody.put("userId", userId);
        responseBody.put("nickname", nickname);
        responseBody.put("userDetails", userDetails);

        return ResponseEntity.ok(responseBody);
    }
}
