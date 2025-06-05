package com.nklcbdty.api.ai.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor // 필
public class GeminiContent {
    private List<GeminiPart> parts;
    private String role;
    public GeminiContent(List<GeminiPart> parts) { this.parts = parts; }
}
