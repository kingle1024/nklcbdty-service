package com.nklcbdty.api.ai.service;

import org.springframework.stereotype.Service;
import org.springframework.ai.chat.client.ChatClient;


@Service
public class OpenAiService {
    private final ChatClient chatClient;

    public OpenAiService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 채용 공고 제목을 받아 AI 모델에게 직무 유형 분류를 요청합니다.
     * @param jobTitle 채용 공고 제목
     * @return 예측된 직무 유형 (예: "백엔드", "프론트엔드", "기획", "기타")
     */
    public String classifyJob(String jobTitle) {
        // AI 모델에게 보낼 프롬프트 메시지 구성
        // 모델이 원하는 형식으로 정확하게 응답하도록 지시하는 것이 중요합니다.
        String prompt = String.format("""
                주어진 채용 공고 제목을 보고 가장 적합한 하나의 직무 유형을 분류해주세요.
                분류 기준은 다음과 같습니다:
                - 백엔드
                - 프론트엔드
                - 데이터 과학
                - 기획
                - 운영
                - 기타 (위 분류에 해당하지 않는 경우)

                응답은 오직 분류된 직무 유형 하나만 포함해야 합니다.
                예시)
                제목: 시니어 프론트엔드 개발자 채용
                응답: 프론트엔드

                제목: ML 엔지니어 모십니다
                응답: 데이터 과학

                제목: 글로벌 백엔드 개발자
                응답: 백엔드

                ---
                제목: %s
                응답:
                """, jobTitle);

        // AI 모델 호출 및 응답 받기
        // .prompt()로 프롬프트 메시지를 설정하고 .call().content()로 응답 본문만 가져옵니다.
        String predictedJobType = chatClient.prompt()
                                           .user(prompt)
                                           .call()
                                           .content();

        // 모델의 응답에서 불필요한 공백이나 개행 문자 제거
        return predictedJobType.trim();
    }
}
