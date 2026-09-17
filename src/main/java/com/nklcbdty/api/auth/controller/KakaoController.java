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
import com.nklcbdty.api.common.security.AdminEmailPolicy;

@RestController
@RequestMapping("/api")
public class KakaoController {
    private final UserService userService;
    private final TokenService tokenService;
    private final UtilityNklcb utilityNklcb;
    private final AdminEmailPolicy adminEmailPolicy;

    @Autowired
    public KakaoController(UserService userService, TokenService tokenService, UtilityNklcb utilityNklcb,
                           AdminEmailPolicy adminEmailPolicy) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.utilityNklcb = utilityNklcb;
        this.adminEmailPolicy = adminEmailPolicy;
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
        JSONObject kakaoAccount = jsonObject.getJSONObject("kakao_account");
        String nickname = kakaoAccount.getJSONObject("profile").getString("nickname");
        // 이메일은 동의 항목이라 없을 수 있다. 없으면 관리자 판정을 하지 않는다.
        String email = kakaoAccount.optString("email", null);
        boolean admin = adminEmailPolicy.isAdminEmail(email);

        String jwtToken = admin
            ? utilityNklcb.generateAdminUserToken(userId, nickname)
            : utilityNklcb.generateToken(userId, false);
        String refreshToken = utilityNklcb.generateToken(userId, true);
        UserDetails userDetails = userService.loadUserById(userId, nickname, refreshToken);
        // 토큰 갱신 때는 user 테이블의 이메일로 관리자 여부를 다시 판정하므로 여기서 채워 둔다.
        userService.updateEmail(userId, email);
        tokenService.saveRefreshToken(userId, refreshToken);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("token", jwtToken);
        responseBody.put("refreshToken", refreshToken);
        responseBody.put("userId", userId);
        responseBody.put("nickname", nickname);
        responseBody.put("userDetails", userDetails);
        // 관리자 이메일로 로그인했는지. 프론트는 이 값으로 헤더의 관리자 메뉴를 띄운다.
        responseBody.put("isAdmin", admin);

        return ResponseEntity.ok(responseBody);
    }
}
