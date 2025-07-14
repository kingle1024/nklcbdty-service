package com.nklcbdty.api.email.controller;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.email.dto.EmailRequest;
import com.nklcbdty.api.email.service.EmailService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;

    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> emailSend(@RequestBody EmailRequest emailRequest) {
        Map<String, Object> response = new HashMap<>();

        try {
            final String title = "[네카라쿠배] 문의 메일 도착";
            final String content = String.format("문의자 : %s\n%s", emailRequest.getTo(), emailRequest.getBody());
            emailService.sendEmail("kingle1024@gmail.com", title, content);

            response.put("result", "ok");
            return ResponseEntity.ok().body(response);
        } catch (Exception e) {
            response.put("result", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/send/job")
    public ResponseEntity<Map<String, Object>> emailSendJob() {
        Map<String, Object> response = new HashMap<>();

        try {
            Map<String, List<String>> userCategoryMap = emailService.getUserCategoryMap();
            Map<String, String> ab = emailService.sendEmail(userCategoryMap.get("AB"));

            LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일");

            for (Map.Entry<String, String> entry : ab.entrySet()) {
                String userEmail = entry.getKey();
                String content = entry.getValue();
                final String title = "[네카라쿠배] " + today.format(formatter) + " 맞춤 채용 공고가 도착했어요!";
                emailService.sendEmail(userEmail, title, content);
            }

            response.put("result", "ok");
            return ResponseEntity.ok().body(response);
        } catch (Exception e) {
            response.put("result", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
