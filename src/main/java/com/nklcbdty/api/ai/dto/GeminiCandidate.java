package com.nklcbdty.api.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeminiCandidate {
    private GeminiContent content;
    private String finishReason;
    private Double avgLogprobs;
}
