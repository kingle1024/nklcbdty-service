package com.nklcbdty.api.ai.nlp;

import com.nklcbdty.api.crawler.dto.PersonalHistoryDto;

public class NerSmokeTest {
    public static void main(String[] args) {
        PersonalHistoryNerService svc = new PersonalHistoryNerService();
        svc.load();
        if (!svc.isAvailable()) {
            System.out.println("모델 로드 실패");
            System.exit(1);
        }
        String[] cases = {
            "경력 3년 이상",
            "경력 5년 이상의 백엔드 개발자",
            "소프트웨어 개발 경험 10년 이상 관리 경험 2년 이상",
            "1년 이상 5년 이하",
            "3 to 5 years of experience",
            "Over 7 years of relevant experience",
            "5+ years of experience",
            "Less than 3 years of experience",
            "신입 모집",
            "프론트엔드 개발자 채용공고"
        };
        for (String c : cases) {
            PersonalHistoryDto r = svc.extract(c);
            System.out.printf("[%-50s] -> from=%d to=%d%n", c, r.getFrom(), r.getTo());
        }
    }
}
